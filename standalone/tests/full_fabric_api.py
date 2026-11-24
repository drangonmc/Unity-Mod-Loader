"""Exercise the hash-locked full Fabric API distribution through Unity's normal server and client entrypoints."""
import argparse
import contextlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import tempfile
import zipfile

from acceptance import ROOT, DIST, EVIDENCE, VERSION, Server, clean_env, digest
from content import Window, observed, slot
from dual import exited
from server_configs import asset_fixture, verify_play, line


def contents(api, sdk):
    metadata = json.loads((ROOT / "standalone/beta-gameplay.json").read_bytes())["fabricApi"]
    if api.stat().st_size != metadata["size"] or digest(api, "sha512") != metadata["sha512"]:
        raise ValueError("Fabric API distribution differs from lock")
    rows = (ROOT/"standalone/src/main/resources/META-INF/unity/fabric-api-modules.sha256").read_text().splitlines()
    expected = {row.split()[1]: row for row in rows}
    assert len(rows) == len(expected) == 43
    sizes = {}
    with zipfile.ZipFile(api) as umbrella:
        for ref in json.loads(umbrella.read("fabric.mod.json"))["jars"]:
            data = umbrella.read(ref["file"])
            with zipfile.ZipFile(io.BytesIO(data)) as module:
                meta = json.loads(module.read("fabric.mod.json"))
                name = meta["id"]
                saved = sdk / (name+".jar")
                saved.write_bytes(data)
                side = meta.get("environment","*")
                assert expected[name] == " ".join([digest(saved),name,meta["version"],side])
                attrs = module.read("META-INF/MANIFEST.MF").decode().replace("\r\n ","")
                split = re.search(r"^Fabric-Loom-Client-Only-Entries: (.*)$",attrs,re.M)
                excluded=set(split[1].strip().split(";")) if split else set()
                public = [n for n in module.namelist() if n.startswith("net/fabricmc/fabric/api/") and n.endswith(".class") and not n.endswith("package-info.class")]
                sizes[name] = {"client":len(public),"server":None if side=="client" else sum(n not in excluded for n in public)}
    assert sizes.keys()==expected.keys()
    return sizes


def compile_probe(folder, sdk, home, cache, loader_sdk):
    sources = ROOT/"standalone/tests/beta"
    classes=folder/"classes";classes.mkdir()
    lock=json.loads((ROOT/"standalone/tests/interop/references.json").read_bytes())["fabric-loader"]["sha256"]
    assert digest(loader_sdk)==lock
    cp = [loader_sdk, *sorted(sdk.glob("*.jar")), home/"versions/26.2/26.2.jar", *sorted((cache/"libraries").rglob("*.jar"))]
    names=["FullFabricProbe","FullFabricClient","FullFabricDatagen","FabricPlayProbe","FabricPlayClient"]
    subprocess.run(["javac","-proc:none","-g:none","--release","25","-cp",os.pathsep.join(map(str,cp)),"-d",classes,
                    *[sources/(name+".java") for name in names]],check=True)
    probe=folder/"qa23-full-api.jar"
    with zipfile.ZipFile(probe,"w") as out:
        for f in sorted(classes.rglob("*.class")):out.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix()),f.read_bytes())
        out.writestr(zipfile.ZipInfo("fabric.mod.json"),json.dumps({"schemaVersion":1,"id":"qa23_full","version":"1.0","depends":{"fabric-api":"*"},
            "entrypoints":{"main":["qa.fabric.FullFabricProbe"],"client":["qa.fabric.FullFabricClient"],"fabric-datagen":["qa.fabric.FullFabricDatagen"]}}))
        out.writestr(zipfile.ZipInfo("qa23-official-modules.txt"),(ROOT/"standalone/src/main/resources/META-INF/unity/fabric-api-modules.sha256").read_bytes())
    return probe


def module_receipt(log, sizes, side):
    text=log.read_text("utf8",errors="replace")
    values=re.findall(r"QA23 MODULE ([a-z0-9-]+) classes=(\d+)",text)
    expected={k:v[side] for k,v in sizes.items() if v[side] is not None}
    assert len(values)==len(expected) and {name:int(n) for name,n in values}==expected,(log,values)
    assert f"QA23 FULL API {side.upper()} modules={len(expected)} classes={sum(expected.values())}" in text
    return expected


def run(options):
    cache=ROOT/"cache"/VERSION;home=cache/"client-home";bundle=cache/"mojang-server-26.2.jar"
    runtime=options.runtime.resolve() if options.runtime else DIST/f"unity-loader-{VERSION}.jar"
    api=options.api.resolve(); vision=options.vision.resolve()
    assert digest(vision,"sha512")==json.loads((ROOT/"standalone/fabric-play-sample.json").read_bytes())["sha512"]
    label=options.label
    if not re.fullmatch("[a-z0-9-]+",label):raise ValueError(label)
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="Fabric API ",dir=ROOT/".work") as temp:
        folder=Path(temp);sdk=folder/"sdk";sdk.mkdir()
        sizes=contents(api,sdk);probe=compile_probe(folder,sdk,home,cache,options.loader_sdk.resolve())
        asset=folder/"qa23-resources.jar";asset_fixture(asset)
        server=folder/"server";clients=[folder/"A",folder/"B"]
        jars={}
        for g in [server,*clients]:
            mods=g/"mods";mods.mkdir(parents=True);(g/"config").mkdir();(g/"config/qa23-play").touch()
            (g/"unity-mods").mkdir()
            for f in [api,vision,probe,asset]:shutil.copyfile(f,mods/f.name)
            if options.mixed:
                pack=json.loads((ROOT/"standalone/performance-pack.json").read_bytes())["files"]
                source=cache/("performance-samples" if g==server else "performance-client-samples")
                selected={m["filename"]:m["sha512"] for m in pack if g==server or (source/m["filename"]).is_file()}
                for name,h in selected.items():
                    destination=mods/name;shutil.copyfile(source/name,destination)
                    assert digest(destination,"sha512")==h
                jars[g.name]=selected
            sample="unity-world-marker.jar" if g==server else "unity-client-witness.jar"
            shutil.copyfile(DIST/sample,g/"unity-mods"/sample)
            if g!=server:
                (g/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:3\nmaxFps:30\nfullscreen:false\ntutorialStep:none\n")
        assert re.search(r"(?im)^eula\s*=\s*true\s*$",options.eula_from.read_text())
        shutil.copyfile(options.eula_from,server/"eula.txt")
        with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
        (server/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\npause-when-empty-seconds=-1\nlevel-seed=23023\nview-distance=3\nsimulation-distance=3\n")
        report={"loader":digest(runtime),"fabricApi":digest(api,"sha512"),"vision":digest(vision,"sha512"),"probe":digest(probe),"mixed":jars,"runs":[]}
        for iteration in ("first","restart"):
            phase=label+"-"+iteration
            with contextlib.ExitStack() as stack:
                srv=Server(server,phase,runtime,bundle,cache);stack.callback(srv.close)
                srv.expect("Standalone ready",120)
                log=EVIDENCE/(phase+".log")
                row={"serverModules":module_receipt(log,sizes,"server"),"command":srv.send("qa_full_api",r"QA23 FULL API COMMAND")}
                windows=[]
                for g in clients:
                    win=Window(home,g,phase+"-"+g.name,port,runtime,"Config"+g.name);stack.callback(exited,win);windows.append(win)
                status=[win.act("status",timeout=110) for win in windows]
                assert all(p["gpu"] and p["remote"] and not p["visible"] for p in status)
                logs=[EVIDENCE/(phase+"-"+g.name+"-client.log") for g in clients]
                row["clientModules"]=[module_receipt(f,sizes,"client") for f in logs]
                row["mixinTargets"]=[int(line(f,"QA23 FULL TRANSFORMS targets=").split("targets=")[1]) for f in [log,*logs]]
                assert row["mixinTargets"][0]>0 and row["mixinTargets"][1]==row["mixinTargets"][2]>=row["mixinTargets"][0]
                row["play"]=verify_play(srv,log,clients,logs,status)
                row["status"]=status
                for n,win in enumerate(windows):
                    if iteration=="first":srv.send(f"item replace entity Config{clients[n].name} hotbar.8 with lapis_lazuli {6+n*3}",r"Replaced")
                    slot(win,8,"minecraft:lapis_lazuli",6+n*3)
                srv.send("forceload add 0 0")
                if iteration=="first":srv.send("setblock 7 200 8 minecraft:barrel",r"Changed the block")
                observed(srv,"block 7 200 8 minecraft:barrel","QA23_FULL_WORLD")
                srv.send("reload",r"Reloading!")
                row["resource"]=srv.send("function qa23_pack:check",r"\[Server\] QA23_DATA_FUNCTION")
                srv.send("save-all flush",r"Saved the game")
                report["runs"].append(row)
        generated=folder/"generated";output=EVIDENCE/(label+"-datagen.log")
        with output.open("wb") as log:
            subprocess.run(["java","-Xmx2g","-Dfabric-api.datagen","-Dfabric-api.datagen.modid=qa23_full","-Dfabric-api.datagen.output-dir="+str(generated),
                "-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-jar",str(runtime),"--server-jar",str(bundle),"--runtime-dir",str(cache),"nogui"],
                check=True,timeout=150,cwd=server,stdout=log,stderr=subprocess.STDOUT,env=clean_env(),creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0))
        report["datagen"]=json.loads((generated/"data/qa23_full/audit.json").read_bytes())
        assert report["datagen"]=={"owner":"qa23_full"}
        report["logs"]={f.name:digest(f) for f in EVIDENCE.glob(label+"-*.log")}
        (EVIDENCE/(label+".json")).write_text(json.dumps(report,indent=2),"utf8")
        print("PASS original full Fabric API, two clients, restart and data generation")


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    for name in ["api","vision","eula-from","loader-sdk"]:parser.add_argument("--"+name,type=Path,required=True)
    parser.add_argument("--runtime",type=Path)
    parser.add_argument("--mixed",action="store_true")
    parser.add_argument("--label",default="full-fabric-api")
    run(parser.parse_args())
