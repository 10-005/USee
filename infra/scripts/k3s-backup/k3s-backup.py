#!/usr/bin/env python3
"""Take an online SQLite snapshot and upload an age-encrypted k3s backup."""

import base64
from contextlib import closing
from datetime import datetime, timezone
from hashlib import file_digest
from pathlib import Path
import sqlite3
import subprocess
import tarfile
from tempfile import TemporaryDirectory
from uuid import uuid4

import boto3
from botocore.config import Config


SERVER = Path("/var/lib/rancher/k3s/server")
RECIPIENT = Path("/etc/s15-k3s-backup/recipients")
RUNTIME = Path("/run/s15-k3s-backup")
BUCKET = "s15p31d106-backup-887324111755"
REGION = "ap-northeast-2"


def capture(server, work):
    token = (server / "token").read_bytes()
    if not token.strip():
        raise RuntimeError("The k3s server token is empty")

    source_db = server / "db/state.db"
    snapshot = work / "state.db"
    with closing(sqlite3.connect(source_db.as_uri() + "?mode=ro", uri=True)) as source:
        with closing(sqlite3.connect(snapshot)) as destination:
            source.backup(destination, pages=256, sleep=0.1)
            if destination.execute("PRAGMA quick_check").fetchall() != [("ok",)]:
                raise RuntimeError("SQLite quick_check failed")

    if token != (server / "token").read_bytes():
        raise RuntimeError("The k3s server token changed during the snapshot")
    token_file = work / "token"
    token_file.write_bytes(token)
    archive = work / "k3s.tar.gz"
    with tarfile.open(archive, "w:gz", compresslevel=3) as output:
        output.add(snapshot, arcname="db/state.db")
        output.add(token_file, arcname="token")
    return archive


def upload(encrypted, key):
    client = boto3.client(
        "s3", region_name=REGION,
        config=Config(signature_version="s3v4", connect_timeout=10, read_timeout=35,
                      retries={"mode": "standard", "total_max_attempts": 4}),
    )
    with encrypted.open("rb") as payload:
        checksum = base64.b64encode(file_digest(payload, "sha256").digest()).decode("ascii")
        payload.seek(0)
        response = client.put_object(
            Bucket=BUCKET, Key=key, Body=payload, ChecksumSHA256=checksum,
            ContentType="application/octet-stream", ServerSideEncryption="AES256",
        )
    if response.get("ChecksumSHA256") != checksum:
        raise RuntimeError("S3 did not confirm the backup's SHA-256 checksum")
    print(f"Uploaded s3://{BUCKET}/{key} (SHA-256 verified)", flush=True)


def main():
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    key = f"k3s/state-{stamp}-{uuid4().hex[:8]}.tar.gz.age"
    with TemporaryDirectory(prefix="snapshot-", dir=RUNTIME) as directory:
        work = Path(directory)
        archive = capture(SERVER, work)
        encrypted = work / "k3s.tar.gz.age"
        subprocess.run(
            ["/usr/bin/age", "-R", str(RECIPIENT), "-o", str(encrypted), str(archive)],
            check=True,
        )
        upload(encrypted, key)


if __name__ == "__main__":
    main()
