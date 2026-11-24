#!/usr/bin/env python3
"""Archive the Registry images referenced by the app Deployments."""

from base64 import b64encode
from contextlib import closing
from datetime import datetime, timedelta, timezone
from hashlib import file_digest, sha256
from io import BytesIO
import json
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
from uuid import uuid4

import boto3
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError


S3_BUCKET = "s15p31d106-backup-887324111755"
S3_REGION = "ap-northeast-2"
REGISTRY = "k15d106.p.ssafy.io/"
STORAGE_ROOT = Path("/var/lib/rancher/k3s/storage")
RECIPIENTS = Path("/etc/s15-k3s-backup/recipients")
SPOOL = Path("/var/cache/s15-registry-backup/snapshot.tar.age")
STATE = Path("/var/lib/s15-registry-backup/last-completed.json")
REFRESH = timedelta(days=7)
PART_BYTES = 64 * 1024**2


def kube_get(*args):
    command = [
        "/usr/local/bin/k3s", "kubectl", "--kubeconfig=/etc/rancher/k3s/k3s.yaml",
        "--request-timeout=20s", "get", *args, "-o", "json",
    ]
    return json.loads(subprocess.check_output(command, timeout=25))


def snapshot():
    claim = kube_get("-n", "platform", "pvc", "registry-data")
    volume = kube_get("pv", claim["spec"]["volumeName"])["spec"]
    storage = Path((volume.get("hostPath") or volume["local"])["path"]).resolve()
    if not storage.is_relative_to(STORAGE_ROOT):
        raise RuntimeError("Unexpected Registry storage path")
    storage = storage / "docker/registry/v2"

    refs = set()
    for deployment in kube_get("-n", "app", "deployments")["items"]:
        spec = deployment["spec"]["template"]["spec"]
        for container in spec["containers"] + spec.get("initContainers", []):
            image = container["image"]
            if image.startswith(REGISTRY):
                refs.add(image.removeprefix(REGISTRY))
    if not refs:
        raise RuntimeError("No deployed images in the project Registry")

    files, links, visited, images = {}, {}, set(), {}

    def blob(digest, expected_size=None):
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
            raise RuntimeError("Unsupported Registry digest")
        value = digest.removeprefix("sha256:")
        relative = f"blobs/sha256/{value[:2]}/{value}/data"
        path = storage / relative
        size = path.stat().st_size
        if expected_size is not None and size != expected_size:
            raise RuntimeError(f"Registry blob size mismatch: {digest}")
        files[relative] = (path, size, value)
        return path, size

    def manifest(repo, digest, expected_size=None):
        path, size = blob(digest, expected_size)
        if (repo, digest) in visited:
            return
        visited.add((repo, digest))
        value = digest.removeprefix("sha256:")
        links[f"repositories/{repo}/_manifests/revisions/sha256/{value}/link"] = digest
        if size > 16 * 1024**2:
            raise RuntimeError("Registry manifest exceeds the supported size")
        data = path.read_bytes()
        if sha256(data).hexdigest() != value:
            raise RuntimeError(f"Registry manifest checksum mismatch: {digest}")
        document = json.loads(data)
        for child in document.get("manifests", []):
            manifest(repo, child["digest"], child.get("size"))
        config = [document["config"]] if "config" in document else []
        for descriptor in document.get("layers", []) + config:
            layer = descriptor["digest"]
            blob(layer, descriptor.get("size"))
            links[f"repositories/{repo}/_layers/sha256/{layer.removeprefix('sha256:')}/link"] = layer

    for ref in sorted(refs):
        repo, separator, tag = ref.rpartition(":")
        if not separator or not re.fullmatch(r"animal/[a-z0-9]+(?:[._-][a-z0-9]+)*", repo):
            raise RuntimeError(f"Unsupported Registry reference: {ref}")
        if not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}", tag):
            raise RuntimeError(f"Unsupported Registry tag: {tag}")
        tag_path = f"repositories/{repo}/_manifests/tags/{tag}"
        digest = (storage / tag_path / "current/link").read_text(encoding="ascii").strip()
        manifest(repo, digest)
        links[f"{tag_path}/current/link"] = digest
        links[f"{tag_path}/index/sha256/{digest.removeprefix('sha256:')}/link"] = digest
        images[ref] = digest

    return {"images": images, "blob_count": len(files)}, links, files


class CheckedReader:
    def __init__(self, source):
        self.source = source
        self.digest = sha256()

    def read(self, size):
        data = self.source.read(size)
        self.digest.update(data)
        return data


def write_archive(output, index, links, files):
    def add_bytes(archive, name, data):
        entry = tarfile.TarInfo(name)
        entry.mode, entry.size = 0o600, len(data)
        archive.addfile(entry, BytesIO(data))

    root = "docker/registry/v2/"
    with tarfile.open(fileobj=output, mode="w|", bufsize=1024**2) as archive:
        add_bytes(archive, "backup-index.json", json.dumps(index).encode())
        for name, digest in links.items():
            add_bytes(archive, root + name, digest.encode("ascii"))
        for name, (path, size, expected) in files.items():
            entry = tarfile.TarInfo(root + name)
            entry.mode, entry.size = 0o600, size
            with path.open("rb") as source:
                reader = CheckedReader(source)
                archive.addfile(entry, reader)
            if reader.digest.hexdigest() != expected:
                raise RuntimeError(f"Registry blob checksum mismatch: {expected}")


def encrypt(index, links, files):
    bytes_needed = sum(size for _, size, _ in files.values())
    if shutil.disk_usage(SPOOL.parent).free < bytes_needed + 1024**3:
        raise RuntimeError("Insufficient cache space for the encrypted Registry archive")
    command = ["/usr/bin/age", "-R", str(RECIPIENTS), "-o", str(SPOOL)]
    with subprocess.Popen(command, stdin=subprocess.PIPE) as process:
        try:
            with closing(process.stdin):
                write_archive(process.stdin, index, links, files)
        except BaseException:
            process.kill()
            raise
        if process.wait() != 0:
            raise RuntimeError("Registry archive encryption failed")


def upload(key, fingerprint, index):
    client = boto3.client(
        "s3", region_name=S3_REGION,
        config=Config(signature_version="s3v4", connect_timeout=10, read_timeout=90,
                      retries={"mode": "standard", "total_max_attempts": 4}),
    )
    with SPOOL.open("rb") as source:
        checksum = file_digest(source, "sha256").hexdigest()
    part_size = max(PART_BYTES, (SPOOL.stat().st_size + 9_999) // 10_000)
    common = {"Bucket": S3_BUCKET, "Key": key}
    created = client.create_multipart_upload(
        **common, ServerSideEncryption="AES256", ChecksumAlgorithm="SHA256",
        ContentType="application/octet-stream",
        Metadata={"sha256": checksum, "index-sha256": fingerprint,
                  "image-count": str(len(index["images"])), "blob-count": str(index["blob_count"])},
    )
    target = {**common, "UploadId": created["UploadId"]}
    parts = []
    composite = sha256()
    try:
        with SPOOL.open("rb") as source:
            for number, chunk in enumerate(iter(lambda: source.read(part_size), b""), start=1):
                digest = sha256(chunk).digest()
                composite.update(digest)
                digest_b64 = b64encode(digest).decode("ascii")
                result = client.upload_part(**target, PartNumber=number, Body=chunk, ChecksumSHA256=digest_b64)
                if result.get("ChecksumSHA256") != digest_b64:
                    raise RuntimeError(f"S3 checksum mismatch for part {number}")
                parts.append({"PartNumber": number, "ETag": result["ETag"], "ChecksumSHA256": digest_b64})
                if number % 10 == 0:
                    print(f"Uploaded {number} verified parts", flush=True)
        completed = client.complete_multipart_upload(**target, MultipartUpload={"Parts": parts})
    except BaseException:
        try:
            client.abort_multipart_upload(**target)
        except (BotoCoreError, ClientError):
            print("Immediate multipart cleanup failed. The bucket lifecycle will expire incomplete parts.", flush=True)
        raise
    expected = b64encode(composite.digest()).decode("ascii")
    if completed.get("ChecksumSHA256") not in {expected, f"{expected}-{len(parts)}"}:
        raise RuntimeError("S3 composite checksum did not match")
    print(f"Uploaded s3://{S3_BUCKET}/{key} bytes={SPOOL.stat().st_size} sha256={checksum}", flush=True)


def recently_backed_up(fingerprint, now):
    if not STATE.exists():
        return False
    try:
        state = json.loads(STATE.read_text(encoding="utf-8"))
        age = now - datetime.fromisoformat(state["completed_at"])
        return state["index_sha256"] == fingerprint and timedelta() <= age < REFRESH
    except (KeyError, ValueError, TypeError):
        print("Previous Registry backup state is invalid. Creating a fresh archive.", flush=True)
        return False


def main():
    index, links, files = snapshot()
    fingerprint = sha256(json.dumps(index, sort_keys=True).encode()).hexdigest()
    now = datetime.now(timezone.utc)
    if recently_backed_up(fingerprint, now):
        print(f"Registry snapshot skipped: {len(index['images'])} image references are unchanged", flush=True)
        return
    key = f"registry/active-{now.strftime('%Y%m%dT%H%M%SZ')}-{uuid4().hex[:8]}.tar.age"
    print(f"Archiving {len(index['images'])} image references and {len(files)} unique blobs", flush=True)
    SPOOL.unlink(missing_ok=True)
    try:
        encrypt(index, links, files)
        upload(key, fingerprint, index)
        marker = STATE.with_suffix(".new")
        marker.write_text(json.dumps({"index_sha256": fingerprint, "key": key,
                                     "completed_at": datetime.now(timezone.utc).isoformat()}), encoding="utf-8")
        marker.replace(STATE)
    finally:
        SPOOL.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
