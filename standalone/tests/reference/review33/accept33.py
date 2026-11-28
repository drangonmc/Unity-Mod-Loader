"""Run the same original protection plugins and physical clients on Paper and Unity."""
import argparse
import contextlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import sys
import subprocess
import time
import traceback

HERE=Path(__file__).resolve().parent
sys.path[:0]=[str(HERE),str(HERE.parent),str(HERE.parent.parent)]
import beta25 as qa
import acceptance
import content
from gameplay import eventually
from dual import exited
import machines

def module(name,file):
    spec=importlib.util.spec_from_file_location(name,file);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def install(o,g,games):
    assert acceptance.digest(o.baseline)==json.loads((HERE/"baseline.json").read_bytes())["resultSHA256"]
    old=json.loads(o.baseline.read_bytes())
    plugins=dict(old["plugins"]);pl=g/"plugins"
    def copy(src,dest,sha):
        assert acceptance.digest(src,"sha512")==sha,src
        shutil.copy2(src,dest)
    for name,sha in plugins.items():copy(o.original_matrix/"server/plugins"/name,pl,sha)
    pins={row["slug"]:row for row in json.loads((HERE/"originals.json").read_bytes())}
    for k in ("residence","cmilib"):
        p=pins[k];copy(o.authors/p["filename"],pl,p["sha512"]);plugins[p["filename"]]=p["sha512"]
    libs=sorted((o.cache/"libraries").rglob("*.jar"))
    builder=module("fixture_builder33",HERE.parent/"review30/run.py");builder.HERE=HERE
    builder.fixture("RuleProbe.java",[*sorted((qa.ROOT/"standalone/vendor/bukkit").glob("*.jar")),*pl.glob("*.jar"),*libs],"plugin.yml",
        'name: Unity33Rules\nmain: qa.unity33.RuleProbe\napi-version: "26.2"\nversion: 1\ndepend: [WorldGuard, Residence]\ncommands:\n  u33rules: {}\n',pl/"Unity33Rules.jar")
    matrix={}
    if o.host=="unity":
        shutil.copy2(o.dist/"UnityEditRegions.jar",pl)
        for side in [g,*games]:
            mods=side/"mods";mods.mkdir(exist_ok=True);src=o.original_matrix/side.name/"mods"
            files=matrix[side.name]=dict(old["matrix"][side.name])
            for name,sha in files.items():copy(src/name,mods,sha)
            api,=src.glob("fabric-api-[0-9]*.jar")
            if not (mods/api.name).exists():shutil.copy2(api,mods)
            q=pins["quite-quarry"];copy(o.authors/q["filename"],mods,q["sha512"]);files[q["filename"]]=q["sha512"]
        probe=g/"mods/unity33routes.jar"
        drone,=(g/"mods").glob("logisticsdrones*.jar")
        builder.fixture("Routes.java",[*(o.sdk/n for n in ("neo.jar","neo-bus.jar","neo-loader.jar","dist.jar")),drone,o.authors/pins["quite-quarry"]["filename"],o.cache/"versions/26.2/server-26.2.jar",*libs],"META-INF/neoforge.mods.toml",
            'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="unity33routes"\nversion="1"\ndisplayName="Drone controller fixture"\n',probe)
        for side in games:shutil.copy2(probe,side/"mods")
    return plugins,matrix

def run(o):
    d=o.out;d.mkdir(parents=True);inst=d/"instance";inst.mkdir()
    qa.acceptance.EVIDENCE=content.EVIDENCE=d
    o.plain=True;o.regions=False
    g,games,port,_,_=qa.setup_game(o,inst,True,qa.observer(inst,o.cache))
    plugins,matrix=install(o,g,games)
    paper=o.host=="paper";engine=o.dist/"unity-loader-0.3.3-Beta.jar";bundle=o.cache/"mojang-server-26.2.jar"
    if paper:
        assert acceptance.digest(o.paper)=="0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
        (g/"cache").mkdir();shutil.copy2(bundle,g/"cache/mojang_26.2.jar")
    os.environ["PAPER_DEFAULT_CENTRAL_REPOSITORY"]="https://repo.maven.apache.org/maven2/"
    cmd=["java","-Xmx2g","-XX:ActiveProcessorCount=4","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Dterminal.jline=false","-Dterminal.ansi=false","-Dmc.unity.machineQA=true","-Dmc.unity.contentQA=true","-jar",str(o.paper if paper else engine)]
    cmd+=["--nogui"] if paper else ["--server-jar",str(bundle),"--runtime-dir",str(o.cache),"nogui"]
    results=[];prefix="minecraft:" if paper else ""
    for phase in ["first","restart"]:
        first=phase=="first";s=acceptance.Server(g,phase,engine,bundle,o.cache,cmd);record={"phase":phase,"clients":[]}
        try:
            s.expect(r"Done \([0-9.,]+s\)!" if paper else "Standalone ready",150)
            if first:
                s.send("forceload add 48 0 84 47");content.observed(s,"loaded 65 80 18","U33_READY",50)
                for c in ("fill 54 79 12 80 79 44 stone","fill 54 80 12 80 85 44 air","gamerule minecraft:spawn_mobs false"):
                    s.send(prefix+c)
            with contextlib.ExitStack() as clean:
                ws=[]
                for n,client in zip(qa.NAMES,games):
                    w=content.Window(o.cache/"client-home",client,phase+n,port,engine,n)
                    clean.callback(exited,w);ws.append(w)
                    status=w.act("status",timeout=180)
                    assert status["remote"] and status["gpu"] and not status["visible"],status
                    record["clients"].append({"name":n,"pid":w.process.pid,"visible":False})
                def tp(w,n,x,y,z):
                    s.send(prefix+f"tp {n} {x:.3f} {y:.3f} {z:.3f} 0 0","Teleported",15)
                    eventually(lambda:w.act("qaPlayer"),lambda v:abs(v["x"]-x)<.1 and abs(v["y"]-y)<.1 and abs(v["z"]-z)<.1)
                for w,n,x in zip(ws,qa.NAMES,[64.5,71.5]):tp(w,n,x,80,16.5)
                audit=s.send("u33rules audit","U33 AUDIT lore=ok rule=",12)
                record["worldId"]=re.search(r"uuid=([0-9a-f-]{36})",audit)[1]
                if not paper:assert "legacy=false nonpositive=false remaining=2" in audit,audit
                s.send("u33rules "+("setup" if first else "check"),"U33 RULES END",25)
                lines=Path(s.log.name).read_text("utf8",errors="replace")
                rules={(n,int(x)):[v=="true" for v in flags.split()] for n,x,flags in re.findall(r"U33 RULE (Volume[AB]) (\d+) ((?:(?:true|false) ){3}(?:true|false))",lines)}
                assert len(rules)==12,rules
                for (name,x),values in rules.items():
                    want_wg= x not in ({66} if name=="VolumeA" else {68,70})
                    want_res= x not in ({67,68} if name=="VolumeA" else set())
                    assert values==[want_wg,want_res,want_wg,want_res],(name,x,values)
                record["rules"]={f"{n}-{x}":v for (n,x),v in rules.items()}
                if first and not o.machine_only:
                    for w,n in zip(ws,qa.NAMES):
                        s.send(prefix+f"gamemode creative {n}")
                        s.send(prefix+f"clear {n}")
                        for x in (65,66,67,68,69,70):
                            tp(w,n,x+.5,80,12.7);s.send(prefix+f"setblock {x} 80 15 stone")
                            machines.check_block(s,ws,x,80,15,"minecraft:stone")
                            w.act("qaMine",slot=0,x=x,y=80,z=15)
                            allow=all(rules[n,x][:2])
                            machines.check_block(s,ws,x,80,15,"minecraft:"+("air" if allow else "stone"))
                        s.send(prefix+f"gamemode survival {n}")
                        tp(w,n,60.5 if n=="VolumeA" else 74.5,80,12.5)
                for w,n in ([] if o.machine_only else zip(ws,qa.NAMES)):
                    for x in (65,66,67,68,70):
                        z=15
                        if first:
                            s.send(prefix+f"setblock {x} 80 {z} chest")
                            s.send(prefix+f"item replace block {x} 80 {z} container.0 with amethyst_shard 9")
                        tp(w,n,x+.5,80,z-2.3)
                        allow=all(rules[n,x][2:])
                        if allow:
                            w.act("menuOpen",handSlot=0,x=x,y=80,z=z)
                            cells=w.act("menuInfo")["slots"]
                            assert cells[0]["id"]=="minecraft:amethyst_shard" and cells[0]["count"]==9,(n,x,cells[0])
                            w.act("menuClose")
                        else:
                            w.act("qaRight",slot=0,x=x,y=80,z=z)
                            s.expect(f"U33 CHEST {n} {x} DENY",10)
                            assert w.act("qaMenuClosed")["closed"]
                        content.observed(s,f'data block {x} 80 {z} {{Items:[{{Slot:0b,id:"minecraft:amethyst_shard",count:9}}]}}',"U33_RULE_CHEST")
                    tp(w,n,60.5 if n=="VolumeA" else 74.5,80,12.5)
                if first:
                    for w,n,material,quantity in [(ws[0],"VolumeA","gold_ingot",9),(ws[1],"VolumeB","iron_ingot",7)]:
                        s.send(prefix+f"item replace entity {n} hotbar.4 with {material} {quantity}","Replaced")
                        qa.held(s,w,n,4,material,quantity)
                if first and not o.machine_only:
                    if not paper:s.send("gamerule minecraft:disable_nether false",r"false")
                    # The original Portal Lock mod checks for eight glowstone before the portal callback.
                    s.send(prefix+"item replace entity VolumeA hotbar.5 with glowstone 8","Replaced")
                    s.send(prefix+"gamemode creative VolumeA")
                    qa.held(s,ws[0],"VolumeA",5,"glowstone",8)
                    for c in ("fill 55 79 21 58 83 21 obsidian","fill 56 80 21 57 82 21 air","setblock 56 80 21 fire"):
                        s.send(prefix+c)
                    content.observed(s,"block 56 80 21 nether_portal","U33_PORTAL_READY")
                    tp(ws[0],"VolumeA",56.5,80,21.5)
                    s.expect("U33 PORTAL cancelled NETHER_PORTAL",45)
                    content.observed(s,"entity @a[name=VolumeA,x=56,y=80,z=21,distance=..2]","U33_PORTAL_STAY")
                    for w in ws:eventually(lambda:w.act("qaPlayer",name="VolumeA"),lambda v:abs(v["x"]-56.5)<.12)
                    s.send(prefix+"setblock 56 80 21 air")
                    tp(ws[0],"VolumeA",60.5,80,16.5)
                    s.send(prefix+"gamemode survival VolumeA")
                    s.send(prefix+"item replace entity VolumeA hotbar.5 with air")
                if not paper:
                    record["machines"]=machines.run(s,ws,tp,first)
                    machines.attachments(s,ws,tp,first)
                for w,n,x in zip(ws,qa.NAMES,[64.5,71.5]):tp(w,n,x,80,16.5)
                record["bags"]=qa.bags(s,ws)
                for name,item in (("VolumeA","minecraft:gold_ingot*9"),("VolumeB","minecraft:iron_ingot*7")):
                    assert record["bags"][name]["4"].split("@")[0]==item,record["bags"]
                if not paper and not first:
                    s.send("kick VolumeA 0.3.3 offline-owner test",r"(Kicked VolumeA|VolumeA left the game)",15)
                    eventually(lambda:s.send("execute unless entity @a[name=VolumeA] run say U33_OWNER_OFFLINE") or
                               s.send("execute if entity @a[name=VolumeA] run say U33_OWNER_PRESENT",r"\[Server\] U33_OWNER_(OFFLINE|PRESENT)"),
                               lambda line:"OFFLINE" in line,12)
                    content.observed(s,"entity VolumeB","U33_OWNER_OBSERVER")
                    start=Path(s.log.name).stat().st_size
                    s.send("u33drone VolumeA route 66 69","U33 ROUTE")
                    def offline_policy():
                        with Path(s.log.name).open("rb") as file:file.seek(start);return file.read().decode("utf8","replace")
                    eventually(offline_policy,lambda text:any("U33 POLICY EXTRACT 66, 80, 39" in row and "WorldGuard=false" in row for row in text.splitlines()),35)
                    machines.chest(s,66,"diamond",9);machines.chest(s,69,"emerald",12);machines.cargo(s,"VolumeA",0)
                    s.send("u33drone VolumeA off","U33 DRONE OFF")
                    record["offlineOwnerDenied"]=True
            s.send("save-all flush","Saved the game",100)
        except BaseException:
            (d/(phase+"-failure.txt")).write_text(traceback.format_exc(),"utf8");raise
        finally:
            start=time.monotonic();s.close();elapsed=time.monotonic()-start
            assert s.process.returncode==0 and elapsed<180,(s.process.returncode,elapsed)
            record["stopSeconds"]=round(elapsed,3)
        results.append(record)
        (d/(phase+"-result.json")).write_text(json.dumps(record,indent=2),"utf8")
    assert results[0]["worldId"]==results[1]["worldId"]
    assert results[0]["rules"]==results[1]["rules"] and results[0]["bags"]==results[1]["bags"]
    for name,h in plugins.items():assert acceptance.digest(g/"plugins"/name,"sha512")==h
    for side,files in matrix.items():
        for name,h in files.items():assert acceptance.digest(inst/side/"mods"/name,"sha512")==h
    cp=os.pathsep.join(map(str,[o.cache/"versions/26.2/server-26.2.jar",*sorted((o.cache/"libraries").rglob("*.jar"))]))
    cls=d/"nbt-reader";cls.mkdir()
    subprocess.run(["javac","--release","25","-proc:none","-cp",cp,"-d",cls,HERE/"SavedMachines.java",HERE.parent/"review30/SavedWorld.java",HERE.parent/"review31/SavedPlayers.java"],check=True)
    for reader,name in (("SavedMachines","world"),("SavedPlayers","players")):
        subprocess.run(["java","-cp",str(cls)+os.pathsep+cp,reader,g/"world",d/(name+".json"),o.host+("-machines" if o.machine_only else "")],check=True)
    disk=json.loads((d/"players.json").read_bytes())
    for n in qa.NAMES:assert disk[n]["bag"]=={k:v.split("@")[0] for k,v in results[-1]["bags"][n].items()}
    (d/"result.json").write_text(json.dumps(dict(host=o.host,machineOnly=o.machine_only,engine=acceptance.digest(engine),paper=acceptance.digest(o.paper),clientWitness=acceptance.digest(o.dist/"unity-client-witness.jar"),plugins=plugins,matrix=matrix,runs=results,logs={p.name:acceptance.digest(p) for p in sorted(d.glob("*.log"))}),indent=2),"utf8")

if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--machine-only",action="store_true");p.add_argument("--host",choices=["paper","unity"],required=True)
    for key in ("out","baseline","cache","dist","original_matrix","eula","paper","sdk","authors"):p.add_argument("--"+key,type=lambda s:Path(s).resolve(),required=True)
    run(p.parse_args())
