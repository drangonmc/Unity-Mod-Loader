"""Create A, enter B, reopen A in a single real hidden client JVM using Minecraft's world flows."""
import argparse, json, shutil, subprocess, tempfile, time
from pathlib import Path
from acceptance import ROOT, VERSION, DIST, EVIDENCE, clean_env, digest
from content import Window
from dual import snapshot

def request(w,op,timeout=160,**params):
    response=w.io/"world-answer.json";response.unlink(missing_ok=True)
    tmp=w.io/"world-request.tmp";tmp.write_text(json.dumps({"op":op,**params}));tmp.replace(w.io/"world-request.json")
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        if response.exists():
            value=json.loads(response.read_bytes())
            if "error" in value:raise RuntimeError(value)
            return value
        if w.process.poll() is not None:raise RuntimeError(f"Client exited {w.process.returncode} during {op}")
        time.sleep(.12)
    raise TimeoutError(op)

def wait_for(w,test):
    end=time.monotonic()+145
    while time.monotonic()<end:
        result=request(w,"state")
        if test(result):return result
        time.sleep(.3)
    raise TimeoutError(result)

def solo_speed(w, log):
    import re
    trials={}
    def coordinate():
        offset=log.stat().st_size
        request(w,"command",value="data get entity UnitySolo Pos")
        for _ in range(60):
            with log.open("rb") as stream:
                stream.seek(offset)
                matches=re.findall(rb"UnitySolo has the following entity data: \[([^\]]+)\]", stream.read())
            if matches:return float(matches[0].split(b",")[2].strip().removesuffix(b"d"))
            time.sleep(.08)
        raise AssertionError("No authoritative position")
    for x,material in ((34,"stone"),(37,"dirt_path")):
        request(w,"command",value=f"fill {x-1} 200 30 {x+1} 200 53 minecraft:{material}")
        request(w,"command",value=f"fill {x-1} 201 30 {x+1} 204 53 minecraft:air")
        request(w,"command",value=f"tp UnitySolo {x+.5} 201 32.5 0 0")
        for _ in range(30):
            pos=w.act("status")
            if abs(pos["x"]-x-.5)<.05 and abs(pos["z"]-32.5)<.05:break
        else:raise AssertionError(pos)
        time.sleep(.35)
        start=coordinate();client=w.act("qaWalk")
        distance=coordinate()-start
        if distance<5 or abs(distance-client["distance"])>.45:raise AssertionError((material,distance,client))
        trials[material]={"server":distance,"client":client["distance"]}
    for side in ("server","client"):
        if not 1.22<=trials["dirt_path"][side]/trials["stone"][side]<=1.47:raise AssertionError(trials)
    return trials

def inspect(w,name,sessions=0,providers=0):
    end=time.monotonic()+40
    while time.monotonic()<end:
        result=request(w,"inspect",world=name)
        client,server=result["client"],result["server"]
        score=17 if name=="UnityQAA" else 29
        for view in (client,server):
            if view["neoLabel"]!="sapphire" or view["component"]!=score or view["worldValue"]!=(73 if name=="UnityQAA" else 109) or view["baselineValue"]!=41:raise AssertionError(result)
        if server["recipeResult"]!=31 or server["privateRule"]!=9 or client.get("privateRule") is not None:raise AssertionError(result)
        if (client["numericIds"]==server["numericIds"] and len(server["entities"])==1 and client["entities"]==server["entities"]
                and client.get("tile")==server.get("tile")=="worldprobe:bell_stand" and client["glass_panel"]=="TRANSLUCENT"
                and client["grate"]=="CUTOUT" and client["glass_panelModel"]=="qa.worlds.Visuals$Tagged" and client["soundWeight"]>0
                and client["originalGrate"]=="TRANSLUCENT" and client["modelSession"]==client["sessions"] and client["lastDrawnProvider"]==client["providers"] and client["sounds"]>0 and client["draws"]>0 and client["sessions"]>sessions and client["providers"]>providers):
            return result
        time.sleep(.2)
    raise AssertionError(result)

def run(o):
    home=o.client_home.resolve();samples=o.samples.resolve()
    ev=EVIDENCE;ev.mkdir(exist_ok=True,parents=True)
    jar=DIST/f"unity-loader-{VERSION}.jar"
    subprocess.run(["java","-jar",jar,"--install-client",home],check=True,env=clean_env())
    installed=home/"libraries/mc/unity/unity-loader"/VERSION/jar.name
    data={"version":VERSION,"minecraft":"26.2","loader":digest(jar),"clientProbe":digest(DIST/"unity-client-witness.jar"),"visits":[],"originals":{}}
    with tempfile.TemporaryDirectory(prefix="Integrated Unity ",dir=ROOT/".work") as tmp:
        game=Path(tmp);mods=game/"mods";mods.mkdir();native=game/"unity-mods";native.mkdir()
        shutil.copy2(DIST/"unity-client-witness.jar",native)
        for lock in ("creative-content","ecosystem","forge-containers","forge-content"):
            for row in json.loads((ROOT/"standalone"/(lock+".json")).read_bytes()):
                data["originals"][row["filename"]]=row["sha512"]
        beta=json.loads((ROOT/"standalone/beta-gameplay.json").read_bytes())
        for row in beta["originals"]+beta["fabricModules"]:data["originals"][row["filename"]]=row["sha512"]
        for name,h in data["originals"].items():
            if digest(Path(shutil.copy2(samples/name,mods)),"sha512")!=h:raise ValueError(name)
        if o.phase_fixtures:
            data["phaseFixtures"]={p.name:digest(Path(shutil.copy2(p,mods))) for p in sorted(o.phase_fixtures.glob("*.jar"))}
            for row in json.loads((ROOT/"standalone/ecology-modules.json").read_bytes()):
                if digest(Path(shutil.copy2(samples/row["filename"],mods)),"sha512")!=row["sha512"]:raise ValueError(row["filename"])
        if o.fixture:
            data["fixtures"]={}
            native_probe=o.fixture/"native/worldprobe-native.jar"
            shutil.copy2(native_probe,native);data["nativeProbe"]=digest(native_probe)
            for folder,score in (("UnityQAA",73),("UnityQAB",109)):
                pack=game/"saves"/folder/"datapacks/qa-settings"
                dst=pack/"data/worldprobe/worldprobe/tuning";dst.mkdir(parents=True)
                (pack/"pack.mcmeta").write_text(json.dumps({"pack":{"description":"Per-save tuning","min_format":107,"max_format":107}}))
                (dst/"world_only.json").write_text(str(score))
            for extra in sorted(o.fixture.glob("*.jar")): data["fixtures"][extra.name]=digest(extra)
            for extra in sorted(o.fixture.glob("*.jar")):shutil.copy2(extra,mods)
        (game/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:4\nsimulationDistance:4\nmaxFps:40\nfullscreen:false\ntutorialStep:none\n")
        w=Window(home,game,"integrated",None,installed,"UnitySolo")
        try:
            wait_for(w,lambda s:s["screen"].endswith("TitleScreen"))
            for name,op in (("UnityQAA","create"),("UnityQAB","create"),("UnityQAA","open")):
                request(w,op,world=name)
                state=wait_for(w,lambda s:s.get("world")==name and s["level"] and s["screen"]=="")
                if o.phase_fixtures and name=="UnityQAA" and op=="create":
                    config=game/"saves"/name/"serverconfig/qa_server_config-server.toml"
                    config.write_text("limit=47\n")
                    from server_configs import line
                    line(EVIDENCE/"integrated-client.log","QA_SERVER_CONFIG reload 47 Server thread")
                view=w.act("status")
                if view["visible"] or view["remote"] or not state["local"]:raise AssertionError((state,view))
                row={"world":name,"action":op,"player":state["uuid"],"pid":w.process.pid,"gpu":view["gpu"],"visible":view["visible"]};data["visits"].append(row)
                x,y,z=44,200,36
                if op=="create":
                    request(w,"command",value=f"forceload add {x} {z}")
                    if o.fixture:
                        request(w,"command",value="fill 38 200 29 53 200 42 minecraft:stone")
                        request(w,"command",value="fill 38 201 29 53 205 42 minecraft:air")
                    request(w,"command",value=f"tp UnitySolo {x+.5} {y+1} {z-3.5}")
                    time.sleep(1)
                    request(w,"command",value=f"setblock {x} {y} {z} "+("immersiveores:vibranium_block" if name=="UnityQAA" else "minecraft:lapis_block"))
                    component=("[worldprobe:score="+("17" if name=="UnityQAA" else "29")+"]") if o.fixture else ""
                    request(w,"command",value="item replace entity UnitySolo hotbar.0 with minecraft:emerald"+component+" "+("7" if name=="UnityQAA" else "3"))
                    if o.fixture:
                        for cmd in ("setblock 44 201 36 worldprobe:bell_stand","setblock 43 201 35 worldprobe:grate",
                                    "setblock 45 201 35 worldprobe:glass_panel","summon worldprobe:crystal 48.5 201 36.5",
                                    "playsound worldprobe:chime master UnitySolo 44 201 34","item replace entity UnitySolo hotbar.1 with minecraft:stick"):
                            request(w,"command",value=cmd)
                expected="immersiveores:vibranium_block" if name=="UnityQAA" else "minecraft:lapis_block"
                for _ in range(50):
                    block=w.act("qaBlock",x=x,y=y,z=z);stack=w.act("slot",slot=0)
                    if block["id"]==expected and stack["id"]=="minecraft:emerald" and stack["count"]==(7 if name=="UnityQAA" else 3):break
                    time.sleep(.1)
                else:raise AssertionError((block,stack))
                row["block"]=block["id"];row["count"]=stack["count"]
                if o.fixture:
                    w.act("look",x=44,y=201,z=36)
                    if op=="create":w.act("qaRight",slot=1,x=43,y=201,z=35)
                    row["extensions"]=inspect(w,name)
                    if op=="open":
                        # Capture the actual world before initiating the resource-reload overlay.
                        w.act("look",x=x,y=y+1,z=z)
                        time.sleep(1.5)
                        for attempt in range(5):
                            row["screenshot"]=snapshot(w,ev/"integrated-world.png")
                            frame=subprocess.run(["java",ROOT/"standalone/tests/SceneCheck.java",ev/"integrated-world.png"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
                            if frame.returncode==0:break
                            time.sleep(1)
                        else:raise AssertionError("Could not capture the rendered singleplayer world")
                        old=row["extensions"]["client"]
                        request(w,"reload")
                        row["afterReload"]=inspect(w,name,old["sessions"],old["providers"])
                        if row["extensions"]["server"]!=data["visits"][0]["extensions"]["server"]:raise AssertionError("Per-world state changed across reopen")
                        # The loading overlay fades out after the new renderers have already been published.
                        time.sleep(3)
                        row["soloMovement"]=solo_speed(w,ev/"integrated-client.log")
                if op=="open" and not o.fixture:
                    w.act("look",x=x,y=y,z=z);row["screenshot"]=snapshot(w,ev/"integrated-world.png")
                request(w,"leave");wait_for(w,lambda s:not s["level"] and not s["local"] and s["screen"].endswith("TitleScreen"))
                if not (game/"saves"/name/"level.dat").is_file():raise AssertionError("Missing level.dat")
                print("PASS",op,name,flush=True)
        finally:
            import sys
            primary=sys.exc_info()[1]
            if w.close()!=0:
                if primary is None:raise RuntimeError("Integrated client did not shut down cleanly")
                primary.add_note("Client exit also failed; see integrated-client.log")
    log=ev/"integrated-client.log";text=log.read_text("utf8",errors="replace")
    if text.count("[Unity] Integrated ready")!=3 or text.count("[Unity] Integrated stopped")!=3:raise AssertionError("Incorrect lifecycle count")
    if o.fixture:
        if text.count("QA14 native-init")!=1 or any(text.count(f"QA14 native-stop {i} {name} ticks=")!=1 for i,name in enumerate(("UnityQAA","UnityQAB","UnityQAA"),1)):
            raise AssertionError("One native extension must follow all three world lifetimes")
        for event in ("starting","started","stopping","stopped"):
            for which,count in (("UnityQAA",2),("UnityQAB",1)):
                if text.count("QA14 "+event+" "+which)!=count:raise AssertionError((event,which))
        for dim in ("minecraft:overworld","minecraft:the_nether","minecraft:the_end"):
            for action in ("level-load","level-unload"):
                for which,count in (("UnityQAA",2),("UnityQAB",1)):
                    if text.count(f"QA14 {action} {which} {dim}")!=count:raise AssertionError((action,which,dim))
        if text.count("QA14 use client")!=2 or text.count("QA14 use server")!=2:raise AssertionError("Fabric physical and logical callbacks")
    if o.phase_fixtures:
        import re
        trace=log.read_text("utf8",errors="replace")
        counts={kind:len(re.findall("QA21 "+kind+" ACK ",trace)) for kind in ("NEO","FABRIC")}
        if counts!={"NEO":3,"FABRIC":3} or trace.count("local=true player=UnitySolo")!=3 or trace.count("QA21 MEMORY WIRE ")!=6 or "Redundant SERVER config" in trace:raise AssertionError(("Integrated configuration sessions",counts))
        data["configuration"]=dict(sessions=3,wirePackets=6,serverConfigPackets=0,acks=counts)
    data["logSHA256"]=digest(log)
    (ev/"integrated.json").write_text(json.dumps(data,indent=2))
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for n in ("samples","client-home"):p.add_argument("--"+n,required=True,type=Path)
    p.add_argument("--phase-fixtures",type=Path)
    p.add_argument("--fixture",type=Path)
    run(p.parse_args())
