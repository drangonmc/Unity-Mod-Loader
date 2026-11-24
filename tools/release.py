"""Package the verified independent runtime and activate it after unpacked acceptance."""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import tempfile
import zipfile

from beta20 import evidence as beta20_evidence
from beta21 import evidence as beta21_evidence
from beta22 import evidence as beta22_evidence
from beta23 import evidence as beta23_evidence
from unity import DIST, JAR, ROOT, VERSION, beta_samples

EVIDENCE = ROOT / "evidence" / VERSION
ARCHIVE = DIST / f"Unity-{VERSION}-MC26.2-Standalone.zip"
PENDING_ARCHIVE = ARCHIVE.with_name(ARCHIVE.name + ".candidate")
LAUNCHERS = (ROOT / "Start-Unity.cmd", ROOT / "Install-Unity-Client.cmd")
SHARED = ("mc/unity/api/UnityMod.java", "mc/unity/loader/NativeLoader.java", "net/minecraftforge/fml/common/Mod.java")


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def checked(condition, message):
    if not condition:
        raise ValueError(message)


def content_evidence(files):
    report=json.loads((EVIDENCE/"content.json").read_bytes())
    checked((report["version"],report["minecraft"])==(VERSION,"26.2"),"Content version")
    for key,file in zip(("loaderSHA256","worldMarkerSHA256","clientWitnessSHA256"),files):
        checked(report[key]==sha(file),"Content binary differs: "+file.name)
    specs=sum((json.loads((ROOT/"standalone"/name).read_bytes()) for name in ("forge-content.json","forge-samples.json")),[])
    checked(report["originalSHA512"]=={s["filename"]:s["sha512"] for s in specs},"Original Forge JARs")
    first,restart,negative=(report["runs"][name] for name in ("content-mixed","content-restart","content-mismatch"))
    checked(first["digest"]==restart["digest"] and set(first["registries"])==set(restart["registries"]),"Registry stability")
    for id,item in first["registries"].items():
        for state in (item,restart["registries"][id]):
            client=state["client"]
            checked(state["server"]==item["server"]==[client["itemNumber"],client.get("blockNumber",-1)] and
                    client["renderLayers"] and client["assetPack"]=="unity/"+id.split(":")[0],"Content model and ID "+id)
    checked(first["window"]["uuid"]==restart["window"]["uuid"] and first["window"]["remote"] and restart["window"]["remote"],"Actual multiplayer identity")
    checked(first["inventory"]==restart["inventory"] and len(first["inventory"])==3,"Player inventory persistence")
    ids=["cratedelight:apple_crate","stackedblocks:stacked_cobblestone_blocks"]
    for id in ids:
        steps=[s for s in first["actions"] if s["target"]==id]
        checked([s["action"] for s in steps]==["place","break","place"] and [s["observed"]["id"] for s in steps]==[id,"minecraft:air",id],"Client interaction: "+id)
    checked([s["id"] for s in restart["world"]]==ids and first["pickaxeDamage"]==[77,0] and first["randomItem"],"Forge commands and world")
    checked(first["consumption"]["before"]-first["consumption"]["after"]==1 and first["foodAfter"]>first["foodBefore"],"Food consumption")
    checked("registry mismatch" in negative["rejection"] and negative["omitted"] in report["originalSHA512"],"Mismatch rejection")
    shot=EVIDENCE/"content-world.png";checked(sha(shot)==first["screenshotSHA256"],"Content screenshot")
    evidence=[EVIDENCE/"content.json",shot]
    for name,run in report["runs"].items():
        checked((run["exit"],run["clientExit"])==(0,0),"Exit: "+name)
        for suffix in (".log","-client.log"):
            log=EVIDENCE/(name+suffix)
            checked(sha(log)==run["logs"][log.name],"Content log: "+log.name);evidence.append(log)
    return evidence


def dual_evidence(files):
    data=json.loads((EVIDENCE/"dual.json").read_bytes())
    checked((data["version"],data["minecraft"])==(VERSION,"26.2"),"Dual runtime")
    for key,f in zip(("loaderSHA256","worldMarkerSHA256","clientWitnessSHA256"),files):
        checked(data[key]==sha(f),"Stale dual binary "+f.name)
    specs=sum((json.loads((ROOT/"standalone"/n).read_bytes()) for n in ("forge-samples.json","forge-content.json","forge-containers.json")),[])
    checked(data["originalSHA512"]=={e["filename"]:e["sha512"] for e in specs},"Eight original JAR hashes")
    checked(data["authoredTestMods"]=={name:sha(ROOT/"standalone/tests/platform"/name) for name in ("unity_fabric_sample.jar","unity_neo_sample.jar")},"Cross-platform sample JARs")
    probes=ROOT/"standalone/tests/interop"
    checked(data["interopSamples"]=={n+".jar":sha(probes/(n+".jar")) for n in ("aa_bridge","aa_events","zz_neo_before","zz_plainlib")},"Cross-platform dependency and event probes")
    first,last=(data["runs"][key] for key in ("dual-mixed","dual-restart"))
    checked(first["digest"]==last["digest"] and [p["uuid"] for p in first["players"]]==[p["uuid"] for p in last["players"]],"Dual restart IDs")
    checked(first["shared"]["fullInventories"]==last["shared"]["fullInventories"],"Full inventories across restart")
    for run in (first,last):
        routes=run["distributors"]; ordered=sorted((int(k),value.split(" ",1)[1]) for k,value in routes.items())
        checked(len(ordered)==5 and [v for n,v in ordered]==["[UnityDrawerA]","[UnityDrawerA, UnityDrawerB]","[UnityDrawerA, UnityDrawerB]","[UnityDrawerA]","[UnityDrawerA, UnityDrawerB]"],"Player, all and tracking chunk recipients")
        checked(set(run["platformModels"])=={"unity_fabric_sample:token","unity_neo_sample:token"} and all(len(pair)==2 and all(r["renderLayers"] for r in pair) for pair in run["platformModels"].values()),"Two clients render both platform items")
        checked(run["custom"]["colorizerReply"]["nonce"]>=0,"Custom GUI server reply")
    checked(first["custom"]["storageReply"]["nonce"]>=0,"Storage custom GUI server reply")
    evidence=[EVIDENCE/"dual.json"]
    for phase,run in data["runs"].items():
        server=(EVIDENCE/(phase+".log")).read_text(encoding="utf8")
        stages=["UNITY_ABOUT_TO_START","UNITY_STARTING","UNITY_STARTED_ORDER","UNITY_STOPPING","UNITY_STOPPED"]
        checked(all(server.count(marker)==1 for marker in stages) and [server.index(m) for m in stages]==sorted(server.index(m) for m in stages),"Server events")
        checked(server.count("CHECK FABRIC SERVER ENTRY")==1 and "CHECK FABRIC CLIENT ENTRY" not in server,"Dedicated Fabric entrypoint selection")
        checked(server.count("UNITY_AUTO_SUBSCRIBER")==1 and "qa-render.json" not in server,"Subscriber registration and side filter")
        for n in ("UnityDrawerA","UnityDrawerB"):
            checked(server.count("UNITY_LOGIN "+n)==1 and server.count("UNITY_LOGOUT "+n)==1,"Player event pair")
        for suffix in ("-a-client.log","-b-client.log"):
            client=(EVIDENCE/(phase+suffix)).read_text(encoding="utf8",errors="replace")
            checked(client.count("CHECK FABRIC CLIENT ENTRY")==1 and "CHECK FABRIC SERVER ENTRY" not in client,"Client Fabric entrypoint selection")
            checked(client.count("UNITY_EARLY_SETUP")==1 and client.count("UNITY_RENDERER_TARGET")==1 and "qa-render.json" in client,"Early client setup and non-Minecraft target")
        for label in ("serverAudit","aAudit","bAudit"):
            checked(all(run[label]["fileOrigins"].get(n,0)>0 for n in data["interopSamples"]),"Shared-domain provenance")
        checked(run["exit"]==0 and len({p["pid"] for p in run["players"] if p["remote"] and p["gpu"]})==2,"Two graphical clients")
        checked(run["custom"]["slotId"]==17 and run["shared"]["drawer"]=={"cratedelight:carrot_crate":8,"moreberryuses:jam_cookie":4}
                and run["shared"]["bags"]=={"UnityDrawerA":{"moreberryuses:jam_cookie":5},"UnityDrawerB":{"cratedelight:carrot_crate":9}},"Container persistence")
        replies=run["shared"]["replies"]
        checked(len({x["nonce"] for x in replies})==2 and all((x["id"],x["count"])==("cratedelight:carrot_crate",8) for x in replies),"Bidirectional packet evidence")
        checked("FABRIC_TOKEN" in run["platformCommands"][0] and "NEO_TOKEN" in run["platformCommands"][1],"Cross-platform commands")
        for log in (phase+".log",phase+"-a-client.log",phase+"-b-client.log"):
            path=EVIDENCE/log;checked(sha(path)==run["logs"][log],"Changed dual log "+log);evidence.append(path)
    checked([s["serverCount"] for s in first["shared"]["steps"]]==[17,8,9,4] and first["custom"]["before"]!=first["custom"]["after"],"Four drawer operations and colorizer")
    for name in ("drawer-a.png","drawer-b.png"):
        path=EVIDENCE/name;checked(sha(path)==first["shared"]["screenshots"][name],"Drawer screenshot");evidence.append(path)
    return evidence


def ecology_evidence(files):
    report=json.loads((EVIDENCE/"ecology.json").read_bytes())
    checked((report["version"],report["minecraft"])==(VERSION,"26.2"),"Ecology release version")
    for key,path in zip(("loader","serverProbe","clientProbe"),files):
        checked(sha(path)==report[key],"Stale ecology binary "+path.name)
    fixture=ROOT/"standalone/tests/gameplay/unity_cancel_fixture.jar"
    checked(sha(fixture)==report["rulesSHA256"],"Rules probe changed")
    specs=sum((json.loads((ROOT/"standalone"/n).read_bytes()) for n in ("forge-content.json","forge-containers.json","forge-samples.json","ecosystem.json")),[])
    checked(len(specs)==15 and report["originals"]=={m["filename"]:m["sha512"] for m in specs},"Original ecosystem release set")
    r1,r2=[report["runs"][p] for p in ("ecology-mixed","ecology-restart")];first=r1["actions"];last=r2["actions"]
    checked([p["uuid"] for p in r1["players"]]==[p["uuid"] for p in r2["players"]],"Persistent player identities")
    proofs=[EVIDENCE/"ecology.json"]
    for phase,row in report["runs"].items():
        checked(len({p["pid"] for p in row["players"] if p["remote"] and p["gpu"]})==2,"Independent game windows")
        checked(all(row["serverAudit"]["fileOrigins"].get(m["filename"],0)>0 for m in specs),"Original server classes")
        for suffix in (".log","-a-client.log","-b-client.log"):
            log=EVIDENCE/(phase+suffix);checked(sha(log)==row["logs"][log.name],"Stale ecology log");proofs.append(log)
    checked(all(v==["HIGH","RULE","MONITOR true"] for v in r1["priority"].values()) and set(r1["priority"])=={"PLACE","MULTI","BREAK","USE","ITEM","ENTITY_USE","ENTITY_ATTACK"},"Cancellation order")
    for label,item,n,damage in (("PLACE","minecraft:barrel",3,0),("MULTI","minecraft:oak_door",2,0),("BREAK","minecraft:diamond_pickaxe",1,3)):
        checked(all((v["item"],v["count"],v["damage"])==(item,n,damage) for v in first[label]["held"]) and len(first[label]["held"])==2,"Predicted hand corrected: "+label)
    absorbed=first["absorptionStat"];checked(absorbed[0][0]==absorbed[1][0] and absorbed[2][0]>absorbed[1][0],"Final damage veto and non-cancelled zero side effects")
    checked(first["pvpOff"]==first["hurt"]==first["damage"] and first["none"]["server"]<first["pvpOff"]["server"],"Original PvP veto and uncancelled damage")
    checked(all(len(first["beforeCancelBags"][n]["client"])>0 and first["beforeCancelBags"][n]["client"]==first["beforeCancelBags"][n]["server"] for n in ("UnityGuardA","UnityGuardB")),"Both players have verified, nonempty inventory")
    checked(first["finalHealth"]==first["none"] and len(last["blocks"])>=7,"A veto immediately precedes persistence")
    checked(all("minecraft:air" in block for block in first["MULTI"]["upper"]) and all("powered=false" in block for block in first["USE"]) and all("minecraft:stone" in block for block in first["BREAK"]["world"]),"Authoritative block corrections")
    checked(first["beforeCancelBags"]==first["afterCancelBags"] and first["experienceAwarded"]==7,"Both player inventories and break XP")
    checked(first["bags"]==last["bags"] and first["finalHealth"]==last["health"],"Persisted bags and health")
    checked(first["shield"]["immediate"]=="minecraft:shield" and first["shield"]["ticks"]<5 and first["backAfterSKC"],"Real NeoForge/Fabric behaviour")
    for name in ("event-client-a.png","event-client-b.png"):
        shot=EVIDENCE/name;checked(sha(shot)==first["screenshots"][name],"Game window image");proofs.append(shot)
    return proofs


def new_content_evidence(files):
    report=json.loads((EVIDENCE/"new-content.json").read_bytes())
    checked((report["version"],report["minecraft"])==(VERSION,"26.2"),"Wrong content release")
    checked(report["executables"]=={p.name:sha(p) for p in files},"Content binaries changed")
    specs=sum((json.loads((ROOT/"standalone"/n).read_bytes()) for n in ("forge-content.json","forge-containers.json","forge-samples.json","ecosystem.json","creative-content.json")),[])
    checked(len(specs)==19 and report["originals"]=={sp["filename"]:sp["sha512"] for sp in specs},"Original mixed content samples")
    fixtures=ROOT/"standalone/tests/nested"
    checked(report["fixtures"]=={p.name:sha(p) for p in fixtures.glob("*.jar")} and report["nested"]==json.loads((fixtures/"nested-lock.json").read_bytes()),"Nested fixture selection")
    first,last=report["runs"]["new-content"],report["runs"]["new-restart"]
    checked(first["content"]["authorityBag"]==last["content"]["authorityBag"] and first["bagB"]==last["bagB"] and first["content"]["blocks"]==last["content"]["blocks"],"New content persistence")
    checked([p["uuid"] for p in first["players"]]==[p["uuid"] for p in last["players"]],"Reconnected identities")
    checked([(first["tx"][k]["block"],first["tx"][k]["hand"]) for k in ("pass","fail","commit")]==[("minecraft:barrel",3),("minecraft:barrel",3),("qa_neo_content:counter",2)],"Item-use journal")
    new=json.loads((ROOT/"standalone/creative-content.json").read_bytes())
    assets=[EVIDENCE/"new-content.json"]
    by_id={m["tested_block"]:m for m in new}
    for phase,run in report["runs"].items():
        checked(len({p["pid"] for p in run["players"] if p["remote"] and p["gpu"]})==2,"Two real content clients")
        checked(len(run["content"]["registrations"])==4 and len(set(run["content"]["neoPayloads"]))==2,"Content coverage and per-client network replies")
        for item,ref in run["content"]["registrations"].items():
            sample=by_id[item]
            checked(len(ref["clients"])==2 and all(v["texturesAndTabs"]["textureSHA256"]==sample["texture_sha256"] and v["texturesAndTabs"]["pack"]=="unity/"+sample["mod_id"] for v in ref["clients"]),"Original texture: "+item)
            checked(all(view["model"]["renderLayers"] and view["texturesAndTabs"]["tabs"] and view["model"]["itemNumber"]==ref["numbers"][0] and view["model"]["blockNumber"]==ref["numbers"][1] for view in ref["clients"]),item)
        for side in ("serverAudit","aAudit","bAudit"):
            origins=run[side]["fileOrigins"]
            checked(all(origins.get(m["filename"],0)>0 for m in new) and all(origins.get("nested:"+k,0)>0 for k in ("fabric-inner","selected-helper","middle")),"Runtime origins")
        server = (EVIDENCE / (phase + ".log")).read_text("utf8")
        checked("QA_ACCESS_FABRIC 42" in server and "QA_NESTED_NEO 14 HM" in server, "Access and nested priority outcome")
        if phase == "new-content":
            checked(all(server.count(x) == 1 for x in ("QA_COUNTER_PLACE 59, 200, 18", "QA_PLACEMENT_SINGLE 59, 200, 18"))
                    and all("[Server] NBT_" + mode in server for mode in ("pass", "fail")), "Repeated-write callbacks and restored barrel NBT")
        for suffix, player, nonce in zip(("-a-client.log", "-b-client.log"), run["players"], run["content"]["neoPayloads"]):
            who = player["player"]
            client_log = (EVIDENCE / (phase + suffix)).read_text("utf8", errors="replace")
            checked(f"QA_NEO_C2S {who} {nonce} Server thread" in server and f"QA_NEO_S2C {who} {nonce} Render thread" in client_log,
                    "Round trip on the originating player's connection")
        for suffix in (".log","-a-client.log","-b-client.log"):
            path=EVIDENCE/(phase+suffix);checked(sha(path)==run["logs"][path.name],"Stale log "+path.name);assets.append(path)
    for view in first["content"]["screens"].values():
        path=EVIDENCE/view["image"];checked(sha(path)==view["sha256"] and view["items"]>0,"Creative screen");assets.append(path)
    checked(len(first["content"]["screens"])==2,"Custom tab pages")
    shot=EVIDENCE/"new-content-world.png";checked(sha(shot)==first["worldImage"],"Content world screenshot");assets.append(shot)
    upstream = EVIDENCE / "upstream-nested.json"
    published = json.loads(upstream.read_bytes())
    names = {"ImmersiveOres-26.2-0.3.jar", "BlockRunner-v26.2.0-mc26.2.x-NeoForge.jar"}
    members = sorted(published["membersSHA256"].values())
    checked(published["loaderSHA256"] == sha(files[0]) and set(published["parentsSHA512"]) == names
            and len(set(members)) == 2 and published["resolved"] == {"client": members, "server": members},
            "Published Fabric nested and NeoForge JarJar originals")
    assets.extend((upstream, ROOT / "standalone/tests/PublishedNested.java"))
    return assets


def gameplay_evidence():
    path = EVIDENCE/"beta-mixed-gameplay.json"
    checked(path.exists(), "Beta requires Immersive Ores and Block Runner in the same two-client game and restart")
    report = json.loads(path.read_bytes())
    base = []
    for lock in ("creative-content","ecosystem","forge-containers","forge-content","forge-samples"):
        base.extend(json.loads((ROOT/"standalone"/(lock+".json")).read_bytes()))
    all_samples = base + beta_samples(ROOT)
    checked(report["version"] == VERSION and report["loader"] == sha(JAR), "Wrong Beta gameplay binary")
    checked(report["originals"] == {s["filename"]:s["sha512"] for s in all_samples}, "Beta original JAR set")
    first, last = report["runs"]
    checked(first["bags"] == last["bags"] and first["pinned"] == ["SEARCH","INVENTORY"], "Persistence and pinned tabs")
    proofs=[path]
    for phase, run in zip(("first","restart"),(first,last)):
        checked(len(set(x[1] for x in run["players"] if x[2])) == 2 and all(n in run["banner"] for n in ("blockrunner","immersiveores","puzzleslib","multiloaderdataextensions","playerabilitylib")),
                "Two real players and the requested author mods")
        for player in ("UnityMinerA","UnityMinerB"):
            for observer in ("client","server"):
                v=run["speed"][player];checked(1.22<=v["dirt_path"][observer]/v["stone"][observer]<=1.47,"Original Block Runner movement")
        logs=[EVIDENCE/("beta-mixed-"+phase+s) for s in (".log","-A-client.log","-B-client.log")]
        checked(run["logs"] == {log.name:sha(log) for log in logs},"Beta gameplay log changed")
        server=logs[0].read_text("utf8");checked(all("[Server] WORLD_"+str(x)+"_"+str(y) in server for x in range(39,42) for y in range(201,204))
                and "[Server] WORLD_43_201" in server,"Real area mining and placed block")
        proofs.extend(logs)
    shot=EVIDENCE/"beta-mixed-world.png";checked(sha(shot)==first["screenshot"],"Beta in-game screenshot")
    checked(first["windowVisible"] == last["windowVisible"] == [False,False],"Both real windows remained hidden")
    checked([p[0] for p in first["players"]]==[p[0] for p in last["players"]],"Same players reconnected")
    expected={"blockrunner:handshake":True,"puzzleslib:handshake":True,"qa_missing:handshake":False}
    for run in (first,last):
        checked(all(run["optionalChannels"][p]==expected for p in ("UnityMinerA","UnityMinerB")),"Negotiated per-player optional channels")
        checked(set(run["classAudits"])=={"server","A","B"},"Complete original-code provenance")
        inputs={r["id"]:r["filename"] for r in json.loads((ROOT/"standalone/beta-gameplay.json").read_bytes())["originals"]}
        server_origins=run["classAudits"]["server"]["fileOrigins"]
        checked(all(server_origins.get(spec["filename"],0)>0 for spec in base) and len(base)==19,"Original baseline actually loaded on the mixed server")
        for side,audit in run["classAudits"].items():
            checked(all(audit["fileOrigins"].get(filename,0)>0 for filename in inputs.values()),"Both publisher JARs and their prerequisite were loaded")
            expected_provider="mc.unity.adapter.puzzles."+("CommonPlatform" if side=="server" else "ClientPlatform")
            checked(audit["classes"].get(expected_provider)=="unity-loader" and
                    audit["classes"].get("fuzs.blockrunner.common.helper.BlockSpeedAttributeHelper")==inputs["VNgfrtBA"] and
                    audit["fileOrigins"].get("nested:multiloaderdataextensions",0)>0 and audit["fileOrigins"].get("nested:playerabilitylib",0)>0,
                    "Author gameplay classes, SDK provider and both nested libraries")
        for trials in run["speed"].values():
            for walk in trials.values():
                checked(walk["server"]>5 and abs(walk["server"]-walk["client"])<0.45 and abs(walk["server"]-(walk["serverEnd"][2]-walk["serverStart"][2]))<1e-6, "Independent distances")
    return proofs+[shot]

def world_evidence():
    local_path, wire_path = EVIDENCE/"integrated.json", EVIDENCE/"registry-wire.json"
    solo, wire = (json.loads(p.read_bytes()) for p in (local_path,wire_path))
    fixture_dir=ROOT/"standalone/tests/worlds/fixtures"
    fixtures={p.name:sha(p) for p in fixture_dir.glob("*.jar")}
    checked(len(fixtures)==3 and solo["fixtures"]==wire["fixtures"]==fixtures,"Published-API fixture identities")
    checked(sha(fixture_dir/"native/worldprobe-native.jar")==solo["nativeProbe"] and
            sha(DIST/"unity-client-witness.jar")==solo["clientProbe"],"Integrated native probe binaries")
    for r in (solo,wire):
        checked(r["version"]==VERSION and r["loader"]==sha(JAR),"Wrong world evidence binary")
    visits=solo["visits"]
    checked([(x["world"],x["action"]) for x in visits]==[("UnityQAA","create"),("UnityQAB","create"),("UnityQAA","open")],"Normal singleplayer entrypoints")
    checked(len({x["pid"] for x in visits})==len({x["player"] for x in visits})==1 and
            all(x["gpu"] and x["visible"] is False for x in visits),"One persistent graphical JVM")
    for x,score,component,count in zip(visits,(73,109,73),(17,29,17),(7,3,7)):
        a,b=x["extensions"]["server"],x["extensions"]["client"]
        checked(len(a["numericIds"])==4 and a["numericIds"]==b["numericIds"] and min(a["numericIds"])>=0,"Four static registry IDs")
        checked(b["worldValue"]==a["worldValue"]==score and b["baselineValue"]==a["baselineValue"]==41 and a["privateRule"]==9 and b.get("privateRule") is None and a["neoLabel"]==b["neoLabel"]=="sapphire","Per-world codecs and client scope")
        checked(a["recipeResult"]==31 and a["component"]==b["component"]==component and a["count"]==b["count"]==count and a["entities"]==b["entities"] and len(a["entities"])==1,"Recipe, component and entity")
        checked(a["tile"]==b["tile"]=="worldprobe:bell_stand" and b["draws"]>0 and b["lastDrawnProvider"]==b["providers"] and b["sounds"]>0,"Live renderer and sound callback")
        checked(b["glass_panel"]==b["originalGrate"]=="TRANSLUCENT" and b["grate"]=="CUTOUT" and b["glass_panelModel"]=="qa.worlds.Visuals$Tagged","Native and overridden material passes")
    checked(visits[0]["extensions"]["server"]==visits[2]["extensions"]["server"] and visits[1]["extensions"]["server"]["entities"]!=visits[2]["extensions"]["server"]["entities"],"Saved entity identity and world isolation")
    movement=visits[2]["soloMovement"]
    for side in ("server","client"):
        checked(1.22<=movement["dirt_path"][side]/movement["stone"][side]<=1.47,"Integrated author movement")
    reload=visits[2]["afterReload"]["client"]; prior=visits[2]["extensions"]["client"]
    checked(reload["sessions"]>prior["sessions"] and reload["modelSession"]==reload["sessions"] and reload["providers"]>prior["providers"] and reload["lastDrawnProvider"]==reload["providers"],"New rendered factories and baked model on resource reload")
    a,b=wire["server"],wire["client"]
    checked(wire["status"]["remote"] and wire["status"]["visible"] is False and b["worldValue"]==a["worldValue"]==127 and b.get("privateRule") is None and a["privateRule"]==9 and a["component"]==b["component"]==53 and a["recipeResult"]==31 and a["entities"]==b["entities"] and a["numericIds"]==b["numericIds"] and b["sounds"]>0,"Independent dedicated registry transfer")
    paths=[local_path,wire_path,EVIDENCE/"integrated-world.png",EVIDENCE/"integrated-client.log"]
    checked(sha(paths[2])==visits[-1]["screenshot"] and sha(paths[3])==solo["logSHA256"],"Bound integrated evidence")
    trace=paths[3].read_text("utf8")
    checked(trace.count("QA14 native-init")==1 and trace.count("[Unity] Integrated ready")==3 and trace.count("[Unity] Integrated stopped")==3 and trace.count("QA14 use client")==2 and trace.count("QA14 use server")==2,"Logical server and Fabric events")
    for dimension in ("minecraft:overworld","minecraft:the_nether","minecraft:the_end"):
        for event in ("level-load","level-unload"):
            for w,n in (("UnityQAA",2),("UnityQAB",1)):
                checked(trace.count(f"QA14 {event} {w} {dimension}")==n,"Dimension callbacks")
    for file in ("registry-wire.log","registry-wire-client.log"):
        path=EVIDENCE/file;checked(sha(path)==wire["logs"][file],"Wire log changed");paths.append(path)
    return paths

def inputs():
    beta = gameplay_evidence() + world_evidence() + beta20_evidence(ROOT,EVIDENCE,JAR) + beta21_evidence(ROOT,EVIDENCE,JAR) + beta22_evidence(ROOT,EVIDENCE,JAR) + beta23_evidence(ROOT,EVIDENCE,JAR)
    report = json.loads((EVIDENCE / "acceptance.json").read_bytes())
    checked((report["version"], report["minecraft"]) == (VERSION, "26.2"), "Wrong acceptance version")
    files = [JAR, DIST / "unity-world-marker.jar", DIST / "unity-client-witness.jar"]
    for key, file in zip(("loaderSHA256", "worldMarkerSHA256", "clientWitnessSHA256"), files):
        checked(sha(file) == report[key], f"Binary changed since acceptance: {file}")
    checked(set(report["runs"]) == {"bare", "native", "restart"}, "Missing server phases")
    for name, phase in report["runs"].items():
        checked(phase["exit"] == 0 and phase["classAudit"]["classes"]["mc.unity.bootstrap.Main"] == "unity-loader", name)
        if name != "bare":
            checked(any("[Server] UNITY_WORLD_OK" in msg for msg in phase["checks"]), name + " world read")
    client = report["client"]
    checked(client["exit"] == 0 and client["witness"]["block"] == "minecraft:amethyst_block"
            and client["witness"]["graphics"] == "OpenGL" and bool(client["witness"]["gpu"])
            and client["pickaxeDamage"] == [77, 0] and bool(client["randomItem"]), "Missing client evidence")
    specs = json.loads((ROOT / "standalone/forge-samples.json").read_bytes())
    checked(report["unmodifiedForge"] == {s["filename"]: s["sha512"] for s in specs}, "Forge samples changed")
    checked(sha(EVIDENCE / "standalone-world.png") == client["screenshotSHA256"], "Client screenshot changed")
    checked(set(report["logSHA256"]) == {"bare.log", "native.log", "restart.log", "graphical-client.log"}, "Missing logs")
    for name, expected in report["logSHA256"].items():
        checked(sha(EVIDENCE / name) == expected, "Acceptance log changed: " + name)
    lifecycle=json.loads((EVIDENCE/"lifecycle.json").read_bytes())
    checked(lifecycle["loaderSHA256"]==sha(JAR) and lifecycle["logSHA256"]==sha(EVIDENCE/"lifecycle.log") and lifecycle["exit"]==0,"Lifecycle regressions")
    bench=json.loads((EVIDENCE/"resource-reads.json").read_bytes())
    checked(bench["candidateSHA256"]==sha(JAR) and all(len(b)==14 and min(b)>0 for b in bench["batches"].values()),"Archive resource read measurements")
    extra=new_content_evidence(files)+ecology_evidence(files)+dual_evidence(files)+[EVIDENCE/"resource-reads.json",ROOT/"standalone/tests/archive_reads.py",ROOT/"standalone/tests/ArchiveReads.java"]
    files += content_evidence(files)+extra
    files += [EVIDENCE/"lifecycle.json",EVIDENCE/"lifecycle.log"]
    files += [ROOT / n for n in ("README.md", "LICENSE", "Start-Unity.cmd", "Install-Unity-Client.cmd", "tools/unity.py",
              "tools/release.py", "standalone/Build.java", "standalone/forge-samples.json",
              "standalone/tests/acceptance.py", "standalone/tests/content.py", "standalone/tests/dual.py","standalone/tests/platform_samples.py","standalone/bytecode-libraries.json","standalone/forge-containers.json", "standalone/forge-content.json", "standalone/tests/unpacked.py", "Install-Unity-Samples.cmd", "standalone/tests/RuntimeChecks.java", "standalone/tests/LifecycleProbe.java", "tests/check.py")]
    lock=json.loads((ROOT/"standalone/common-tags.json").read_bytes())
    tags=ROOT/"standalone/src/main/resources"
    checked({p.relative_to(tags).as_posix():sha(p) for p in (tags/"data/c/tags").rglob("*.json")}==lock["files"] and len(lock["files"])==513,"Public tag set")
    files+=[ROOT/"standalone/common-tags.json",ROOT/"standalone/tests/build_interop.py",ROOT/"tools/common_tags.py"]
    files+=[ROOT/"standalone/tests/BusPriorityChecks.java",ROOT/"standalone/tests/gameplay.py",ROOT/"standalone/ecosystem.json"]
    fabric_meta=json.loads((ROOT/"standalone/fabric-modules.json").read_bytes())
    checked(sha(ROOT/"standalone/src/main/resources"/fabric_meta["accessFile"])==fabric_meta["accessSHA256"],"Bundled upstream access baseline")
    files+=[ROOT/"standalone/fabric-modules.json",ROOT/"standalone/creative-content.json",ROOT/"standalone/tests/creative_content.py",ROOT/"standalone/tests/build_nested.py",ROOT/"standalone/tests/NestedChecks.java"]
    files+=sorted((ROOT/"standalone/tests/nested").glob("*"))
    files+=sorted((ROOT/"standalone/tests/gameplay").glob("*"))
    files+=sorted((ROOT/"standalone/tests/interop").glob("*"))
    library_lock=ROOT/"standalone/lib/SHA256SUMS"
    files.append(library_lock)
    for row in library_lock.read_text().splitlines():
        expected,filename=row.split("  ")
        library=library_lock.parent/filename
        checked(sha(library)==expected,"Changed bundled dependency "+filename)
        files.append(library)
    files+=sorted((ROOT/"standalone/tests/platform").glob("*"))
    files += [ROOT / "src/main/java" / name for name in SHARED]
    files += sorted(f for f in (ROOT / "standalone/src").rglob("*") if f.is_file())
    files += [EVIDENCE / name for name in ("acceptance.json", "standalone-world.png", *report["logSHA256"])]
    files+=sorted(p for p in (ROOT/"standalone/tests/beta").iterdir() if p.is_file())
    files += sorted(p for p in (ROOT/"standalone/adapters/puzzles").rglob("*") if p.is_file() and "__pycache__" not in p.parts)
    phases=json.loads((EVIDENCE/"puzzles-57-phases.json").read_bytes())
    checked(phases["loaderSHA256"]==sha(JAR) and phases["originalLogSHA256"]==sha(EVIDENCE/"puzzles-57-original.log") and len(phases["rows"])==57 and sorted(phases["counts"].values())==[4,26,27],"Original phase audit")
    checks=json.loads((EVIDENCE/"puzzles-adapter-checks.json").read_bytes())
    checked(checks["loaderSHA256"]==sha(JAR) and checks["testSHA256"]==sha(ROOT/"standalone/adapters/puzzles/VerifyAdapter.java") and checks["logSHA256"]==sha(EVIDENCE/"puzzles-adapter-checks.log") and checks["exit"]==0,"Adapter guard and optional network evidence")
    files += [EVIDENCE/"puzzles-adapter-checks.json",ROOT/"standalone/BETA-0.1.3.md", EVIDENCE/"puzzles-57-phases.md", EVIDENCE/"puzzles-57-phases.json", EVIDENCE/"puzzles-57-original.log", EVIDENCE/"puzzles-adapter-checks.log"]
    files += sorted(p for folder in ("standalone/adapters/client14","standalone/tests/worlds") for p in (ROOT/folder).rglob("*") if p.is_file() and "__pycache__" not in p.parts)
    files += [ROOT/"standalone/tests/integrated.py", ROOT/"standalone/tests/registry_network.py", ROOT/"standalone/tests/SceneCheck.java", ROOT/"standalone/tests/beta_status.py", ROOT/"standalone/BETA-0.1.4.md"]
    files += [ROOT / p for p in ("tools/beta20.py","tools/performance_samples.py","standalone/ecology-modules.json","standalone/tests/server_configs.py","standalone/tests/mod_diagnostics.py","standalone/tests/performance.py","standalone/tests/TickSamples.java","standalone/tests/CallbackCosts.java","standalone/tests/CorpusPreflight.java","standalone/BETA-0.2.0.md")]
    files += sorted(p for adapter in ["transfer","ecology"] for p in (ROOT/"standalone/adapters"/adapter).rglob("*") if p.is_file() and "__pycache__" not in p.parts)
    return sorted(files+beta+[ROOT/"standalone/beta-gameplay.json",ROOT/"standalone/tests/beta_gameplay.py",ROOT/"standalone/tests/BetaChecks.java"])


def distribution_bytes(path):
    data = path.read_bytes()
    if path in LAUNCHERS:
        if b"0.2.2-Beta" not in data and VERSION.encode() not in data:
            raise ValueError("Unexpected launch script version: " + str(path))
        return data.replace(b"0.2.2-Beta", VERSION.encode())
    return data


def replace_file(path,data):
    with tempfile.NamedTemporaryFile(dir=path.parent,delete=False) as f:
        f.write(data)
    try:
        os.replace(f.name,path)
    finally:
        Path(f.name).unlink(missing_ok=True)


def package():
    sources = inputs()
    with tempfile.TemporaryDirectory(dir=DIST) as temp:
        candidate = Path(temp) / ARCHIVE.name
        with zipfile.ZipFile(candidate, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for path in sources:
                name = path.relative_to(ROOT).as_posix()
                info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
                archive.writestr(info, distribution_bytes(path), compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(candidate) as archive:
            checked(archive.testzip() is None, "Damaged archive")
            for file in sources:
                checked(archive.read(file.relative_to(ROOT).as_posix()) == distribution_bytes(file), "Archive mismatch")
        os.replace(candidate, PENDING_ARCHIVE)
    print(PENDING_ARCHIVE, sha(PENDING_ARCHIVE))


@contextmanager
def state_lock(path):
    with path.open("a+b") as stream:
        if stream.tell() == 0:
            stream.write(b"0")
            stream.flush()
        stream.seek(0)
        if os.name == "nt":
            import msvcrt
            msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


def activate():
    with state_lock(ROOT / "versions/state.lock"):
        sources = inputs()
        package_file=PENDING_ARCHIVE if PENDING_ARCHIVE.is_file() else ARCHIVE
        unpacked = json.loads((EVIDENCE / "unpacked.json").read_bytes())
        checked(unpacked["archiveSHA256"] == sha(package_file) and unpacked["serverStarts"] == 2
                and unpacked["worldCount"] == 1 and unpacked["clientProfile"] == "mc.unity.bootstrap.ClientEntry",
                "Run the packaged entry points before promotion")
        content=sum((json.loads((ROOT/"standalone"/name).read_bytes()) for name in ("forge-content.json","forge-containers.json","ecosystem.json","creative-content.json")),[])
        commands=json.loads((ROOT/"standalone/forge-samples.json").read_bytes())
        content+=beta_samples(ROOT)+json.loads((ROOT/"standalone/ecology-modules.json").read_bytes())
        for side,specs in (("standalone-instance",content+commands),("standalone-client",content)):
            checked(unpacked["installedSamples"][side]=={m["filename"]:m["sha512"] for m in specs},"Packaged original samples: "+side)
        checked(unpacked["installedFabricApi"]==json.loads((ROOT/"standalone/beta-gameplay.json").read_bytes())["fabricApi"]["sha512"],"Offline official Fabric API install")
        checked(unpacked["clientApiRebuildSHA256"]==sha(ROOT/"standalone/lib/unity-client-api-0.2.3.jar"),"Client model and renderer API source rebuild")
        checked(unpacked["newAdapterRebuild"]=={name:sha(ROOT/"standalone/lib"/name) for name in ("unity-transfer-adapter-0.2.3.jar","unity-biome-adapter-0.2.3.jar")},"Ecology and transfer adapter source rebuild")
        fixtures=ROOT/"standalone/tests/worlds/fixtures"
        checked(unpacked["worldFixtureRebuild"]=={f.relative_to(fixtures).as_posix():sha(f) for f in fixtures.rglob("*.jar")},"Offline fixture rebuild")
        checked(unpacked["adapterRebuildSHA256"]==sha(ROOT/"standalone/lib/unity-puzzles-platform-26.2.5.jar"),"Adapter source rebuild")
        for phase in ("packaged-start","packaged-restart"):
            for item in ("oak_wood","cobblestone","red_dye","diamond_pickaxe"):
                checked(any("[Server] TAG_OK_"+item in s for s in unpacked["checks"][phase]),"Unverified common tag membership "+item)
        for file in (JAR, DIST/"unity-world-marker.jar", DIST/"unity-client-witness.jar"):
            checked(unpacked["byteIdenticalRebuild"][file.name] == sha(file), "Unverified source rebuild")
        for name in ("packaged-start.log", "packaged-restart.log"):
            checked(unpacked["logSHA256"][name] == sha(EVIDENCE/name), "Packaged entry point log changed")
        with zipfile.ZipFile(package_file) as archive:
            checked(set(archive.namelist()) == {p.relative_to(ROOT).as_posix() for p in sources}, "Archive inventory")
            for p in sources:
                checked(archive.read(p.relative_to(ROOT).as_posix()) == distribution_bytes(p), "Stale archive member")
        path = ROOT / "versions/state.json"
        state = json.loads(path.read_bytes()) if path.exists() else {"active": None, "pending": []}
        previous = state.get("active") or {}
        if previous and (previous.get("version") not in ("0.2.2-Beta",VERSION) or previous.get("mode")!="standalone"):
            raise ValueError("Unrecognized release: "+str(previous.get("version")))
        state.pop("compatibility",None)
        if package_file != ARCHIVE:
            os.replace(package_file, ARCHIVE)
        entries = [["versions", p.name, sha(p)] for p in [JAR, DIST/"unity-world-marker.jar", DIST/"unity-client-witness.jar", ARCHIVE]]
        state["active"] = {"version": VERSION, "mode": "standalone", "files": entries}
        with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as temp:
            temp.write(json.dumps(state, indent=2).encode())
        saved={p:p.read_bytes() for p in LAUNCHERS}
        try:
            for launcher in LAUNCHERS:
                data=distribution_bytes(launcher)
                if data!=saved[launcher]: replace_file(launcher,data)
            os.replace(temp.name, path)
        except BaseException:
            for launcher,data in saved.items():
                if launcher.read_bytes()!=data: replace_file(launcher,data)
            raise
        finally:
            Path(temp.name).unlink(missing_ok=True)
    print("Active standalone release", VERSION)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--activate", action="store_true")
    options = parser.parse_args()
    if options.activate:
        activate()
    else:
        package()
