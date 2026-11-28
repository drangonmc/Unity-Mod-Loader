"""Exercise the accepted mixed-game baseline and two-sided NeoForge event outcomes on the same save."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile

HERE=Path(__file__).resolve().parent
sys.path[:0]=[str(HERE.parent/"review29"),str(HERE.parent),str(HERE.parent.parent)]
import full
from content import observed
from gameplay import eventually,teleport
from acceptance import digest
import beta25 as base

def fixture(source, libs, descriptor, value, dest):
    with tempfile.TemporaryDirectory(dir=dest.parent) as tmp:
        out=Path(tmp)
        subprocess.run(["javac","--release","25","-proc:none","-g:none","-cp",os.pathsep.join(map(str,libs)),
                        "-d",out,*([HERE/source] if isinstance(source,str) else [HERE/f for f in source])],check=True)
        with zipfile.ZipFile(dest,"w") as jar:
            jar.writestr(zipfile.ZipInfo(descriptor),value)
            for f in sorted(out.rglob("*.class")):jar.writestr(zipfile.ZipInfo(f.relative_to(out).as_posix()),f.read_bytes())

def fixtures(o,g,clients,corpus):
    libs=list((o.cache/"libraries").rglob("*.jar"))
    sdk=[o.sdk/n for n in ["neo.jar","neo-bus.jar","neo-loader.jar","dist.jar"]]
    neo=g/"mods/unity30witness.jar"
    fixture("NeoProbe.java",[*sdk,o.cache/"versions/26.2/server-26.2.jar",*libs],"META-INF/neoforge.mods.toml",
            'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="unity30witness"\nversion="1"\ndisplayName="Unity event witness"\n',neo)
    for c in clients:shutil.copy2(neo,c/"mods")
    fixture("PluginProbe.java",[*sorted((base.ROOT/"standalone/vendor/bukkit").glob("*.jar")),*libs],"plugin.yml",
            'name: Unity30Observer\nmain: qa.unity30.PluginProbe\napi-version: "26.2"\nversion: 1\ncommands:\n  unity30restore: {}\n',g/"plugins/Unity30Observer.jar")
    for row in json.loads((HERE/"originals.json").read_bytes()):
        f=o.event_authors/row["filename"]
        assert digest(f,"sha512")==row["sha512"],f
        for d in [g,*clients]:
            shutil.copy2(f,d/"mods")
            corpus[d.name][f.name]=row["sha512"]

def nearby(ws,x,check):
    return [eventually(lambda:w.act("qaNearby",x=x+.5,y=80.5,z=26.5),check,12) for w in ws]

def record(s,ws):
    a,b=ws
    for w,n,x in zip(ws,base.NAMES,[48.5,50.5]):teleport(s,w,n,x,80,17.5)
    blocks={}
    for x,kind in [(46,"stone"),(47,"air"),(49,"air"),(50,"bricks"),(52,"lever")]:
        blocks[str(x)]=base.block(s,ws,x,80,20,kind,"powered=false" if kind=="lever" else "")
    for y in (81,82,83):base.block(s,ws,54,y,20,"air")
    kept=base.health(s,ws,"VolumeB",15)
    observed(s,'entity @e[tag=u30save,nbt={Health:4.0f}]',"U30_SAVED_COW")
    cow=nearby(ws,46,lambda v:{"kind":"minecraft:cow","health":4} in v["living"])
    empty=nearby(ws,50,lambda v:v["items"]=={})
    emerald=nearby(ws,55,lambda v:v["items"]=={"minecraft:emerald":5})
    furnace=nearby(ws,59,lambda v:v["items"].get("minecraft:blast_furnace")==1)
    return {"savedCow":[next(m["health"] for m in v["living"] if m["kind"]=="minecraft:cow") for v in cow],
            "emptyDrops":[v["items"] for v in empty],"emeraldDrops":[v["items"] for v in emerald],
            "armorerFurnaces":[v["items"]["minecraft:blast_furnace"] for v in furnace],"blocks":blocks,"healthB":kept,"healthA":base.health(s,ws,"VolumeA",17),"bags":base.bags(s,ws)}

def verify(s,ws,first):
    if not first:return record(s,ws)
    a,b=ws
    s.send("forceload add 32 16 63 31")
    observed(s,"loaded 52 80 20","U30_AREA",60)
    for cmd in ["fill 44 79 15 63 79 29 stone","fill 44 80 15 63 85 29 air","setblock 46 80 20 stone","setblock 47 80 20 stone",
                "setblock 52 80 20 lever[face=floor]","fill 54 80 21 54 83 21 stone","fill 54 81 20 54 83 20 vine[south=true]",
                "item replace entity VolumeA hotbar.1 with bricks 6","item replace entity VolumeA hotbar.2 with diamond_pickaxe",
                "item replace entity VolumeA hotbar.3 with shears","gamemode survival VolumeA","gamemode survival VolumeB"]:
        s.send(cmd)
    def aim(x,y=80):
        teleport(s,a,"VolumeA",x+.5,80,17.65)
    teleport(s,b,"VolumeB",49.5,80,16.5)
    audit=Path(s.log.name)
    def rows():return re.findall(r"UNITY30 ([^\r\n]+)",audit.read_text("utf8",errors="replace"))
    def trace(start,expected,absent=()):
        entries=eventually(lambda:rows()[start:],lambda vals:all(v in vals for v in expected),12)
        indexes=[entries.index(v) for v in expected]
        assert indexes==sorted(indexes) and not any(v in entries for v in absent),(entries,expected,absent)
    for x,deny in [(46,True),(47,False)]:
        aim(x);i=len(rows());a.act("qaMine",slot=2,x=x,y=80,z=20)
        flag=str(deny).lower()
        case=["break-high-"+str(x)+" false"]
        if not deny:case.append("break-normal-47 false")
        case+=["break-low-"+str(x)+" "+flag,"bukkit-break-"+str(x)+" "+flag]
        trace(i,case,["break-normal-46 false","break-normal-46 true"] if deny else ())
        base.block(s,ws,x,80,20,"stone" if deny else "air")
    for x,deny in [(49,True),(50,False)]:
        aim(x);i=len(rows());a.act("qaRight",slot=1,x=x,y=79,z=20,top=True)
        flag=str(deny).lower()
        trace(i,["place-old-"+str(x)+" true","place-low-"+str(x)+" "+flag,"bukkit-place-"+str(x)+" "+flag])
        base.block(s,ws,x,80,20,"air" if deny else "bricks")
        base.held(s,a,"VolumeA",1,"bricks",6 if deny else 5)
    aim(52);i=len(rows());a.act("qaRight",slot=0,x=52,y=80,z=20)
    trace(i,["interact-deny true"]);base.block(s,ws,52,80,20,"lever","powered=false")
    aim(54)
    for y in (81,82,83):base.block(s,ws,54,y,20,"vine")
    a.act("qaMine",slot=3,x=54,y=83,z=20)
    for y in (81,82,83):base.block(s,ws,54,y,20,"air")
    # An otherwise self-supported column below the cut is removed by the author's break handler.
    teleport(s,a,"VolumeA",50.5,80,18.4);teleport(s,b,"VolumeB",50.5,80,20.4)
    s.send("tag VolumeB add u30attack","Added tag")
    i=len(rows());a.act("qaAttack",slot=0,name="VolumeB");trace(i,["attack-deny true"])
    base.health(s,ws,"VolumeB",15)
    s.send("tag VolumeB remove u30attack");s.send("tag VolumeB add u30health","Added tag")
    i=len(rows());s.send("damage VolumeB 1 minecraft:sweet_berry_bush")
    trace(i,["incoming-sweetBerryBush true"]);base.health(s,ws,"VolumeB",15)
    i=len(rows());s.send("damage VolumeB 7 minecraft:generic")
    trace(i,["incoming-generic false","bukkit-damage-deny true"]);base.health(s,ws,"VolumeB",15)
    time.sleep(1.2)
    i=len(rows());s.send("damage VolumeB 4 minecraft:generic")
    trace(i,["incoming-generic false","pre 4.0","post 2.0"])
    base.health(s,ws,"VolumeB",13)
    s.send("tag VolumeB remove u30health")
    s.send("unity30restore","UNITY30 restored-and-pdc")
    for tag,x in [("u30save",46),("u30nodrops",50),("u30loot",55)]:
        area=f"@e[type=item,x={x},y=80,z=26,distance=..2"
        s.send("execute as "+area+"] run data get entity @s Item")
        s.send("execute unless entity "+area+"] run say U30_CLEAR_"+tag,r"\[Server\] U30_CLEAR_"+tag,8)
        s.send(f'summon cow {x} 80 26 {{Tags:["{tag}"],NoAI:1b}}',"Summoned")
        i=len(rows());s.send(f"damage @e[tag={tag},limit=1] 100 minecraft:generic")
        if tag=="u30save":
            trace(i,["death-deny true"]);observed(s,'entity @e[tag=u30save,nbt={Health:4.0f}]',"U30_ALIVE")
        else:
            key="drops-cancel" if tag=="u30nodrops" else "drops-replace"
            eventually(lambda:rows()[i:],lambda vals:any(re.fullmatch(key+r" [1-9]\d*",v) for v in vals),15)
            s.send("execute as "+area+"] run data get entity @s Item")
            if tag=="u30nodrops":s.send("execute unless entity "+area+"] run say U30_NO_DROPS",r"\[Server\] U30_NO_DROPS")
            else:
                observed(s,"entity "+area+',nbt={Item:{id:"minecraft:emerald",count:5}}]',"U30_DROP_COUNT")
                s.send("execute unless entity "+area+',nbt=!{Item:{id:"minecraft:emerald"}}] run say U30_ONLY_EMERALDS',r"\[Server\] U30_ONLY_EMERALDS")
    s.send('summon villager 59 80 26 {Tags:["u30villager"],NoAI:1b,VillagerData:{profession:"minecraft:armorer",type:"minecraft:plains",level:2}}',"Summoned")
    s.send("damage @e[tag=u30villager,limit=1] 100 minecraft:generic")
    observed(s,'entity @e[type=item,x=59,y=80,z=26,distance=..3,nbt={Item:{id:"minecraft:blast_furnace"}}]',"U30_AUTHOR_FURNACE")
    return record(s,ws)

def main(o):
    o.version=getattr(o,"version","0.3.0-Beta")
    o.install_events=lambda g,clients,corpus:fixtures(o,g,clients,corpus)
    o.verify_events=verify
    full.main(o)
    cp=[o.cache/"versions/26.2/server-26.2.jar",*sorted((o.cache/"libraries").rglob("*.jar"))]
    subprocess.run(["java","-cp",os.pathsep.join(map(str,cp)),HERE/"SavedWorld.java",
                    o.out/"instance/server/world",o.out/"offline.json"],check=True)

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--version",default="0.3.0-Beta")
    for k in ("dist","cache","authors","eula","out","samples","plugins","machines","sdk","region_bridge","old_machines","event_authors"):
        parser.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    main(parser.parse_args())
