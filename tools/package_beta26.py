"""Package matched Paper/Unity edit results and verify a clean extraction before installing the Beta."""
import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import zipfile
from pathlib import Path
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/"standalone/tests"))
import acceptance
import unpacked
from acceptance import digest

VERSION = "0.2.6-Beta"
LOADER = f"unity-loader-{VERSION}.jar"
MAIN_JARS = [LOADER,"unity-world-marker.jar","unity-client-witness.jar","UnityEditRegions.jar"]

def validated(options):
    reports = {}
    for host in ("unity","paper"):
        folder = options.runs/host
        result = json.loads((folder/"result.json").read_bytes())
        for name,h in result["logs"].items():
            if Path(name).name != name or digest(folder/name) != h:
                raise ValueError(f"Changed {host} evidence: {name}")
        assert result["client"] == digest(options.dist/LOADER)
        assert result["witness"] == digest(options.dist/"unity-client-witness.jar")
        assert result["regionIntegration"] == digest(options.dist/"UnityEditRegions.jar")
        assert len(result["runs"])==2 and result["runs"][0]==result["runs"][1]
        assert len(set(result["runs"][0]["players"]))==2
        reports[host]=result
    unity,paper=reports["unity"],reports["paper"]
    assert unity["runtime"]==unity["client"] and unity["neoForgeWitness"]
    assert len(unity["mixed"]["server"])>=73 and all(len(unity["mixed"][n])>=71 for n in ("VolumeA","VolumeB"))
    assert not paper["mixed"] and paper["runtime"]=="0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
    for key in ("authors","runs","plugin"):
        assert unity[key]==paper[key],key
    pins=json.loads((ROOT/"standalone/tests/reference/protection-originals.json").read_bytes())
    assert unity["authors"]=={p["slug"]:p["sha512"] for p in pins if p["slug"] in ("worldedit","worldguard")}
    return reports

def main(o):
    reports=validated(o)
    evidence=ROOT/"evidence"/VERSION
    stage=ROOT/".work/v26/distribution"
    stage.mkdir(parents=True,exist_ok=True)
    evidence.mkdir(parents=True,exist_ok=True)
    target=ROOT/"versions"/VERSION
    archive=stage/f"Unity-{VERSION}-MC26.2-Standalone.zip"
    verified={"minecraft":"26.2","version":VERSION,
              "artifacts":{n:digest(o.dist/n) for n in MAIN_JARS},
              "reports":{h:digest(o.runs/h/"result.json") for h in reports}}
    (evidence/"verified.json").write_text(json.dumps(verified,indent=2),"utf8")
    for host in reports:
        out=evidence/host
        out.mkdir(exist_ok=True)
        shutil.copy2(o.runs/host/"result.json",out/"result.json")
        # Publish the relevant game observations. Keep machine paths and environment diagnostics local.
        excerpts = []
        for name in sorted(reports[host]["logs"]):
            if not name.endswith(".log"): continue
            excerpts.append("[" + name + "]")
            for raw in (o.runs/host/name).read_text("utf8",errors="replace").splitlines():
                line = re.sub(r"\x1b\[[0-9;]*m","",raw)
                if any(s in line for s in ("Operation completed", "available edits", "clipboard has been pasted",
                          "has the following block data:", "REGION_CHEST_INTACT", "UNITY_NEO26_TICKS_OK",
                          "has the following entity data:", "BLOCK_OK_gold_block", "BLOCK_OK_smooth_stone")):
                    excerpts.append(line)
        (out/"observations.txt").write_text("\n".join(excerpts)+"\n","utf8")
    readme=(ROOT/"standalone/BETA-0.2.6.md").read_bytes()
    files={"README.md":readme}
    for n in ("LICENSE",".clang-format","Install-Unity-Samples.cmd","Install-Unity-Fabric-API.cmd",
              "tools/unity.py","tools/plugin-api.lock.json","tools/plugins.lock.json","tools/package_beta26.py",
              "tests/check.py","src/main/java/mc/unity/api/UnityMod.java","src/main/java/mc/unity/api/PluginHost.java",
              "src/main/java/mc/unity/loader/NativeLoader.java","src/main/java/net/minecraftforge/fml/common/Mod.java"):
        files[n]=(ROOT/n).read_bytes()
    for n in ("Install-Unity-Client.cmd","Start-Unity.cmd"):
        files[n]=re.sub(rb"0\.2\.\d+-Beta",VERSION.encode(),(ROOT/n).read_bytes())
    for base in ("standalone","src/plugin"):
        for f in sorted((ROOT/base).rglob("*")):
            if not f.is_file() or any(p.startswith(".") or p=="__pycache__" for p in f.relative_to(ROOT).parts):continue
            if f.parent==ROOT/"standalone" and f.suffix==".md" and f.name!="BETA-0.2.6.md":continue
            if f.parent==ROOT/"standalone/lib" and f.suffix==".jar" and not any(
                    row.endswith("  "+f.name) for row in (f.parent/"SHA256SUMS").read_text().splitlines()):continue
            files[f.relative_to(ROOT).as_posix()]=f.read_bytes()
    for n in MAIN_JARS: files[f"versions/{VERSION}/{n}"]=(o.dist/n).read_bytes()
    for n in ("verified.json","unity/result.json","paper/result.json","unity/observations.txt","paper/observations.txt"):
        files[f"evidence/{VERSION}/{n}"]=(evidence/n).read_bytes()
    with zipfile.ZipFile(archive,"w") as z:
        for n,data in sorted(files.items()):
            z.writestr(zipfile.ZipInfo(n,(2026,1,1,0,0,0)),data,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(archive) as z: assert z.testzip() is None
    unpacked.EVIDENCE=acceptance.EVIDENCE=evidence
    unpacked.run(SimpleNamespace(version=VERSION,archive=archive,bundle=o.cache/"mojang-server-26.2.jar",
        client_home=o.cache/"client-home",eula_from=o.eula,samples=o.cache/"samples",adapter_refs=o.adapter_refs,
        world_refs=o.world_refs,fabric_refs=o.adapter_refs,worldedit_jar=o.worldedit))
    check=json.loads((evidence/"unpacked.json").read_bytes())
    assert check["archiveSHA256"]==digest(archive) and check["byteIdenticalRebuild"]==verified["artifacts"]
    target.mkdir(parents=True,exist_ok=True)
    for f in [*(o.dist/n for n in MAIN_JARS),archive]:
        pending=target/(f.name+".tmp")
        shutil.copy2(f,pending)
        os.replace(pending,target/f.name)
    delivered = target/archive.name
    print("Verified",delivered,digest(delivered))
if __name__=="__main__":
    parser=argparse.ArgumentParser()
    for k in ("runs","dist","cache","eula","adapter-refs","world-refs","worldedit"):
        parser.add_argument("--"+k,type=lambda s:Path(s).resolve(),required=True)
    main(parser.parse_args())
