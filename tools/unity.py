"""Developer commands for the independent Unity runtime."""
import argparse
import hashlib
import json
import os
import tempfile
import urllib.request
import zipfile
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.3.3-Beta"
DIST = ROOT / "versions" / VERSION
CACHE = ROOT / "cache" / VERSION
JAR = DIST / f"unity-loader-{VERSION}.jar"


def beta_samples(root):
    spec = json.loads((root/"standalone/beta-gameplay.json").read_bytes())
    return spec["originals"] + spec["fabricModules"]


def install_samples(cache_only=False):
    # A source checkout can have a newer candidate than its currently selected launch scripts.
    state = ROOT/"versions/state.json"
    selected = json.loads(state.read_bytes())["active"]["version"] if state.exists() and not cache_only else VERSION
    cache_root = ROOT/"cache"/selected

    specs=sum((json.loads((ROOT/"standalone"/name).read_bytes()) for name in ("forge-content.json","forge-containers.json","ecosystem.json","creative-content.json")),[])
    commands=json.loads((ROOT/"standalone/forge-samples.json").read_bytes())
    if selected in ("0.1.3-Beta","0.1.4-Beta","0.2.0-Beta","0.2.1-Beta","0.2.2-Beta","0.2.3-Beta","0.2.4-Beta","0.2.5-Beta","0.2.6-Beta","0.2.7-Beta","0.2.8-Beta","0.2.9-Beta","0.3.0-Beta","0.3.1-Beta","0.3.2-Beta","0.3.3-Beta"):
        specs.extend(beta_samples(ROOT))
    if selected in ("0.2.0-Beta","0.2.1-Beta","0.2.2-Beta","0.2.3-Beta","0.2.4-Beta","0.2.5-Beta","0.2.6-Beta","0.2.7-Beta","0.2.8-Beta","0.2.9-Beta","0.3.0-Beta","0.3.1-Beta","0.3.2-Beta","0.3.3-Beta"):
        specs.extend(json.loads((ROOT/"standalone/ecology-modules.json").read_bytes()))
    for spec in [*specs,*commands]:
        cache=cache_root/"samples"/spec["filename"]
        cache.parent.mkdir(parents=True,exist_ok=True)
        def matches(p):
            if not p.is_file() or p.stat().st_size!=spec["size"]:return False
            with p.open("rb") as f:return hashlib.file_digest(f,"sha512").hexdigest()==spec["sha512"]
        if not matches(cache):
            with tempfile.TemporaryDirectory(dir=cache.parent) as temp:
                candidate=Path(temp)/cache.name
                if "member" in spec:
                    umbrella=json.loads((ROOT/"standalone/beta-gameplay.json").read_bytes())["fabricApi"]
                    with urllib.request.urlopen(umbrella["url"],timeout=90) as response:
                        blob=response.read(umbrella["size"]+1)
                    if len(blob)!=umbrella["size"] or hashlib.sha512(blob).hexdigest()!=umbrella["sha512"]:
                        raise ValueError("Fabric API archive differs from lock")
                    import io
                    with zipfile.ZipFile(io.BytesIO(blob)) as z:
                        candidate.write_bytes(z.read(spec["member"]))
                else:
                    with urllib.request.urlopen(spec["url"],timeout=90) as response,candidate.open("wb") as out:
                        shutil.copyfileobj(response,out)
                if not matches(candidate):raise ValueError("Author JAR hash mismatch: "+cache.name)
                os.replace(candidate,cache)
        if cache_only:
            print(cache.name)
            continue
        for game in (["standalone-instance","standalone-client"] if spec in specs else ["standalone-instance"]):
            dest=ROOT/game/"mods"/cache.name
            dest.parent.mkdir(parents=True,exist_ok=True)
            if dest.exists():
                if not matches(dest):raise ValueError("Existing user JAR differs: "+str(dest))
                continue
            with tempfile.TemporaryDirectory(dir=dest.parent) as temp:
                staged=Path(temp)/cache.name;shutil.copy2(cache,staged)
                os.link(staged,dest)
        print(spec["filename"],spec["version"])


def install_fabric_api(games, source):
    lock = json.loads((ROOT/"standalone/beta-gameplay.json").read_bytes())["fabricApi"]
    name = lock["url"].rsplit("/",1)[1]
    def valid(path):
        if not path.is_file() or path.stat().st_size != lock["size"]: return False
        with path.open("rb") as stream: return hashlib.file_digest(stream,"sha512").hexdigest()==lock["sha512"]
    bundled=ROOT/"standalone/vendor"/name
    if source is None and bundled.is_file(): source=bundled
    if source is None:
        source=CACHE/"fabric-api"/name
        if not valid(source):
            source.parent.mkdir(exist_ok=True,parents=True)
            with tempfile.TemporaryDirectory(dir=source.parent) as temp:
                candidate=Path(temp)/name
                with urllib.request.urlopen(lock["url"],timeout=90) as response:
                    candidate.write_bytes(response.read(lock["size"]+1))
                if not valid(candidate): raise ValueError("Official Fabric API download differs from lock")
                candidate.replace(source)
    if not valid(source): raise ValueError("Fabric API original differs from lock: "+str(source))
    targets=[]
    for game in dict.fromkeys(games):
        mods=game/"mods"
        mods.mkdir(parents=True,exist_ok=True)
        installed=[]
        for file in mods.glob("*.jar"):
            with zipfile.ZipFile(file) as archive:
                if "fabric.mod.json" in archive.namelist() and json.loads(archive.read("fabric.mod.json")).get("id")=="fabric-api":
                    installed.append(file)
        if installed:
            if len(installed)!=1 or not valid(installed[0]): raise ValueError("Conflicting Fabric API in "+str(mods))
        elif (mods/name).exists(): raise ValueError("Existing file at "+str(mods/name))
        else: targets.append(mods/name)
    for target in targets:
        with tempfile.TemporaryDirectory(dir=target.parent) as temp:
            staged=Path(temp)/name;shutil.copyfile(source,staged)
            if not valid(staged): raise ValueError("Fabric API copy differs")
            try: os.link(staged,target)
            except FileExistsError:
                if not valid(target): raise
        print(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_subparsers(dest="action")
    actions.add_parser("samples").add_argument("--cache-only",action="store_true",help="Prepare the source candidate without changing any installed game")
    fabric=actions.add_parser("fabric-api")
    fabric.add_argument("--game-dir",type=Path,action="append")
    fabric.add_argument("--from-jar",type=Path)
    run = actions.add_parser("run")
    run.add_argument("--server-jar", type=Path)
    build = actions.add_parser("build")
    build.add_argument("--server-jar", type=Path, default=CACHE / "mojang-server-26.2.jar")
    build.add_argument("--client-home", type=Path)
    prepare = actions.add_parser("prepare")
    prepare.add_argument("--server-jar", type=Path)
    client = actions.add_parser("install-client")
    client.add_argument("launcher_root", type=Path)
    options = parser.parse_args()
    if options.action=="fabric-api":
        install_fabric_api(options.game_dir or [ROOT/"standalone-instance",ROOT/"standalone-client"],options.from_jar)
        return 0
    if options.action=="samples":
        install_samples(options.cache_only);return 0
    if options.action == "build":
        args = ["java", ROOT / "standalone/Build.java", ROOT, options.server_jar.resolve()]
        if options.client_home:
            args.append(options.client_home.resolve())
        return subprocess.call(args)
    state=ROOT/"versions/state.json"
    selected=json.loads(state.read_bytes())["active"]["version"] if state.exists() else VERSION
    if Path(selected).name!=selected or selected in ("",".",".."):
        raise ValueError("Invalid active version")
    distribution=ROOT/"versions"/selected
    executable=distribution/f"unity-loader-{selected}.jar"
    runtime_cache=ROOT/"cache"/selected
    if options.action == "install-client":
        return subprocess.call(["java", "-jar", executable, "--install-client", options.launcher_root.resolve()])
    args = ["java", "-Xms256m", "-Xmx2g", "-jar", executable, "--runtime-dir", runtime_cache]
    if getattr(options, "server_jar", None):
        args.extend(["--server-jar", options.server_jar.resolve()])
    if options.action == "prepare":
        return subprocess.call([*args, "--prepare-only"])
    game = ROOT / "standalone-instance"
    mods = game / "unity-mods"
    if not mods.exists():
        mods.mkdir(parents=True)
        shutil.copy2(distribution / "unity-world-marker.jar", mods)
    return subprocess.call([*args, "nogui"], cwd=game)


if __name__ == "__main__":
    raise SystemExit(main())
