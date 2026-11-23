"""Run the real author mods with the existing 19 in disposable 26.2 game instances."""
import argparse, contextlib, json, shutil, socket, subprocess, tempfile, time
from pathlib import Path
from acceptance import ROOT, DIST, VERSION, EVIDENCE, SERVER_HASH, Server, clean_env, digest, class_audit, client_libraries
from content import Window, slot, observed
from gameplay import teleport, eventually
from dual import exited, snapshot

PLAYERS = ("UnityMinerA", "UnityMinerB")

def path_speeds(server, windows, fresh):
    import re
    result = {}
    for i, (player, window) in enumerate(zip(PLAYERS, windows)):
        trials = {}
        for offset, material in enumerate(("stone", "dirt_path")):
            x = 35 + i * 5 + offset * 2
            if fresh:
                server.send(f"fill {x-1} 200 32 {x+1} 200 57 minecraft:{material}")
                server.send(f"fill {x-1} 201 32 {x+1} 204 57 minecraft:air")
            observed(server, f"block {x} 200 35 minecraft:{material}", f"TRACK_{x}")
            teleport(server, window, player, x+.5, 201, 35.5)
            initial=server.send("data get entity "+player+" Pos", r"following entity data: \[")
            start=[float(n) for n in re.findall(r"(-?[0-9]+(?:\.[0-9]+)?)d",initial)]
            response = window.act("qaWalk")
            line = server.send("data get entity "+player+" Pos", r"following entity data: \[")
            pos = [float(n) for n in re.findall(r"(-?[0-9]+(?:\.[0-9]+)?)d", line)]
            travelled = pos[2]-start[2]
            if travelled < 5 or abs(response["distance"]-travelled) > .45:
                raise AssertionError((player,material,response,line))
            trials[material] = {"client":response["distance"],"server":travelled,"serverStart":start,"serverEnd":pos,
                                "clientStartZ":response["startZ"],"clientEndZ":response["endZ"],"forwardTicks":38,"settleTicks":12}
        for observer in ("server","client"):
            ratio=trials["dirt_path"][observer]/trials["stone"][observer]
            if not 1.22 <= ratio <= 1.47: raise AssertionError((player,observer,trials))
        result[player]=trials
    return result

def run(o):
    o.extra = o.extra or o.samples
    o.modules = o.modules or o.samples
    cache = ROOT / "cache" / VERSION
    home = o.client_home.resolve()
    bundle = o.bundle.resolve()
    eula = o.eula_from.read_text()
    import re
    if digest(bundle) != SERVER_HASH or not re.search(r"(?im)^eula\s*=\s*true\s*$", eula):
        raise ValueError("Provide the official bundle and accepted local test EULA")
    jar = DIST / ("unity-loader-" + VERSION + ".jar")
    subprocess.run(["java", "-jar", jar, "--install-client", home], check=True, env=clean_env())
    profile = home / "libraries/mc/unity/unity-loader" / VERSION / jar.name
    spec = json.loads((ROOT / "standalone/beta-gameplay.json").read_bytes())
    report = {"version": VERSION, "loader": digest(jar), "runs": [], "originals": {}}
    commands = {x["filename"] for x in json.loads((ROOT/"standalone/forge-samples.json").read_bytes())}
    originals = []
    for lock in ("creative-content", "ecosystem", "forge-containers", "forge-content", "forge-samples"):
        for row in json.loads((ROOT/"standalone"/(lock+".json")).read_bytes()):
            originals.append((o.samples/row["filename"], row["sha512"]))
    for row in spec["originals"]:
        if o.ore_only and row["id"] != "fjZyYXGJ": continue
        originals.append((o.extra/row["filename"], row["sha512"]))
    modules = [(o.modules/m["filename"], m["sha512"]) for m in spec["fabricModules"]]
    for p, h in originals + modules:
        if digest(p,"sha512") != h: raise ValueError("Changed author archive " + str(p))
        report["originals"][p.name]=h
    label = ("ore-foundation" if o.foundation else "ore") if o.ore_only else "beta-mixed"
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=ROOT/".work", prefix="Beta gameplay ") as temp:
        base=Path(temp)
        srv=base/"server"
        games=[base/"a",base/"b"]
        for game in [srv,*games]:
            (game/"mods").mkdir(parents=True)
            (game/"unity-mods").mkdir()
            for p,expected in originals+modules:
                if game==srv or p.name not in commands:
                    installed = Path(shutil.copy2(p,game/"mods"))
                    if digest(installed,"sha512") != expected: raise ValueError(installed)
            if o.foundation:
                probes=[o.foundation/"qa_registry_data.jar"]
                if o.ore_only: probes.append(o.foundation/"multiloaderdataextensions-neoforge-26.2.0.jar")
                for fixture in probes: shutil.copy2(fixture,game/"mods")
                report["additionalProbeSHA256"]={p.name:digest(p) for p in probes}
                (game/"config").mkdir(exist_ok=True)
                (game/"config/qa_registry_data.toml").write_text("limit = 23\n")
            probe = "unity-world-marker.jar" if game==srv else "unity-client-witness.jar"
            shutil.copy2(DIST/probe,game/"unity-mods")
            if game!=srv:
                (game/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:4\nmaxFps:35\nfullscreen:false\ntutorialStep:none\n")
        with socket.socket() as sock:
            sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
        (srv/"eula.txt").write_text(eula)
        (srv/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=3\nlevel-seed=261302\npause-when-empty-seconds=-1\n")
        if o.foundation:
            with (srv/"server.properties").open("a") as props:
                props.write("initial-disabled-packs=file/scores\n")
            override=srv/"world/datapacks/scores"
            folder=override/"data/qa_registry_data/data_maps/block"
            folder.mkdir(parents=True)
            (override/"pack.mcmeta").write_text(json.dumps({"pack":{"description":"Server-side scores","min_format":107,"max_format":107}}))
            (folder/"scores.json").write_text('{"values":{"minecraft:stone":83}}')
        for phase in ("first","restart"):
            name=label+"-"+phase
            s=Server(srv,name,jar,bundle,cache)
            row={"players":[],"bags":{}}
            try:
                s.expect("Standalone ready",150)
                row["banner"]=s.send("unity",r"standalone \| MC 26\.2")
                with contextlib.ExitStack() as closers:
                    windows=[]
                    for player,directory in zip(PLAYERS,games):
                        w=Window(home,directory,name+"-"+player[-1],port,profile,player,False)
                        windows.append(w);closers.callback(exited,w)
                        state=w.act("status",timeout=130)
                        assert state["visible"] is False and state["remote"] and state["gpu"] and state["player"]==player
                        row["players"].append([state["uuid"],w.process.pid,state["gpu"]]);row.setdefault("windowVisible",[]).append(state["visible"])
                    if not o.ore_only:
                        row["optionalChannels"]={}
                        for player,w in zip(PLAYERS,windows):
                            actual=w.act("qaChannels")
                            checked={}
                            for channel,expected in (("blockrunner:handshake",True),("puzzleslib:handshake",True),("qa_missing:handshake",False)):
                                if actual[channel]!=expected:raise AssertionError((player,actual))
                                s.send(f"execute as {player} run unityqa_channel {channel}",f"QA_CHANNEL {player} {channel} {str(expected).lower()}")
                                checked[channel]=actual[channel]
                            row["optionalChannels"][player]=checked
                    if o.foundation:
                        if phase=="first":
                            row["beforeReload"]=s.send("unitydataprobe","QA_DATA_MAP 41 CONFIG 23")
                            s.send('datapack enable "file/scores" last',r"Enabling data pack")
                        row["serverData"]=eventually(lambda:s.send("unitydataprobe","QA_DATA_MAP "),lambda v:v.endswith("83 CONFIG 23"))

                        row["clientData"]=[eventually(lambda:w.act("qaRegistryData"),lambda v:v["value"]==83)["value"] for w in windows]
                        if row["clientData"] != [83,83]: raise AssertionError(row)
                    a,b=windows
                    s.send("forceload add 32 16 48 20")
                    observed(s,"loaded 42 201 18","ORE_LOADED")
                    if phase=="first":
                        s.send("fill 32 200 14 48 200 24 minecraft:stone")
                        s.send("fill 32 201 14 48 205 24 minecraft:air")
                        s.send("fill 39 201 18 41 203 18 minecraft:stone")
                    for w,player,x in zip(windows,PLAYERS,[40.5,43.5]):
                        teleport(s,w,player,x,201,15.7)
                    if phase=="first":
                        s.send("gamemode creative UnityMinerA")
                        time.sleep(.7)
                        row["pinned"] = a.act("qaTabScreen",tab="bedrockcrafter:bedrock_tab",checkPinned=True)["pinned"]
                        a.act("qaCloseTab")
                        s.send("gamemode survival UnityMinerA")
                        if row["pinned"] != ["SEARCH","INVENTORY"]: raise AssertionError(row["pinned"])
                        s.send("item replace entity UnityMinerA hotbar.0 with immersiveores:vibranium_hammer")
                        s.send("item replace entity UnityMinerB hotbar.0 with immersiveores:vibranium_block 3")
                        slot(a,0,"immersiveores:vibranium_hammer",1)
                        slot(b,0,"immersiveores:vibranium_block",3)
                        b.act("qaRight",x=43,y=200,z=18,slot=0,top=True)
                        a.act("qaMine",x=40,y=202,z=18,slot=0)
                    for x,y,z,block in [(43,201,18,"immersiveores:vibranium_block")]+[(x,y,18,"minecraft:air") for x in range(39,42) for y in range(201,204)]:
                        observed(s,f"block {x} {y} {z} {block}",f"WORLD_{x}_{y}")
                        for w in windows:
                            eventually(lambda:w.act("qaBlock",x=x,y=y,z=z),lambda result:result["id"]==block)
                    for w,player,item,count in zip(windows,PLAYERS,("immersiveores:vibranium_hammer","immersiveores:vibranium_block"),(1,2)):
                        slot(w,0,item,count)
                        row["bags"][player]=s.send("data get entity "+player+" Inventory",r"following entity data: \[").split("following entity data: ",1)[1]
                    if not o.ore_only:
                        s.send("forceload add 32 32 48 57")
                        observed(s,"loaded 39 201 55","TRACK_READY")
                        row["speed"] = path_speeds(s, windows, phase=="first")
                    if phase=="first":
                        teleport(s,a,PLAYERS[0],42.5,201,14.5)
                        a.act("look",x=42,y=201,z=18);row["screenshot"]=snapshot(a,EVIDENCE/(label+"-world.png"))
                s.send("save-all flush","Saved the game",150)
            finally:
                try:
                    s.close()
                except RuntimeError:
                    server_log=EVIDENCE/(name+".log")
                    if not o.ore_only and phase=="first" and server_log.is_file() and s.process.returncode:
                        text=server_log.read_text("utf8")
                        if "Unsupported platform types or members before initialization" in text and "Standalone ready" not in text:
                            failed={"version":VERSION,"loader":digest(jar),"originals":report["originals"],
                                    "exit":s.process.returncode,"stage":"platform ABI preflight",
                                    "log":server_log.name,"logSHA256":digest(server_log)}
                            path=EVIDENCE/"beta-mixed-failure.json"
                            pending=path.with_suffix(".tmp")
                            pending.write_text(json.dumps(failed,indent=2));pending.replace(path)
                    raise
            logs = [EVIDENCE/(name+suffix) for suffix in (".log","-A-client.log","-B-client.log")]
            row["logs"]={p.name:digest(p) for p in logs}
            if not o.ore_only:
                by_id={m["id"]:m["filename"] for m in spec["originals"]}
                nested={"27c180f803b28ada61eb56c691e7ce562de90a57710d948c60362d035f091da3":"playerabilitylib",
                        "ffbadbdc7b3c55462edb935f2493bcabf06f690a5c1f09b3b5a216171349350d":"multiloaderdataextensions"}
                row["classAudits"]={}
                for physical,game in zip(("server","A","B"),(srv,*games)):
                    client = physical != "server"
                    allowed=client_libraries(home) if client else {p.resolve():"Mojang library" for p in (cache/"libraries").rglob("*.jar")}
                    allowed[(profile if client else jar).resolve()]="unity-loader"
                    allowed[(home/"versions/26.2/26.2.jar" if client else cache/"versions/26.2/server-26.2.jar").resolve()]="Mojang game"
                    for folder in ("mods","unity-mods"):
                        for p in (game/folder).glob("*.jar"): allowed[p.resolve()]=p.name
                    required={"fuzs.blockrunner.common.BlockRunner":by_id["VNgfrtBA"],"fuzs.puzzleslib.neoforge.impl.PuzzlesLibNeoForge":by_id["XkWV0WSp"],
                        "com.blocklegend001.immersiveores.ImmersiveOres":by_id["fjZyYXGJ"],"fuzs.blockrunner.common.helper.BlockSpeedAttributeHelper":by_id["VNgfrtBA"],
                        "io.github.ladysnake.pal.Pal":"nested:playerabilitylib","fuzs.multiloaderdataextensions.neoforge.impl.services.NeoForgeAbstractions":"nested:multiloaderdataextensions",
                        "mc.unity.adapter.puzzles."+("ClientPlatform" if client else "CommonPlatform"):"unity-loader"}
                    row["classAudits"][physical]=class_audit(game/(name+("-"+physical if client else "")+"-classes.log"),allowed,required,nested,("fuzs.puzzleslib.","fuzs.blockrunner."))
            report["runs"].append(row)
        first,last=report["runs"]
        assert first["bags"]==last["bags"] and [x[0] for x in first["players"]]==[x[0] for x in last["players"]]
        (EVIDENCE/(label+"-gameplay.json")).write_text(json.dumps(report,indent=2))
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for name in ("client-home","bundle","samples","eula-from"):
        p.add_argument("--"+name,required=True,type=Path)
    for name in ("extra","modules"): p.add_argument("--"+name,type=Path)
    p.add_argument("--foundation",type=Path)
    p.add_argument("--ore-only",action="store_true")
    run(p.parse_args())
