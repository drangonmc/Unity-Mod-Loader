"""Check the vendored c: data against the pinned upstream archive or restore its exact files."""
import argparse
import hashlib
import json
import os
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "standalone/src/main/resources"
LOCK = ROOT / "standalone/common-tags.json"

def verify(archive, restore=False):
    lock = json.loads(LOCK.read_bytes())
    if archive:
        with archive.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != lock["sha256"]:
                raise ValueError("Unexpected upstream JAR")
        with zipfile.ZipFile(archive) as original:
            members = {n for n in original.namelist() if n.startswith("data/c/tags/") and n.endswith(".json")}
            if members != set(lock["files"]):
                raise ValueError("Upstream tag inventory changed")
            for name in sorted(members):
                data = original.read(name)
                if hashlib.sha256(data).hexdigest() != lock["files"][name]:
                    raise ValueError("Unexpected upstream tag: " + name)
                if restore:
                    dest = RESOURCES / name
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    with tempfile.NamedTemporaryFile(dir=dest.parent, delete=False) as temp:
                        temp.write(data)
                    try:
                        os.replace(temp.name, dest)
                    finally:
                        Path(temp.name).unlink(missing_ok=True)
    actual = {p.relative_to(RESOURCES).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in (RESOURCES / "data/c/tags").rglob("*.json")}
    if actual != lock["files"]:
        raise ValueError("Bundled common tags differ from the lock")
    print(f"Verified {len(actual)} common tags")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--restore", action="store_true")
    args = parser.parse_args()
    if args.restore and not args.archive:
        parser.error("--restore needs the pinned --archive")
    verify(args.archive, args.restore)
