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

VERSION = "0.2.9-Beta"

def validated(options, version=VERSION, alternate=False):
    reports = {}
    runs=options.variant_runs if alternate else options.runs
    for host in ("unity","paper"):
        folder = runs/host
        result = json.loads((folder/"result.json").read_bytes())
        for name,h in result["logs"].items():
            if Path(name).name != name or digest(folder/name) != h:
                raise ValueError(f"Changed {host} evidence: {name}")
        assert result["client"] == digest(options.dist/f"unity-loader-{version}.jar")
        assert result["witness"] == digest(options.dist/"unity-client-witness.jar")
        assert result["regionIntegration"] == digest(options.dist/"UnityEditRegions.jar")
        assert len(result["runs"])==2 and result["runs"][0]==result["runs"][1]
        assert len(set(result["runs"][0]["players"]))==2
        reports[host]=result
    unity,paper=reports["unity"],reports["paper"]
    assert unity["runtime"]==unity["client"] and unity["neoForgeWitness"]
    assert len(unity["mixed"]["server"])>=73 and all(len(unity["mixed"][n])>=71 for n in ("VolumeA","VolumeB"))
    assert not paper["mixed"] and paper["runtime"]=="0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
    for key in ("authors","plugin"):
        assert unity[key]==paper[key],key
    for live,reference in zip(unity["runs"],paper["runs"]):
        common=dict(live)
        if version in ("0.2.7-Beta","0.2.8-Beta"):
            machine_pins=json.loads((ROOT/"standalone/tests/reference/machines-26.2.json").read_bytes())
            assert unity["machineOriginals"]=={r["project"]:r["sha512"] for r in machine_pins}
            for phase in ("first","restart"):
                log=(runs/"unity"/(phase+".log")).read_text("utf8",errors="replace")
                if phase=="first": assert "UNITY_NEO27_FUEL_OK duration=240" in log
                for marker in ("FURNACE_COOKED","CUSTOM_FUEL_COOKED","MACHINE_FUEL_TRANSFER","MACHINE_FUEL_FILTER",
                               "HOPPER_OUTPUT","HOPPER_FILTER","HOPPER_REJECTED"):
                    assert re.search(r"\[Server\] "+marker+r"\b",log), (phase,marker)
            machines=common.pop("machines")
            assert set(machines)=={"advancedfurnace:advanced_furnace","copperhopper:copper_hopper"}
            for machine in machines.values():
                assert machine["destination"]==machine["source"]
                assert machine["region"]["name"]=="Machine-protected"
                regional_copy=machine["regionalCopy"]
                for key in ("item","count","slot","name","marker","facing"):
                    assert regional_copy[key]==machine["region"][key],key
                assert len(regional_copy["clients"])==2 and len(machine["source"]["clients"])==2
                assert all(c["marker"]==machine["source"]["marker"] for c in machine["source"]["clients"])
        if version=="0.2.8-Beta":
            review=common.pop("review28")
            assert review["bukkitObserved"]==2 and review["restoredByBukkit"]==1 and review["effect"]=="unity28registry:marked"
            first=(runs/"unity/first.log").read_text("utf8",errors="replace")
            for needle in ("UNITY28_REGISTRIES_OK","UNITY28_BUKKIT_BREAK 35 true","UNITY28_BUKKIT_BREAK 37 false",
                           "UNITY28_DROP_ONLY","[Server] NO_ITEM_DROP","[Server] NORMAL_ITEM_DROP","[Server] VANILLA_XP"):
                assert needle in first,needle
        assert common==reference
    pins=json.loads((ROOT/"standalone/tests/reference/protection-originals.json").read_bytes())
    expected={p["slug"]:p["sha512"] for p in pins if p["slug"] in ("worldedit","worldguard")}
    if alternate:
        targets=json.loads((ROOT/"standalone/tests/reference/compat-26.2.json").read_bytes())
        expected["worldedit"]=targets["worldedit"]["hashes"]["sha512"]
        for installed in unity["platformJars"].values():
            for key in ("fabric-api","puzzles-lib"):assert installed[key]["sha512"]==targets[key]["hashes"]["sha512"]
    assert unity["authors"]==expected
    return reports

def main(o):
    version=getattr(o,"version",VERSION)
    number=version.split("-")[0]
    jars=[f"unity-loader-{version}.jar","unity-world-marker.jar","unity-client-witness.jar","UnityEditRegions.jar"]
    if version=="0.2.9-Beta":
        from validate_beta29 import validate
        reports=validate(ROOT,o,digest)
    else:reports=validated(o, version)
    evidence=ROOT/"evidence"/version
    stage=ROOT/".work"/version/"distribution"
    stage.mkdir(parents=True,exist_ok=True)
    evidence.mkdir(parents=True,exist_ok=True)
    target=ROOT/"versions"/version
    archive=stage/(f"Unity-{version}-rebuild.1-MC26.2-Standalone.zip" if version=="0.2.9-Beta" else f"Unity-{version}-MC26.2-Standalone.zip")
    more={}
    if version=="0.2.8-Beta":
        validated(o,version,True)
        for host in ("unity","paper"):more[f"alternate/{host}/result.json"]=o.variant_runs/host/"result.json"
        contract=json.loads(o.contract_result.read_bytes())
        assert contract["loader"]==digest(o.dist/f"unity-loader-{version}.jar") and contract["strictRejected"]
        for n,checksum in contract["logs"].items():assert digest(o.contract_result.parent/n)==checksum
        more["contracts/result.json"]=o.contract_result
        more["contracts/contracts.log"]=o.contract_result.parent/"contracts.log"
        for n,src in more.items():
            dest=evidence/n;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dest)
    if version=="0.2.9-Beta":
        more["contracts/result.json"]=o.contract_result
        more["contracts/contracts.log"]=o.contract_result.parent/"contracts.log"
        more["lifecycle/lifecycle.json"]=evidence/"lifecycle/lifecycle.json"
        for n,src in more.items():
            dst=evidence/n
            if src.resolve()!=dst.resolve():
                dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dst)
    verified={"minecraft":"26.2","version":version,
              "artifacts":{n:digest(o.dist/n) for n in jars},
              "reports":{h:digest(o.runs/h/"result.json") for h in reports}}
    if more:verified["additionalReceipts"]={n:digest(path) for n,path in more.items()}
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
                if any(word in line.lower() for word in ("cookie header","authorization:","access_token=")):continue
                if any(s in line for s in ("Operation completed", "available edits", "clipboard has been pasted",
                          "has the following block data:", "REGION_CHEST_INTACT", "UNITY_NEO26_TICKS_OK",
                          "has the following entity data:", "BLOCK_OK_gold_block", "BLOCK_OK_smooth_stone",
                          "UNITY29 ", "UNITY29_PIPE_READY","[Server] FLUID_MOVED","[Server] ENERGY_MOVED","[Server] ITEM_MOVED","UNITY28_", "[Server] NO_ITEM_DROP","[Server] NORMAL_ITEM_DROP","[Server] VANILLA_XP","UNITY_NEO27_FUEL_OK", "[Server] MACHINE_FUEL", "[Server] CUSTOM_FUEL_COOKED",
                          "[Server] FURNACE_COOKED", "[Server] HOPPER_OUTPUT", "[Server] HOPPER_FILTER", "[Server] HOPPER_REJECTED")):
                    excerpts.append(line)
        (out/"observations.txt").write_text("\n".join(excerpts)+"\n","utf8")
    readme=(ROOT/f"standalone/BETA-{number}.md").read_bytes()
    files={"README.md":readme}
    for n in ("LICENSE",".clang-format","Install-Unity-Samples.cmd","Install-Unity-Fabric-API.cmd",
              "tools/unity.py","tools/plugin-api.lock.json","tools/plugins.lock.json","tools/package_beta26.py",
              "tests/check.py","src/main/java/mc/unity/api/UnityMod.java","src/main/java/mc/unity/api/PluginHost.java",
              "src/main/java/mc/unity/loader/NativeLoader.java","src/main/java/net/minecraftforge/fml/common/Mod.java"):
        files[n]=(ROOT/n).read_bytes()
    for n in ("Install-Unity-Client.cmd","Start-Unity.cmd"):
        files[n]=re.sub(rb"0\.2\.\d+-Beta",version.encode(),(ROOT/n).read_bytes())
    if version=="0.2.9-Beta":
        for n in ("tools/validate_beta29.py","tools/bukkit-library-resolver.lock.json"):files[n]=(ROOT/n).read_bytes()
    for base in ("standalone","src/plugin"):
        for f in sorted((ROOT/base).rglob("*")):
            if not f.is_file() or any(p.startswith(".") or p=="__pycache__" for p in f.relative_to(ROOT).parts):continue
            if f.parent==ROOT/"standalone" and f.suffix==".md" and f.name!=f"BETA-{number}.md":continue
            if f.parent==ROOT/"standalone/lib" and f.suffix==".jar" and not any(
                    row.endswith("  "+f.name) for row in (f.parent/"SHA256SUMS").read_text().splitlines()):continue
            files[f.relative_to(ROOT).as_posix()]=f.read_bytes()
    for n in jars: files[f"versions/{version}/{n}"]=(o.dist/n).read_bytes()
    for n in (*more,"verified.json","unity/result.json","paper/result.json","unity/observations.txt","paper/observations.txt"):
        files[f"evidence/{version}/{n}"]=(evidence/n).read_bytes()
    with zipfile.ZipFile(archive,"w") as z:
        for n,data in sorted(files.items()):
            z.writestr(zipfile.ZipInfo(n,(2026,1,1,0,0,0)),data,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(archive) as z: assert z.testzip() is None
    unpacked.EVIDENCE=acceptance.EVIDENCE=evidence
    unpacked.run(SimpleNamespace(version=version,archive=archive,bundle=o.cache/"mojang-server-26.2.jar",
        client_home=o.cache/"client-home",eula_from=o.eula,samples=o.cache/"samples",adapter_refs=o.adapter_refs,
        world_refs=o.world_refs,fabric_refs=o.adapter_refs,worldedit_jar=o.worldedit,neo_source=o.neo_source))
    check=json.loads((evidence/"unpacked.json").read_bytes())
    assert check["archiveSHA256"]==digest(archive) and check["byteIdenticalRebuild"]==verified["artifacts"]
    target.mkdir(parents=True,exist_ok=True)
    for f in [*(o.dist/n for n in jars),archive]:
        pending=target/(f.name+".tmp")
        shutil.copy2(f,pending)
        os.replace(pending,target/f.name)
    delivered = target/archive.name
    print("Verified",delivered,digest(delivered))
if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--version",choices=("0.2.6-Beta","0.2.7-Beta","0.2.8-Beta","0.2.9-Beta"),default=VERSION)
    parser.add_argument("--neo-source",type=lambda s:Path(s).resolve())
    parser.add_argument("--variant-runs",type=lambda s:Path(s).resolve())
    parser.add_argument("--contract-result",type=lambda s:Path(s).resolve())
    for k in ("runs","dist","cache","eula","adapter-refs","world-refs","worldedit"):
        parser.add_argument("--"+k,type=lambda s:Path(s).resolve(),required=True)
    main(parser.parse_args())
