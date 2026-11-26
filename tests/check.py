"""Exercise the production lifecycle transform, class scanning and JAR ownership in an isolated JVM."""
import argparse
import os
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from unity import DIST, JAR, VERSION

def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def main(jar=JAR,cache=None,evidence=None):
    sys.stdout.reconfigure(errors="backslashreplace")
    work = ROOT / ".work"
    work.mkdir(exist_ok=True)
    cache=cache or ROOT/"cache"/VERSION
    evidence=evidence or ROOT/"evidence"/VERSION
    evidence.mkdir(parents=True, exist_ok=True)
    log = evidence / "lifecycle.log"
    with tempfile.TemporaryDirectory(dir=work) as temp, log.open("wb") as output:
        completed = subprocess.run(["java", "-cp", str(jar), "standalone/tests/RuntimeChecks.java", str(ROOT),temp,str(jar),str(cache)], cwd=ROOT,
                                    stdout=output, stderr=subprocess.STDOUT)
        if completed.returncode==0:
            completed=subprocess.run(["java","standalone/tests/NestedChecks.java",str(ROOT),temp,str(jar),str(cache)],cwd=ROOT,stdout=output,stderr=subprocess.STDOUT)
        if completed.returncode == 0:
            paths = [jar, cache / "versions/26.2/server-26.2.jar", *sorted((cache / "libraries").rglob("*.jar"))]
            completed = subprocess.run(["java", "-Duser.language=en", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Xverify:all", "-cp", os.pathsep.join(map(str, paths)), "standalone/tests/BetaChecks.java", temp],
                                       cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
        if completed.returncode == 0:
            completed = subprocess.run(["java","-Xverify:all","-cp",os.pathsep.join(map(str,paths)),"standalone/tests/ConfigurationChecks.java",temp],cwd=ROOT,stdout=output,stderr=subprocess.STDOUT)
        if completed.returncode == 0:
            completed=subprocess.run(["java","-Xverify:all","-cp",os.pathsep.join(map(str,paths)),"standalone/tests/ReviewChecks.java",temp],cwd=ROOT,stdout=output,stderr=subprocess.STDOUT)
    print(log.read_text(encoding="utf8", errors="replace"))
    completed.check_returncode()
    report = {"exit": 0, "loaderSHA256": sha(jar), "logSHA256": sha(log)}
    pending = evidence / "lifecycle.tmp"
    pending.write_text(json.dumps(report, indent=2), encoding="utf8")
    pending.replace(evidence / "lifecycle.json")

if __name__ == "__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--jar",type=lambda s:Path(s).resolve(),default=JAR)
    parser.add_argument("--cache",type=lambda s:Path(s).resolve())
    parser.add_argument("--evidence",type=lambda s:Path(s).resolve())
    o=parser.parse_args();main(o.jar,o.cache,o.evidence)
