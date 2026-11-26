"""Build the same optional Bukkit integration for Paper and Unity from the pinned plugin APIs."""
import argparse
import hashlib
import json
import os
import subprocess
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]

def build(authors, output):
    pins = json.loads((ROOT / "standalone/tests/reference/protection-originals.json").read_bytes())
    inputs = []
    for row in pins:
        if row["slug"] in ("worldedit", "worldguard"):
            f = authors / row["filename"]
            if hashlib.sha512(f.read_bytes()).hexdigest() != row["sha512"]:
                raise ValueError(f"Unexpected upstream JAR: {f}")
            inputs.append(f)
    cp = [*inputs, *sorted((ROOT / "standalone/vendor/bukkit").glob("*.jar"))]
    work = ROOT / ".work"
    work.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=work) as temp:
        classes = Path(temp)
        subprocess.run(["javac", "--release", "25", "-g:none", "-proc:none", "-cp",
                        os.pathsep.join(map(str, cp)), "-d", classes, *sorted((HERE / "src").rglob("*.java"))], check=True)
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, "w") as jar:
            for f in sorted(classes.rglob("*.class")):
                jar.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix()), f.read_bytes())
            jar.writestr(zipfile.ZipInfo("plugin.yml"), (HERE / "plugin.yml").read_bytes())

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("authors", type=Path)
    parser.add_argument("output", type=Path)
    opts = parser.parse_args()
    build(opts.authors, opts.output)
