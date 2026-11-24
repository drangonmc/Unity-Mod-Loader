"""Verify the delivered Windows entry points in a fresh extraction, including source rebuild."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from acceptance import ROOT, VERSION, DIST, EVIDENCE, Server, clean_env, digest


def run(options):
    archive = DIST / f"Unity-{VERSION}-MC26.2-Standalone.zip"
    candidate=archive.with_name(archive.name+".candidate")
    if candidate.exists(): archive=candidate
    bundle = options.bundle.resolve()
    home = options.client_home.resolve()
    eula = options.eula_from.read_text()
    if not re.search(r"(?im)^eula\s*=\s*true\s*$", eula):
        raise ValueError("Expected an accepted local test instance")
    work = ROOT / ".work"
    work.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="Unity package ", dir=work) as temp:
        extracted = Path(temp)
        with zipfile.ZipFile(archive) as z:
            z.extractall(extracted)
        game = extracted / "standalone-instance"
        if game.exists():
            raise AssertionError("Instance data included in distribution")
        game.mkdir()
        (game / "eula.txt").write_text(eula)
        cache = extracted / "cache" / VERSION
        cache.mkdir(parents=True)
        shutil.copy2(bundle, cache / "mojang-server-26.2.jar")
        sample_hashes={}
        if options.samples:
            samples=cache/"samples";samples.mkdir()
            content=sum((json.loads((extracted/"standalone"/name).read_bytes()) for name in ("forge-content.json","forge-containers.json","ecosystem.json","creative-content.json")),[])
            commands=json.loads((extracted/"standalone/forge-samples.json").read_bytes())
            beta=json.loads((extracted/"standalone/beta-gameplay.json").read_bytes())
            content+=beta["originals"]+beta["fabricModules"]+json.loads((extracted/"standalone/ecology-modules.json").read_bytes())
            for spec in content+commands:shutil.copy2(options.samples/spec["filename"],samples)
            subprocess.run(["cmd.exe","/d","/c","Install-Unity-Samples.cmd"],cwd=extracted,env=clean_env(),check=True)
            for game_path,expected in ((game,content+commands),(extracted/"standalone-client",content)):
                observed={p.name:digest(p,"sha512") for p in (game_path/"mods").glob("*.jar")}
                if observed!={sp["filename"]:sp["sha512"] for sp in expected}:raise AssertionError("Packaged samples: "+str(game_path))
                sample_hashes[game_path.name]=observed
        jar = extracted / "versions" / VERSION / f"unity-loader-{VERSION}.jar"
        hashes = {p.name: digest(p) for p in jar.parent.glob("*.jar")}
        checks = {}
        for phase in ("packaged-start", "packaged-restart"):
            server = Server(extracted, phase, jar, bundle, cache, ["cmd.exe", "/d", "/c", "Start-Unity.cmd"])
            try:
                server.expect(r"\[Unity\] Standalone ready", 160)
                server.send("unity", r"standalone \| MC 26\.2 \| native \[world-marker\]")
                server.send("forceload add 0 0")
                for _ in range(80):
                    server.send("execute if loaded 3 200 3 run say PACKAGED_READY")
                    line=server.send("execute unless loaded 3 200 3 run say PACKAGED_WAIT",r"\[Server\] PACKAGED_(READY|WAIT)")
                    if line.endswith("READY"):
                        break
                    time.sleep(.25)
                else:
                    raise TimeoutError("Chunk loading")
                if phase == "packaged-start":
                    server.send("unitymark place 3 300 3", "Unity marker placed on game tick at 3, 300, 3; total 1")
                server.send("execute if block 3 300 3 minecraft:amethyst_block run say PACKAGED_WORLD", r"\[Server\] PACKAGED_WORLD")
                server.send("data get storage unity_example:marks count", r"following .*: 1$")
                server.send("setblock 1 300 3 minecraft:barrel")
                for item,tag in (("oak_wood","natural_woods"),("cobblestone","cobblestones"),("red_dye","dyes"),("diamond_pickaxe","tools/mining_tool")):
                    server.send(f"item replace block 1 300 3 container.0 with minecraft:{item}")
                    label="TAG_OK_"+item
                    server.send(f"execute if items block 1 300 3 container.0 #c:{tag} run say {label}",r"\[Server\] "+label)
                server.send("save-all flush", "Saved the game")
            finally:
                server.close()
            checks[phase] = server.checks
        launcher = extracted / "test launcher"
        subprocess.run(["cmd.exe", "/d", "/c", "Install-Unity-Client.cmd", str(launcher)], cwd=extracted, env=clean_env(), check=True)
        profile = json.loads(next((launcher/"versions").glob("*/*.json")).read_bytes())
        if profile["inheritsFrom"] != "26.2" or profile["mainClass"] != "mc.unity.bootstrap.ClientEntry":
            raise AssertionError(profile)
        if digest(next((launcher/"libraries").rglob("*.jar"))) != digest(jar):
            raise AssertionError("Wrong client executable installed")
        if digest(extracted / "standalone-client/unity-mods/unity-client-witness.jar") != hashes["unity-client-witness.jar"]:
            raise AssertionError("Wrong client example installed")
        subprocess.run(["java", "standalone/Build.java", ".", str(bundle), str(home)], cwd=extracted, env=clean_env(), check=True)
        if hashes != {p.name: digest(p) for p in jar.parent.glob("*.jar")}:
            raise AssertionError("Source rebuild differed from distributed JARs")
        for side, expected in sample_hashes.items():
            after={p.name:digest(p,"sha512") for p in (extracted/side/"mods").glob("*.jar")}
            if after!=expected: raise AssertionError("Packaged original samples changed during runtime: "+side)
        adapter = extracted/"standalone/lib/unity-puzzles-platform-26.2.5.jar"
        rebuilt = extracted/"rebuilt-puzzles-platform.jar"
        subprocess.run(["python", "standalone/adapters/puzzles/build.py", options.adapter_refs.resolve(), home, samples, jar, rebuilt],
                       cwd=extracted, env=clean_env(), check=True)
        if digest(adapter)!=digest(rebuilt):raise AssertionError("Puzzles platform adapter source rebuild differs")
        client_api=extracted/"client-api-from-source.jar"
        subprocess.run(["python","standalone/adapters/client14/build.py",jar,home,client_api],cwd=extracted,env=clean_env(),check=True)
        if digest(client_api)!=digest(extracted/"standalone/lib/unity-client-api-0.2.3.jar"):raise AssertionError("Client renderer API source rebuild differed")
        probes=extracted/"world-probes-from-source"
        subprocess.run(["python","standalone/tests/worlds/build.py",options.world_refs.resolve(),options.fabric_refs.resolve(),cache,probes,home],cwd=extracted,env=clean_env(),check=True)
        fixture_hashes={p.relative_to(probes).as_posix():digest(p) for p in probes.rglob("*.jar")}
        published=extracted/"standalone/tests/worlds/fixtures"
        if fixture_hashes!={p.relative_to(published).as_posix():digest(p) for p in published.rglob("*.jar")}:raise AssertionError("Published API probes changed")
        adapter_hashes={}
        for source,name in [("ecology","unity-biome-adapter-0.2.3.jar"),("transfer","unity-transfer-adapter-0.2.3.jar")]:
            built=extracted/("rebuilt-"+name)
            subprocess.run(["python","standalone/adapters/"+source+"/build.py",jar,cache,built],cwd=extracted,env=clean_env(),check=True)
            if digest(built)!=digest(extracted/"standalone/lib"/name):raise AssertionError("Adapter rebuild differed: "+name)
            adapter_hashes[name]=digest(built)
        fabric_name=json.loads((extracted/"standalone/beta-gameplay.json").read_bytes())["fabricApi"]["url"].rsplit("/",1)[1]
        api_game=extracted/"api install test"
        for _ in range(2):
            subprocess.run([sys.executable,"tools/unity.py","fabric-api","--game-dir",str(api_game)],cwd=extracted,env=clean_env(),check=True)
        installed_api=digest(api_game/"mods"/fabric_name,"sha512")
        if installed_api!=json.loads((extracted/"standalone/beta-gameplay.json").read_bytes())["fabricApi"]["sha512"]:raise AssertionError("Packaged full API install differs")
        results = dict(installedFabricApi=installed_api,newAdapterRebuild=adapter_hashes,worldFixtureRebuild=fixture_hashes,clientApiRebuildSHA256=digest(client_api),adapterRebuildSHA256=digest(rebuilt), archiveSHA256=digest(archive), serverStarts=2, worldCount=1, checks=checks, installedSamples=sample_hashes, clientProfile=profile["mainClass"],
                       byteIdenticalRebuild=hashes, logSHA256={name:digest(EVIDENCE/name) for name in ("packaged-start.log","packaged-restart.log")})
    out = EVIDENCE/"unpacked.tmp"
    out.write_text(json.dumps(results,indent=2), encoding="utf8")
    out.replace(EVIDENCE/"unpacked.json")
    print("PASS packaged server, restart, client installer, byte-identical JDK build")


if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ("bundle","eula-from","client-home"):
        parser.add_argument("--"+name,required=True,type=Path)
    parser.add_argument("--fabric-refs",required=True,type=Path)
    parser.add_argument("--world-refs",required=True,type=Path)
    parser.add_argument("--adapter-refs",required=True,type=Path)
    parser.add_argument("--samples",required=True,type=Path)
    run(parser.parse_args())
