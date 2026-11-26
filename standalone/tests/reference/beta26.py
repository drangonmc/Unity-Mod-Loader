"""Original WorldEdit command and container history comparison on Paper and a mixed Unity server."""
import argparse
import json
import re
import shutil
import time
import zipfile
from pathlib import Path
import beta25 as qa
from beta25 import NAMES, bags, block, health, strip
from content import observed
from gameplay import teleport, eventually

PERMISSIONS = ["worldedit.selection.pos", "worldedit.region.set", "worldedit.clipboard.copy",
               "worldedit.clipboard.paste", "worldedit.setnbt", "worldedit.history.undo", "worldedit.history.redo"]
original_observer = qa.observer

def observer(temp, cache):
    probe = original_observer(temp, cache)
    with zipfile.ZipFile(probe) as archive:
        files = {n: archive.read(n) for n in archive.namelist()}
    files["plugin.yml"] += "".join("  " + p + ":\n    default: true\n" for p in PERMISSIONS).encode()
    with zipfile.ZipFile(probe, "w") as archive:
        for name, data in files.items():
            archive.writestr(zipfile.ZipInfo(name), data)
    return probe

def command(window, text, reply):
    log = Path(window.log.name)
    offset = log.stat().st_size
    window.act("qaCommand", cmd=text)
    def feedback():
        with log.open("rb") as f:
            f.seek(offset)
            return f.read().decode("utf8", errors="replace")
    eventually(feedback, lambda lines: re.search(reply, lines, re.I), 20)

def cases(server, windows, file, first):
    a,b = windows
    def position(which, x, y, z):
        command(a, f"/pos{which} {x},{y},{z}", r"(?:First|Second) position|pos(?:ition)? set|set to")
    def selection(x1, x2):
        position(1, x1, 80, 7)
        position(2, x2, 80, 7)
    def check(x, name):
        return block(server,windows,x,80,7,name)
    def contents(x, item, count):
        server.send(f"data get block {x} 80 7", "block data:")
        observed(server, f'data block {x} 80 7 Items[{{Slot:3b,id:"minecraft:{item}",count:{count}}}]', "CHEST_"+str(x))
        raw = strip(server.send(f"data get block {x} 80 7 Items","block data:"))
        assert raw.count("Slot:")==1 and "unity_copy" in raw,raw
        displays=[]
        for w,n in zip(windows,NAMES):
            teleport(server,w,n,x+.5,80,4.8)
            view=w.act("menuOpen",handSlot=0,x=x,y=80,z=7)
            found=view["slots"][3]
            assert (found["id"],found["count"]) == ("minecraft:"+item,count),found
            displays.append([found["id"],found["count"]])
            w.act("menuClose")
        return displays
    if first:
        server.send("rg info -w world protected",r"Region: .*protected")
        server.send("fill 12 80 7 18 80 7 smooth_stone")
        teleport(server,a,NAMES[0],18.5,80,4.5)
        teleport(server,b,NAMES[1],16.5,80,4.5)
        selection(13,18)
        command(a,"/set gold_block",r"blocks? (?:have been|were|changed|affected)|operation completed")
        for x in range(13,19):check(x,"smooth_stone" if x<=15 else "gold_block")
        command(a,"/undo",r"undo|undid")
        for x in range(13,19):check(x,"smooth_stone")
        command(a,"/redo",r"redo|redid")
        for x in range(13,19):check(x,"smooth_stone" if x<=15 else "gold_block")
        for x,item,n in [(24,"emerald",11),(30,"diamond",3)]:
            server.send(f"setblock {x} 80 7 chest[facing=north]")
            server.send(f'item replace block {x} 80 7 container.3 with minecraft:{item}[minecraft:custom_data={{unity_copy:1}}] {n}')
        teleport(server,a,NAMES[0],24.5,80,4.5)
        selection(24,24)
        command(a,"/copy",r"copied|clipboard|1 blocks? affected")
        server.send("setblock 9 80 7 chest[facing=north]")
        server.send("item replace block 9 80 7 container.3 with lapis_lazuli 7")
        teleport(server,a,NAMES[0],9.5,80,4.5)
        command(a,"/paste",r"pasted|clipboard|blocks? affected")
        observed(server,'data block 9 80 7 Items[{Slot:3b,id:"minecraft:lapis_lazuli",count:7}]',"REGION_CHEST_INTACT")
        teleport(server,a,NAMES[0],30.5,80,4.5)
        command(a,"/paste",r"pasted|clipboard|1 blocks? affected")
        contents(30,"emerald",11)
        command(a,"/undo",r"undo|undid")
        contents(30,"diamond",3)
        command(a,"/redo",r"redo|redid")
        contents(30,"emerald",11)
        server.send("item replace entity VolumeA hotbar.4 with obsidian 3")
        server.send("item replace entity VolumeB hotbar.4 with flint 5")
    for x,w,n in [(18,a,NAMES[0]),(17,b,NAMES[1])]:teleport(server,w,n,x+.5,80,4.5)
    observed(server,'data block 9 80 7 Items[{Slot:3b,id:"minecraft:lapis_lazuli",count:7}]',"REGION_CHEST_INTACT")
    check(9,"chest")
    states=[check(x,"smooth_stone" if x<=15 else "gold_block") for x in range(13,19)]
    chests=[contents(x,"emerald",11) for x in (24,30)]
    return {"blocks":[states,chests],"bags":bags(server,windows),"health":[health(server,windows,n,20) for n in NAMES]}

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--host",choices=["unity","paper"],required=True)
    for key in ("dist","paper","authors","eula","samples","cache","out"):
        p.add_argument("--"+key,type=lambda s:Path(s).resolve(),required=True)
    p.add_argument("--sdk",type=lambda s:Path(s).resolve(),default=qa.ROOT/".work/v14/refs")
    o=p.parse_args()
    o.version="0.2.6-Beta"
    o.regions=True
    o.plain=False
    def configure(game):
        shutil.copy2(o.dist/"UnityEditRegions.jar",game/"plugins")
        if o.host=="unity":
            import neo26
            target=game/"mods/unity-neo26-probe.jar"
            neo26.fixture(qa.ROOT,o.cache,o.sdk,target)
            for player in NAMES: shutil.copy2(target,game.parent/player/"mods")
            o.neo_hash=qa.digest(target)
    o.configure=configure
    qa.observer=observer
    qa.regions=cases
    qa.run(o)
    result=o.out/o.host/"result.json"
    data=json.loads(result.read_bytes())
    if o.host=="unity":
        for phase in ("first","restart"):
            log=(result.parent/(phase+".log")).read_text("utf8",errors="replace")
            assert log.count("UNITY_NEO26_TICKS_OK extension priority thread unregister")==1,phase
        data["neoForgeWitness"]=o.neo_hash
    data["regionIntegration"]=qa.digest(o.dist/"UnityEditRegions.jar")
    result.write_text(json.dumps(data,indent=2),"utf8")
