"""Run the same SDK plugin, scripted client and world setup on Paper and Unity in fresh worlds."""
import argparse
import contextlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import zipfile

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import acceptance
import content
from acceptance import ROOT, VERSION, DIST, Server, digest
from content import Window, observed, slot
from gameplay import eventually, teleport
from dual import exited

PAPER = "0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
USER = "UnityReference"

def plugin(out):
    sdk = ROOT/"standalone/vendor/bukkit"
    for row in (sdk/"SHA256SUMS").read_text().splitlines():
        h,n = row.split("  ",1)
        assert digest(sdk/n)==h,n
    classes=out/"classes"
    classes.mkdir()
    subprocess.run(["javac","-proc:none","--release","25","-g:none","-cp",os.pathsep.join(map(str,[*sdk.glob("*.jar"),*sorted((ROOT/"cache"/VERSION/"libraries").rglob("guava-*.jar"))])),
                    "-d",classes,HERE/"BehaviourProbe.java"],check=True)
    target=out/"BehaviourProbe.jar"
    with zipfile.ZipFile(target,"w") as jar:
        for f in sorted(classes.rglob("*.class")):
            jar.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix()),f.read_bytes())
        jar.writestr(zipfile.ZipInfo("plugin.yml"),'name: BehaviourProbe\nmain: qa.reference.BehaviourProbe\napi-version: "26.2"\nversion: 1\ncommands:\n  observeunity: {}\n')
    return target

def observations(file):
    rows={}
    if file.exists():
        for row in file.read_text("utf8").splitlines():
            key,*values=row.split("\t")
            rows.setdefault(key,[]).append(values)
    return rows

def wait_key(file,key):
    return eventually(lambda:observations(file),lambda rows:key in rows)

def session(which,o,work,fixture):
    root=work/which
    server=root/"server"; game=root/"client"
    (server/"plugins").mkdir(parents=True)
    (game/"unity-mods").mkdir(parents=True)
    shutil.copy2(fixture,server/"plugins")
    shutil.copy2(DIST/"unity-client-witness.jar",game/"unity-mods")
    (game/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:3\nmaxFps:30\nfullscreen:false\ntutorialStep:none\n")
    shutil.copy2(o.eula,server/"eula.txt")
    with socket.socket() as s:
        s.bind(("127.0.0.1",0)); port=s.getsockname()[1]
    (server/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\npause-when-empty-seconds=-1\nlevel-seed=2424\nlevel-type=minecraft:flat\ngenerate-structures=false\n")
    cache=ROOT/"cache"/VERSION; bundle=cache/"mojang-server-26.2.jar"
    if o.hurt_window:
        with zipfile.ZipFile(cache/"versions/26.2/server-26.2.jar") as z:
            formats=json.loads(z.read("version.json"))["pack_version"]
            version=[formats["data_major"],formats["data_minor"]]
        pack=server/"world/datapacks/reference-window"
        function=pack/"data/reference/function/window.mcfunction"
        function.parent.mkdir(parents=True)
        (pack/"pack.mcmeta").write_text(json.dumps({"pack":{"description":"Reference timing","min_format":version,"max_format":version}}))
        function.write_text(f"damage {USER} 3 minecraft:generic\ndamage {USER} 1 minecraft:generic\nsay REFERENCE_DAMAGE_END\n")
        if o.strong_window:
            function.with_name("strong.mcfunction").write_text(f"damage {USER} 2 minecraft:generic\ndamage {USER} 5 minecraft:generic\nsay REFERENCE_STRONG_END\n")
    cmd=None
    if which=="paper":
        (server/"cache").mkdir()
        shutil.copy2(bundle,server/"cache/mojang_26.2.jar")
        cmd=["java","-Xms384m","-Xmx2g","-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8",
             "-Dstderr.encoding=UTF-8","-Dterminal.jline=false","-Dterminal.ansi=false",
             "-jar",str(o.paper),"--nogui"]
    file=server/"plugins/BehaviourProbe/observed.tsv"
    with contextlib.ExitStack() as stack:
        srv=Server(server,which,o.unity,bundle,cache,command=cmd)
        stack.callback(srv.close)
        srv.expect(r'Done \([0-9.,]+s\)!' if cmd else 'Standalone ready',200)
        srv.send("forceload add 0 0 15 15")
        observed(srv,"loaded 8 80 8","REFERENCE_READY",40)
        srv.send("fill 4 79 4 12 79 12 minecraft:stone")
        srv.send("fill 4 80 4 12 85 12 minecraft:air")
        srv.send("setblock 8 80 8 minecraft:chest")
        srv.send("item replace block 8 80 8 container.0 with minecraft:apple 5")
        client=Window(cache/"client-home",game,which,port,DIST/f"unity-loader-{VERSION}.jar",USER)
        stack.callback(exited,client)
        status=client.act("status",timeout=180)
        assert status["gpu"] and status["remote"] and not status["visible"]
        teleport(srv,client,USER,8.5,80,5.7)
        for i,item in enumerate(["stone 8","iron_ingot 7","oak_planks 5"]):
            srv.send(f"item replace entity {USER} hotbar.{i} with minecraft:{item}")
        slot(client,0,"minecraft:stone",8)
        client.act("qaCommand",cmd="observeunity stack"); wait_key(file,"slot")
        opened=client.act("menuOpen",handSlot=3,x=8,y=80,z=8)
        assert opened["slots"][0]["count"]==5
        client.act("menuClick",index=0,button=0); wait_key(file,"click.monitor")
        restored=eventually(lambda:client.act("menuInfo"),lambda v:v["slots"][0]["count"]==5 and v["carried"]["count"]==0)
        client.act("menuClose")
        client.act("qaSneak",down=True)
        time.sleep(.35)
        client.act("qaRight",slot=2,x=8,y=80,z=8)
        wait_key(file,"interact")
        client.act("qaCommand",cmd="observeunity world");wait_key(file,"world")
        client.act("qaSneak",down=False)
        # Keep both reference hosts past the vanilla join grace period before the first hit.
        srv.send("gamerule minecraft:natural_health_regeneration false")
        def game_tick():
            return int(re.search(r"time is (\d+)",srv.send("time query gametime",r"time is \d+"))[1])
        joined_tick=game_tick()
        eventually(game_tick,lambda now:now>=joined_tick+65,30)
        srv.send(f"damage {USER} 3 minecraft:generic")
        wait_key(file,"damage")
        health=eventually(lambda:client.act("qaPlayer"),lambda v:v["health"]==17)["health"]
        data=observations(file)
        if o.hurt_window:
            srv.send("gamerule minecraft:natural_health_regeneration false")
            def tick():
                return int(re.search(r"time is (\d+)",srv.send("time query gametime",r"time is \d+"))[1])
            first=tick()
            eventually(tick,lambda now:now>=first+22)
            before=len(data["damage"])
            srv.send("function reference:window",r"REFERENCE_DAMAGE_END")
            final=eventually(lambda:client.act("qaPlayer"),lambda p:p["health"]==14)
            data["window.callbacks"]=observations(file)["damage"][before:]
            data["window.health"]=[[final["health"]]]
            if o.strong_window:
                first=tick()
                eventually(tick,lambda now:now>=first+22)
                before=len(observations(file)["damage"])
                srv.send("function reference:strong",r"REFERENCE_STRONG_END")
                final=eventually(lambda:client.act("qaPlayer"),lambda p:p["health"]==9)
                data["stronger.callbacks"]=observations(file)["damage"][before:]
                data["stronger.health"]=[[final["health"]]]
        data["click.client"]=[[restored["slots"][0]["count"],restored["carried"]["count"]]]
        data["health.client"]=[[health]]
    return data

def main(o):
    assert digest(o.paper)==PAPER
    if not re.search(r"(?im)^eula\s*=\s*true\s*$",o.eula.read_text()):raise ValueError("Accepted local test instance required")
    report=o.out.resolve();report.mkdir(parents=True,exist_ok=True)
    acceptance.EVIDENCE=content.EVIDENCE=report
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="reference24-") as tmp:
        work=Path(tmp);test=plugin(work)
        meta={"paper":digest(o.paper),"unity":digest(o.unity),"fixture":digest(test),"clientLoader":digest(DIST/f"unity-loader-{VERSION}.jar"),
              "clientWitness":digest(DIST/"unity-client-witness.jar"),"sdk":digest(next((ROOT/"standalone/vendor/bukkit").glob("paper-api*.jar")))}
        sides={}
        for name in ["paper","unity"]:
            sides[name]=session(name,o,work,test)
            (report/(name+"-observations.json")).write_text(json.dumps(sides[name],indent=2),"utf8")
        required={"join","hand","slot","interact","world","damage","click.lowest","click.monitor","click.client","health.client"}
        assert all(required<=v.keys() for v in sides.values())
        assert sides["paper"]["hand"]==[["8","8","4"]] and sides["paper"]["slot"]==[["3"]]
        assert sides["paper"]["world"]==[["1","4"]]
        differences={k:{n:side.get(k,[]) for n,side in sides.items()} for k in sorted(set.union(*(set(v) for v in sides.values()))) if sides["paper"].get(k)!=sides["unity"].get(k)}
        data={"mc":"26.2","binarySHA256":meta,"observations":sides,"differences":differences,"logs":{f.name:digest(f) for f in report.glob("*.log")}}
        (report/"comparison.json").write_text(json.dumps(data,indent=2),"utf8")
        print("Compared",len(set.union(*(set(v) for v in sides.values()))),"observations; differences:",list(differences))
        if o.baseline:
            old=json.loads(o.baseline.read_bytes())
            for key in ("paper","sdk","fixture","clientLoader","clientWitness"):
                if old["binarySHA256"][key]!=meta[key]:raise ValueError("Changed baseline input: "+key)
            if old["observations"]["paper"]!=sides["paper"]:raise ValueError("Official reference observations changed")
            for key,value in differences.items():
                if old["differences"].get(key)!=value:raise ValueError("New or changed deviation: "+key)
        if o.require_equal and differences:raise SystemExit(1)

if __name__=="__main__":
    ap=argparse.ArgumentParser()
    for k in ("paper","unity","eula","out"):ap.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    ap.add_argument("--strong-window",action="store_true",help="With --hurt-window, also compare a stronger second hit within cooldown")
    ap.add_argument("--baseline",type=Path,help="Compare deviations with an existing run of the identical reference fixture")
    ap.add_argument("--hurt-window",action="store_true",help="Compare two ordinary hits in one vanilla command function")
    ap.add_argument("--require-equal",action="store_true")
    main(ap.parse_args())
