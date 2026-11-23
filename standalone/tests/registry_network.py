"""Verify datapack values and static IDs from an independent server and real client."""
import argparse,contextlib,json,re,shutil,socket,subprocess,tempfile,time
from pathlib import Path
from acceptance import ROOT,VERSION,DIST,EVIDENCE,Server,SERVER_HASH,clean_env,digest
from content import Window,observed
from gameplay import teleport,eventually
from dual import exited

def run(o):
    cache=ROOT/"cache"/VERSION;jar=DIST/f"unity-loader-{VERSION}.jar"
    home=o.client_home.resolve();bundle=o.bundle.resolve()
    eula=o.eula_from.read_text();assert digest(bundle)==SERVER_HASH and re.search(r"(?im)^eula\s*=\s*true\s*$",eula)
    subprocess.run(["java","-jar",jar,"--install-client",home],check=True,env=clean_env())
    profile=home/"libraries/mc/unity/unity-loader"/VERSION/jar.name
    report={"version":VERSION,"loader":digest(jar),"fixtures":{}}
    with tempfile.TemporaryDirectory(prefix="Registry network ",dir=ROOT/".work") as tmp:
        base=Path(tmp);srv=base/"server";client=base/"client"
        for g in [srv,client]:
            (g/"mods").mkdir(parents=True)
            for p in sorted(o.fixture.glob("*.jar")):
                installed=Path(shutil.copy2(p,g/"mods"));report["fixtures"][p.name]=digest(installed)
            (g/"unity-mods").mkdir()
            shutil.copy2(DIST/("unity-world-marker.jar" if g==srv else "unity-client-witness.jar"),g/"unity-mods")
        with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
        (srv/"eula.txt").write_text(eula)
        (srv/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\npause-when-empty-seconds=-1\nlevel-seed=261405\n")
        (client/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:4\nmaxFps:35\nfullscreen:false\ntutorialStep:none\n")
        pack=srv/"world/datapacks/wire"
        folder=pack/"data/worldprobe/worldprobe/tuning";folder.mkdir(parents=True)
        (pack/"pack.mcmeta").write_text('{"pack":{"description":"Server-owned registry value","min_format":107,"max_format":107}}')
        (folder/"world_only.json").write_text("127")
        s=Server(srv,"registry-wire",jar,bundle,cache)
        try:
            s.expect("Standalone ready",130)
            w=Window(home,client,"registry-wire",port,profile,"UnityRegistry")
            try:
                status=w.act("status",timeout=130)
                if not status["remote"] or status["visible"] or not status["gpu"]:raise AssertionError(status)
                s.send("forceload add 38 29 53 42");observed(s,"loaded 44 201 36","WIRE_READY")
                s.send("fill 38 200 29 53 200 42 minecraft:stone");s.send("fill 38 201 29 53 205 42 minecraft:air")
                teleport(s,w,"UnityRegistry",44.5,201,32.5)
                for cmd in ("setblock 44 201 36 worldprobe:bell_stand","setblock 43 201 35 worldprobe:grate","setblock 45 201 35 worldprobe:glass_panel",
                            "summon worldprobe:crystal 48.5 201 36.5","item replace entity UnityRegistry hotbar.0 with minecraft:emerald[worldprobe:score=53] 4","playsound worldprobe:chime master UnityRegistry 44 201 34"):
                    s.send(cmd)
                w.act("look",x=44,y=201,z=36)
                authority=json.loads(s.send("execute as UnityRegistry run worldprobe_inspect",r"QA14 NETWORK \{").split("QA14 NETWORK ",1)[1])
                view=eventually(lambda:w.act("qaWorldExtensions")["client"],lambda x:x.get("component")==53 and x.get("draws",0)>0 and x.get("sounds",0)>0 and x.get("entities")==authority["entities"],timeout=35)
                assert view["worldValue"]==authority["worldValue"]==127 and view["baselineValue"]==41
                assert view.get("privateRule") is None and authority["privateRule"]==9 and authority["recipeResult"]==31
                assert len(authority["entities"])==1 and view["numericIds"]==authority["numericIds"] and view["component"]==authority["component"]
                assert view["glass_panel"]=="TRANSLUCENT" and view["grate"]=="CUTOUT" and view["soundWeight"]>0
                report.update(client=view,server=authority,status=status)
            finally:exited(w)
        finally:s.close()
    logs=[EVIDENCE/"registry-wire.log",EVIDENCE/"registry-wire-client.log"]
    report["logs"]={p.name:digest(p) for p in logs}
    (EVIDENCE/"registry-wire.json").write_text(json.dumps(report,indent=2))
    print("PASS independent registry configuration sync, custom recipe, entity, component, sound and live renderer")
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for n in ("client-home","bundle","eula-from","fixture"):p.add_argument("--"+n,type=Path,required=True)
    run(p.parse_args())
