"""Exercise configuration/login, external atomic saves and disconnect in two real clients."""
import argparse
import contextlib
import json
import os
import re
import shutil
import socket
import subprocess
import tempfile
import time
import zipfile
from pathlib import Path
from acceptance import ROOT, VERSION, DIST, EVIDENCE, Server, digest, clean_env
from content import Window, slot
from dual import exited

def line(file, marker):
    until = time.monotonic()+35
    while time.monotonic()<until:
        lines=file.read_text(encoding="utf8",errors="replace").splitlines()
        found=next((s for s in lines if marker in s),None)
        if found: return found
        time.sleep(.15)
    raise AssertionError((file,marker))

def phase_receipt(server_log,client_logs):
    server=server_log.read_text("utf8",errors="replace");clients={}
    for name,log in zip(("ConfigA","ConfigB"),client_logs):
        client=log.read_text("utf8",errors="replace")
        join=re.search(r"\b"+name+r" joined the game",server)
        if not join:raise AssertionError("Missing play transition "+name)
        result={}
        for platform,thread in [("NEO","Render thread"),("FABRIC","Netty")]:
            client_ids=re.findall(r"QA21 "+platform+r" CLIENT (\d+) "+thread,client)
            if len(client_ids)!=1:raise AssertionError((name,platform,client_ids))
            nonce=client_ids[0]
            ack=re.search(r"QA21 "+platform+" ACK "+nonce+r" input=Netty[^\n]*player="+name+r"\b",server)
            if not ack or not server.index("QA21 "+platform+" OFFER "+nonce)<ack.start()<join.start():raise AssertionError("Task acknowledgement outside configuration "+name)
            if platform=="NEO" and "complete=Server thread local=false" not in ack[0]:raise AssertionError(ack[0])
            result[platform]=int(nonce)
        clients[name]=result
    if any(clients["ConfigA"][p]==clients["ConfigB"][p] for p in ("NEO","FABRIC")):raise AssertionError("Challenge reused between concurrent sessions")
    return clients

def run(o):
    label=o.label
    if not re.fullmatch(r"[a-z0-9-]+",label):raise ValueError(label)
    cache=ROOT/"cache"/VERSION
    home=cache/"client-home"
    jar=DIST/f"unity-loader-{VERSION}.jar"
    bundle=cache/"mojang-server-26.2.jar"
    eula=o.eula_from.read_text()
    if not re.search(r"(?im)^eula\s*=\s*true\s*$",eula):raise ValueError("Local test EULA required")
    EVIDENCE.mkdir(exist_ok=True)
    subprocess.run(["java","-jar",jar,"--install-client",home],env=clean_env(),check=True)
    profile=home/"libraries/mc/unity/unity-loader"/VERSION/jar.name
    assert digest(profile)==digest(jar)
    lock=json.loads((ROOT/"standalone/tests/interop/references.json").read_bytes())
    refs=[o.refs/(k+".jar") for k in ("neo","neo-bus","neo-loader","forge","bus","fmlcore","fmlloader","javafmllanguage")]
    assert all(digest(p)==lock[p.stem]["sha256"] for p in refs)
    with tempfile.TemporaryDirectory(prefix="server-config-",dir=ROOT/".work") as temp:
        root=Path(temp); srv=root/"server"; clients=[root/"A",root/"B"]
        classes=root/"classes";classes.mkdir()
        cp=[*refs,o.refs/"dist.jar",home/"versions/26.2/26.2.jar",*sorted((cache/"libraries").rglob("*.jar")),*sorted((ROOT/"standalone/lib").glob("nightconfig*.jar"))]
        if o.fabric_refs:
            cp.extend([*sorted((ROOT/"standalone/lib").glob("*mixin*.jar")),o.refs/"fabric-loader.jar",*sorted(o.fabric_refs.glob("fabric-object-builder-api-v1-*.jar")),*sorted(o.fabric_refs.glob("fabric-loot-api-v3-*.jar")),*sorted(o.refs.glob("fabric-rendering-v1-*.jar")),*sorted(o.refs.glob("fabric-api-base-*.jar"))])
        if o.fabric_refs:
            for name in ["fabric-networking-api-v1","fabric-lifecycle-events-v1","fabric-data-attachment-api-v1","fabric-command-api-v2","fabric-transfer-api-v1","fabric-api-lookup-api-v1"]:
                cp.extend(sorted(o.fabric_refs.glob(name+"-*.jar")))
        if o.fabric_refs:
            sdk=json.loads((ROOT/"standalone/tests/worlds/fabric-references.json").read_bytes())["modules"]
            for file in cp:
                if file.name in sdk and digest(file)!=sdk[file.name]:raise ValueError("Changed Fabric SDK "+file.name)
            if digest(o.refs/"dist.jar")!=json.loads((ROOT/"standalone/tests/worlds/dist-reference.json").read_bytes())["sha256"] or digest(o.refs/"fabric-loader.jar")!=lock["fabric-loader"]["sha256"]:raise ValueError("Changed loader SDK")
        fabric_sources=sorted((Path(__file__).parent/"beta").glob("Fabric*.java")) if o.fabric_refs else []
        subprocess.run(["javac","-proc:none","--release","25","-g:none","-cp",os.pathsep.join(map(str,cp)),"-d",classes,Path(__file__).parent/"beta/ServerConfigProbe.java",Path(__file__).parent/"beta/NeoPhase.java",*[Path(__file__).parent/"beta"/n for n in ("LoginCapacity.java","MultiForgeProbe.java","MultiNeoProbe.java")],Path(__file__).parent/"beta/ForgeServerConfigProbe.java",Path(__file__).parent/"beta/ForgeInventoryProbe.java",Path(__file__).parent/"beta/ForgeLootProbe.java",*fabric_sources],check=True)
        layer_source=root/"VersionLayer.java"
        layer_source.write_text("package qa.forgeconfig; class VersionLayer {static int value(){return 25;}}")
        layer=root/"java25";layer.mkdir()
        subprocess.run(["javac","-proc:none","--release","25","-g:none","-d",layer,layer_source],check=True)
        neo_meta='modLoader="javafml"\nloaderVersion="*"\n[[mods]]\nmodId="qa_server_config"\nversion="1.0"\n[[mods]]\nmodId="qa_archive_neo"\nversion="2.0"\n'
        neo_meta+=r'displayName = "A \"quoted\" name"'+'\n'
        neo_meta+='description = '+chr(34)*3+'\nLiteral tables in prose:\n[[mixins]]\nconfig = "absent.json"\n[[accessTransformers]]\nfile = "absent.cfg"\n'+chr(34)*3+'\n'
        neo_meta+='[modproperties.qa_archive_neo]\nweights=[1,2,3]\nenabled=true\nrelease=22\n[[dependencies.qa_archive_neo]]\nmodId="qa_server_config"\ntype="required"\nordering="AFTER"\n'
        probe=root/"qa_server_config.jar"
        with zipfile.ZipFile(probe,"w") as z:
            for c in sorted((classes/"qa/config").rglob("*.class")): z.writestr(zipfile.ZipInfo(c.relative_to(classes).as_posix()),c.read_bytes())
            z.writestr(zipfile.ZipInfo("META-INF/neoforge.mods.toml"),neo_meta)
        forge=root/"qa_forge_config.jar"
        with zipfile.ZipFile(forge,"w") as z:
            for c in sorted((classes/"qa/forgeconfig").rglob("*.class")):z.writestr(zipfile.ZipInfo(c.relative_to(classes).as_posix()),c.read_bytes())
            z.writestr(zipfile.ZipInfo("META-INF/MANIFEST.MF"),"Manifest-Version: 1.0\r\nMulti-Release: true\r\n\r\n")
            z.writestr(zipfile.ZipInfo("qa22/layer.txt"),"base")
            z.writestr(zipfile.ZipInfo("META-INF/versions/25/qa22/layer.txt"),"java25")
            z.writestr(zipfile.ZipInfo("META-INF/versions/25/qa/forgeconfig/VersionLayer.class"),(layer/"qa/forgeconfig/VersionLayer.class").read_bytes())
            z.writestr(zipfile.ZipInfo("META-INF/versions/99/qa/forgeconfig/VersionLayer.class"),(classes/"qa/forgeconfig/VersionLayer.class").read_bytes())
            z.writestr(zipfile.ZipInfo("META-INF/mods.toml"),'modLoader="javafml"\nloaderVersion="[65,)"\nlicense="MIT"\n[[mods]]\nmodId="qa_forge_config"\nversion="1.0"\n[[mods]]\nmodId="qa_archive_mod"\nversion="2.0"\n[[dependencies.qa_archive_mod]]\nmodId="qa_forge_config"\nmandatory=true\nordering="AFTER"\n')

        fabric=root/"qa_fabric_ecology.jar"
        if o.fabric_refs:
            with zipfile.ZipFile(fabric,"w") as z:
                for c in sorted((classes/"qa/fabric").rglob("*.class")):z.writestr(zipfile.ZipInfo(c.relative_to(classes).as_posix()),c.read_bytes())
                z.writestr(zipfile.ZipInfo("fabric.mod.json"),json.dumps({"schemaVersion":1,"id":"qa_fabric_ecology","version":"1.0","entrypoints":{"main":["qa.fabric.FabricEcologyProbe"],"client":["qa.fabric.FabricEcologyClient"]},"mixins":["qa21-wire.mixins.json"],"depends":{"fabric-loot-api-v3":"*","fabric-object-builder-api-v1":"*","fabric-networking-api-v1":"*"}}))
                z.writestr(zipfile.ZipInfo("qa21-wire.mixins.json"),json.dumps({"required":True,"package":"qa.fabric.audit","compatibilityLevel":"JAVA_25","mixins":["FabricWireAudit"],"injectors":{"defaultRequire":1}}))
                z.writestr(zipfile.ZipInfo("data/qa_fabric_ecology/loot_table/sample.json"),'{"type":"minecraft:generic","pools":[]}')
        if o.phase_fixtures:
            o.phase_fixtures.mkdir(exist_ok=True,parents=True)
            for fixture in [probe,forge,fabric]:shutil.copy2(fixture,o.phase_fixtures)
        for g in [srv,*clients]:
            mods=g/"mods";mods.mkdir(parents=True);shutil.copy2(probe,mods);shutil.copy2(forge,mods)
            if o.fabric_refs:shutil.copy2(fabric,mods)
            if o.samples:
                for f in sorted(o.samples.glob("*.jar")):
                    if g!=srv and f.name in {x["filename"] for x in json.loads((ROOT/"standalone/forge-samples.json").read_bytes())}:continue
                    shutil.copy2(f,mods)
            native=g/"unity-mods";native.mkdir()
            shutil.copy2(DIST/("unity-world-marker.jar" if g==srv else "unity-client-witness.jar"),native)
        with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
        (srv/"eula.txt").write_text(eula)
        (srv/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\npause-when-empty-seconds=-1\nlevel-seed=262020\nview-distance=3\nsimulation-distance=3\n")
        path=srv/"world/serverconfig/qa_server_config-server.toml"
        path.parent.mkdir(parents=True);path.write_text("limit=23\n")
        forgepath=path.with_name("qa_forge_config-server.toml");forgepath.write_text("limit=31\n")
        def replace(value):
            tmp=path.with_suffix(".tmp");tmp.write_text(value);tmp.replace(path)
        sentinels=[]
        for index,g in enumerate(clients):
            (g/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:3\nmaxFps:30\nfullscreen:false\ntutorialStep:none\n")
            local=g/"config/qa_server_config-server.toml";local.parent.mkdir()
            local.write_text(f"limit={90+index}\n");sentinels.append(digest(local))
        data_lock=json.loads((ROOT/"standalone/universal-data-sample.json").read_bytes())
        raw=o.universal_sample or (cache/"universal-data-sample"/data_lock["filename"])
        if o.universal_sample is None and not raw.is_file():
            import urllib.request
            raw.parent.mkdir(exist_ok=True,parents=True)
            with tempfile.TemporaryDirectory(dir=raw.parent) as staging:
                candidate=Path(staging)/raw.name
                req=urllib.request.Request(data_lock["url"],headers={"User-Agent":"Unity compatibility check/0.2.2"})
                with urllib.request.urlopen(req,timeout=75) as response:candidate.write_bytes(response.read(data_lock["size"]+1))
                if candidate.stat().st_size!=data_lock["size"] or digest(candidate,"sha512")!=data_lock["sha512"]:raise ValueError("Changed original data archive")
                candidate.replace(raw)
        if digest(raw,"sha512")!=data_lock["sha512"]:raise ValueError("Changed author data pack")
        shutil.copy2(raw,srv/"mods")
        report={"dataArchive":data_lock["filename"],"loader":digest(jar),"probe":digest(probe),"forgeProbe":digest(forge),"samples":{p.name:digest(p,"sha512") for p in sorted((srv/"mods").glob("*.jar")) if p.name not in {probe.name,forge.name,fabric.name}}}
        logs=[EVIDENCE/(f"{label}-{g.name}-client.log") for g in clients]
        if o.fabric_refs:
            report["fabricProbe"]=digest(fabric)
            pack=srv/"world/datapacks/qa";folder=pack/"data/qa_fabric_ecology/loot_table";folder.mkdir(parents=True)
            (pack/"pack.mcmeta").write_text('{"pack":{"description":"QA","min_format":107,"max_format":107}}')
            data=pack/"data/qa_fabric_ecology"
            resources={
                "worldgen/configured_feature/sky_marker":{"type":"minecraft:simple_block","config":{"to_place":{"type":"minecraft:simple_state_provider","state":{"Name":"minecraft:amethyst_block"}}}},
                "worldgen/placed_feature/sky_marker":{"feature":"qa_fabric_ecology:sky_marker","placement":[{"type":"minecraft:height_range","height":{"type":"minecraft:uniform","min_inclusive":{"absolute":250},"max_inclusive":{"absolute":250}}}]},
                "forge/biome_modifier/sky_markers":{"type":"forge:add_features","biomes":"#minecraft:is_overworld","features":"qa_fabric_ecology:sky_marker","step":"top_layer_modification"},
                "neoforge/biome_modifier/pigs":{"type":"neoforge:add_spawns","biomes":"minecraft:plains","spawners":[{"type":"qa_fabric_ecology:pig","weight":17,"minCount":2,"maxCount":4}]}
            }
            for platform,table in [("forge","sample"),("neoforge","world_only")]:
                listing=pack/"data"/platform/"loot_modifiers/global_loot_modifiers.json"
                listing.parent.mkdir(parents=True)
                listing.write_text(json.dumps({"replace":False,"entries":["qa_fabric_ecology:"+platform]}))
                resources["loot_modifiers/"+platform]={"type":"qa_forge_config:reward" if platform=="forge" else "neoforge:add_table","conditions":[{"condition":platform+":loot_table_id","loot_table_id":"qa_fabric_ecology:"+table}]}
            resources["loot_modifiers/neoforge"]["table"]="qa_fabric_ecology:bonus"
            resources["loot_table/bonus"]={"type":"minecraft:generic","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:prismarine_crystals"}]}]}
            for name,body in resources.items():
                file=data/(name+".json");file.parent.mkdir(parents=True,exist_ok=True);file.write_text(json.dumps(body))
            (folder/"world_only.json").write_text('{"type":"minecraft:generic","pools":[]}')
        for g in [srv,*clients]:
            (g/"config").mkdir(exist_ok=True)
            (g/"config/qa22-capacity").touch()
        with contextlib.ExitStack() as stack:
            server=Server(srv,label,jar,bundle,cache);stack.callback(server.close)
            server.expect("Standalone ready",150)
            marker=clients[0]/"config/qa21-negative";marker.touch()
            rejected=Window(home,clients[0],label+"-negative",port,profile,"ConfigA")
            try:
                report["phaseMismatch"]=server.expect(r"Disconnecting ConfigA .*content registry mismatch",150)
                line(EVIDENCE/(label+"-negative-client.log"),"Server registry match=false")
            finally:
                exited(rejected);marker.unlink()
            abort=clients[0]/"config/qa22-abort";abort.touch()
            client=Window(home,clients[0],label+"-abort",port,profile,"ConfigA")
            try:
                report["aborted"]=server.expect("QA22 CONFIG END ConfigA",150)
                line(EVIDENCE/(label+"-abort-client.log"),"QA22 ABORT before task completion")
            finally:
                exited(client);abort.unlink()
            windows=[]
            for g in clients:
                win=Window(home,g,label+"-"+g.name,port,profile,"Config"+g.name)
                stack.callback(exited,win);windows.append(win)
            status=[win.act("status",timeout=180) for win in windows]
            assert all(s["remote"] and s["gpu"] and not s["visible"] for s in status)
            report["status"]=status
            report["archiveRecipe"]=server.send("qa_craft_archive",r"QA22 UNIVERSAL recipe=craftable_trident:trident result=minecraft:trident count=1")
            report["clientFallback"]=[line(log,"QA22 CLIENT ACTIONS vanillaBreak=true vanillaHarvest=1") for log in logs]
            report["modelBake"]=[line(log,"QA_MODEL custom layer baked") for log in logs]
            server.send("execute at ConfigA run summon qa_server_config:pig ~ ~ ~ {NoAI:1b,PersistenceRequired:1b}",r"Summoned new")
            report["mobHealth"]=server.send("execute as ConfigA at @s run attribute @e[type=qa_server_config:pig,sort=nearest,limit=1] minecraft:max_health get",r"47")
            server.send("reload",r"Reloading!")
            server.expect("QA_RELOAD first");server.expect("QA_RELOAD second")
            server.expect("QA_RELOAD forge")
            if o.fabric_refs:
                server.send("execute at ConfigA run summon qa_fabric_ecology:pig ~ ~ ~ {NoAI:1b}",r"Summoned new")
                report["fabricHealth"]=server.send("execute as ConfigA at @s run attribute @e[type=qa_fabric_ecology:pig,sort=nearest,limit=1] minecraft:max_health get",r"53")
                for player,log in zip(("ConfigA","ConfigB"),logs):
                    server.send("execute as "+player+" run qa_phase_play")
                    line(log,"QA21 PLAY play-"+player)
                report["biomes"]=server.send("qa_ecology",r"QA_ECOLOGY .*markers=[1-9]",90)
                server.send("forceload add 8 8")
                server.send("setblock 8 200 8 minecraft:stone")
                server.send("setblock 7 201 8 minecraft:barrel",r"Changed the block")
                server.send("setblock 8 201 8 minecraft:bell",r"Changed the block")
                report["transfer"]=server.send("qa_transfer",r"QA_TRANSFER simulated=7 aborted=7 committed=5 invalidated=true")
                report["resources"]=line(EVIDENCE/(label+".log"),"QA_RESOURCES energy=23 fluidMb=750 droplets=60750")
                report["attachmentWrite"]=server.send("qa_attachment_save",r"QA_ATTACHMENT stored tile=42 chunk=43")
                for table,source in [("sample","MOD"),("world_only","DATA_PACK")]:
                    line(EVIDENCE/(label+".log"),"QA_LOOT qa_fabric_ecology:"+table+" "+source)
                    server.send("loot give ConfigA loot qa_fabric_ecology:"+table,r"Dropped|Given")
                report["loot"]=server.send("clear ConfigA minecraft:amethyst_shard 0",r"Found 3 matching")
                for name in ["echo_shard","prismarine_crystals"]:
                    report[name]=server.send("clear ConfigA minecraft:"+name+" 0",r"Found 1 matching")
            for i,g in enumerate(clients):
                server.send(f"item replace entity Config{g.name} hotbar.8 with minecraft:lapis_lazuli {6+3*i}",r"Replaced a slot")
                slot(windows[i],8,"minecraft:lapis_lazuli",6+3*i)
            report["initial"]=[line(log,"QA_SERVER_CONFIG load 23 Render thread") for log in logs]
            report["forgeInitial"]=[line(log,"QA_FORGE_CONFIG load 31 Render thread") for log in logs]
            replace("limit=120\n")
            server.expect("qa_server_config-server.toml.*Invalid values",30)
            replace("limit=71\n")
            server.expect("QA_SERVER_CONFIG reload 71 Server thread",30)
            forgepath.write_text("limit=59\n");server.expect("QA_FORGE_CONFIG reload 59 Server thread",30)
            report["forgeUpdated"]=[line(log,"QA_FORGE_CONFIG reload 59 Render thread") for log in logs]
            report["updated"]=[line(log,"QA_SERVER_CONFIG reload 71 Render thread") for log in logs]
        for i,g in enumerate(clients):
            assert digest(g/"config/qa_server_config-server.toml")==sentinels[i]
            line(logs[i],"QA_SERVER_CONFIG unload 71");line(logs[i],"QA_FORGE_CONFIG unload 59")
            assert "reload 120" not in logs[i].read_text(encoding="utf8",errors="replace")
        (srv/"config").mkdir(exist_ok=True);(srv/"config/qa21-new-schema").touch()
        with contextlib.closing(Server(srv,label+"-restart",jar,bundle,cache)) as second:
            second.expect("QA_SERVER_CONFIG load 71 Server thread",150);second.expect("Standalone ready",150)
            if o.fabric_refs:
                from content import observed
                observed(second,'data block 7 201 8 Items[{Slot:0b,id:"minecraft:emerald",count:5}]',"QA_RESTART_BARREL")
                report["restartBarrel"]=5
                with contextlib.ExitStack() as peers:
                    reopened=[]
                    for g in clients:
                        w=Window(home,g,label+"-restart-"+g.name,port,profile,"Config"+g.name);peers.callback(exited,w);reopened.append(w)
                        logs.append(EVIDENCE/(label+"-restart-"+g.name+"-client.log"))
                    report["reconnected"]=[w.act("status",timeout=180) for w in reopened]
                    if [p["uuid"] for p in report["status"]]!=[p["uuid"] for p in report["reconnected"]]:raise AssertionError("Player identity changed")
                    for i,g in enumerate(clients):
                        observed(second,f'data entity Config{g.name} Inventory[{{Slot:8b,id:"minecraft:lapis_lazuli",count:{6+3*i}}}]',"QA_SAVED_BAG_"+g.name)
                        slot(reopened[i],8,"minecraft:lapis_lazuli",6+3*i)
                    report["savedBags"]=[6,9]
                    report["attachmentRead"]=second.send("qa_attachment_read",r"QA_ATTACHMENT restored tile=42 chunk=43")
        report["configurationPhases"]={
            "first":phase_receipt(EVIDENCE/(label+".log"),logs[:2]),
            "restart":phase_receipt(EVIDENCE/(label+"-restart.log"),logs[2:4])}
        report["quarantine"]=line(EVIDENCE/(label+"-restart.log"),"QA21 QUARANTINE healthy=4 defaults=4 originalNBT=4")
        report["tanks"]=line(EVIDENCE/(label+".log"),"QA21 TANKS count=32 enumerations=1")
        server_log=EVIDENCE/(label+".log")
        report["loginCapacity"]=line(server_log,"Login capabilities play=256 configuration=true extended=true")
        report["multiForge"]=line(server_log,"QA22 FORGE MULTIMOD java=25")
        report["multiNeo"]=line(server_log,"QA22 NEO MULTIMOD owner=qa_archive_neo")
        logs.extend([EVIDENCE/(label+"-abort-client.log"),EVIDENCE/(label+"-negative-client.log")])
        report["logs"]={p.name:digest(p) for p in [*logs,EVIDENCE/(label+".log"),EVIDENCE/(label+"-restart.log")]}
        (EVIDENCE/(label+".json")).write_text(json.dumps(report,indent=2))
    print("PASS two remote clients, initial SERVER config, rejected edit, hot reload, disconnect and restart")
if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--fabric-refs",type=Path)
    parser.add_argument("--universal-sample",type=Path)
    parser.add_argument("--phase-fixtures",type=Path)
    parser.add_argument("--label",default="server-config")
    parser.add_argument("--refs",type=Path,required=True)
    parser.add_argument("--eula-from",type=Path,required=True)
    parser.add_argument("--samples",type=Path)
    run(parser.parse_args())
