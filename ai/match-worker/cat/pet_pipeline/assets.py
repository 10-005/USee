"""Verify bundled models and immutable official source; no network or GPU operations."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VENDOR = ROOT / "vendor/meowid"
REVISION = "886ede57e0930d01c18de97a17c89dfa7c44f39f"
BASE_URL = f"https://huggingface.co/RicePasteM/MeowID-Base/resolve/{REVISION}/artifacts/"
WEIGHTS = {
    "MeowID-Base/meowid_base.onnx": "16bc4ab78aba82b531326ca7be5623d0fcbbabccd4fc89afd32adaaf2b316edd",
    "MeowID-Base/ecpose.onnx": "aed358530fd8d0769c1dad678272c169dc3ba12c3c54b2979ead24eefd758d67",
}
METADATA = {
    "MeowID-Base/SHA256SUMS": "c194afed91791573bb982adcb3f36d3f41e5f44c36ba752f95fcaec8e6d85fc5",
    "MeowID-Base/deployment.json": "7dd53bc9c1b63be400827c93ae655124b370b8cc2e17eb8724c9fb86c7350df1",
}


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def verify() -> dict:
    for name, expected in {**WEIGHTS, **METADATA}.items():
        path = ROOT / "models" / name
        if not path.is_file() or digest(path) != expected:
            raise RuntimeError(f"Missing or changed model: {path}. Restore the models from the release ZIP.")
    provenance_path = ROOT / "models/provenance.json"
    provenance = json.loads(provenance_path.read_text())
    for name, item in provenance["files"].items():
        if digest(ROOT / "models" / name) != item["sha256"]:
            raise RuntimeError(f"Changed model metadata: {name}")
    source = json.loads((ROOT / "vendor_manifest.json").read_text())
    for name, expected in source["files"].items():
        if digest(VENDOR / name) != expected:
            raise RuntimeError(f"Changed official source: {name}")
    return {"revision": REVISION, "weights": WEIGHTS, "vendor_archive_sha256": source["archive_sha256"]}
