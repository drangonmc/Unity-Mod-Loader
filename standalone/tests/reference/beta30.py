"""Run the 0.2.7 gameplay controls against the exact 0.2.8 candidate."""
import io, hashlib, zipfile, re
import argparse
import json
import shutil
from pathlib import Path
import beta27
import beta25 as qa

def totals(server):
    return sum(int(re.search(r"entity data: (\d+)",qa.strip(server.send("data get entity "+n+" XpTotal","entity data:")))[1]) for n in qa.NAMES)

def run(o):
    o.version="0.3.0-Beta"
    upstream=json.loads(Path(__file__).with_name("compat-26.2.json").read_bytes())
    if o.older_worldedit:
        spec=upstream["worldedit"]
        o.edit_version={"slug":"worldedit","filename":spec["filename"],"sha512":spec["hashes"]["sha512"]}
    if o.alternates and o.host=="unity":
        mixed=qa.protection.mixed
        def install(opts,cache,game,clients):
            manifest=mixed(opts,cache,game,clients)
            fabric=upstream["fabric-api"]
            puzzles=upstream["puzzles-lib"]
            for g in [game,*clients]:
                mods=g/"mods"
                for spec,pattern in ((fabric,"fabric-api-[0-9]*.jar"),(puzzles,"*uzzles*ib*.jar")):
                    old,=mods.glob(pattern)
                    if spec is fabric:
                        def modules(umbrella):
                            with zipfile.ZipFile(umbrella) as z:
                                entries={}
                                for member in json.loads(z.read("fabric.mod.json"))["jars"]:
                                    name=member["file"]; blob=z.read(name)
                                    with zipfile.ZipFile(io.BytesIO(blob)) as child:
                                        ident=json.loads(child.read("fabric.mod.json"))["id"]
                                    entries[ident]=(Path(name).name,blob)
                                return entries
                        before=modules(old); after=modules(o.alternates/spec["filename"])
                        for ident,(name,blob) in before.items():
                            duplicate=mods/name
                            if not duplicate.exists():continue
                            assert duplicate.read_bytes()==blob
                            duplicate.unlink();manifest[g.name].pop(name,None)
                            replacement,data=after[ident]
                            (mods/replacement).write_bytes(data)
                            manifest[g.name][replacement]=hashlib.sha512(data).hexdigest()
                    old.unlink()
                    new=mods/spec["filename"]
                    original=o.alternates/new.name
                    if qa.digest(original,"sha512")!=spec["hashes"]["sha512"]:raise ValueError(new.name)
                    shutil.copy2(original,new)
                    if old.name in manifest[g.name]: del manifest[g.name][old.name]
                    manifest[g.name][new.name]=qa.digest(new,"sha512")
            return manifest
        qa.protection.mixed=install
    from review28.probes import build
    o.project=qa.ROOT
    def fixtures(game):
        if o.host!="unity":return
        files=build(o,game.parent)
        shutil.copy2(files["Unity28Bukkit"],game/"plugins")
        native=game/"unity-mods";native.mkdir(exist_ok=True)
        shutil.copy2(files["unity28drop"],native)
        for g in [game,*(game.parent/n for n in qa.NAMES)]:
            for mod in ("unity28registry","unity28forge"):shutil.copy2(files[mod],g/"mods")
    def review(server,ws,first):
        from gameplay import teleport
        from content import observed
        if first:
            # Keep the observer in view of the test blocks and outside item pickup range.
            teleport(server,ws[1],qa.NAMES[1],36.5,80,3.5)
            server.send("item replace entity VolumeA hotbar.2 with diamond_pickaxe")
            for x,kept in [(35,True),(37,False)]:
                server.send(f"setblock {x} 80 8 stone")
                teleport(server,ws[0],qa.NAMES[0],x+.5,80,5.7)
                ws[0].act("qaMine",slot=2,x=x,y=80,z=8)
                server.expect("UNITY28_BUKKIT_BREAK "+str(x)+" "+str(kept).lower(),20)
            # Prevent pickup during the comparison. Check actual item entities, vanilla XP and the positive control.
            for x in (41,44):
                server.send(f"setblock {x} 80 8 diamond_ore")
                teleport(server,ws[0],qa.NAMES[0],x+.5,80,5.1)
                before=totals(server)
                ws[0].act("qaMine",slot=2,x=x,y=80,z=8)
                qa.block(server,ws,x,80,8,"air")
                selector=f'@e[type=item,x={x+.5},y=80,z=8.5,distance=..2,nbt={{Item:{{id:"minecraft:diamond"}}}}]'
                if x==41:
                    server.send("execute unless entity "+selector+" run say NO_ITEM_DROP",r"\[Server\] NO_ITEM_DROP")
                    from gameplay import eventually
                    def xp():
                        log=Path(server.log.name).read_text("utf8",errors="replace")
                        return bool(re.search(r"UNITY28_HARVEST orbs=[1-9]",log)) or totals(server)>before
                    eventually(xp,bool,8)
                    server.send("say VANILLA_XP",r"\[Server\] VANILLA_XP")
                else:
                    observed(server,"entity "+selector,"NORMAL_ITEM_DROP")
                    server.send("execute as "+selector+" run tp @s 5 75 31")
                    server.send("execute unless entity "+selector+" run say CONTROL_ITEM_MOVED",r"\[Server\] CONTROL_ITEM_MOVED")
            for name in qa.NAMES:
                inventory=qa.strip(server.send("data get entity "+name+" Inventory","entity data:"))
                assert 'id: "minecraft:diamond"' not in inventory,inventory
            server.send("item replace entity VolumeA hotbar.2 with air")
        for x,state in [(35,"stone"),(37,"air")]:qa.block(server,ws,x,80,8,state)
        server.send("effect give VolumeA unity28registry:marked 4 0 true","Applied effect")
        server.send("effect clear VolumeA unity28registry:marked")
        return {"forgeCancelled":2,"bukkitObserved":2,"restoredByBukkit":1,"effect":"unity28registry:marked"}
    sdks={}
    def installed(game):
        if o.host!="unity":return
        for g in [game,*(game.parent/n for n in qa.NAMES)]:
            sdks[g.name]={}
            for key,pattern in (("fabric-api","fabric-api-[0-9]*.jar"),("puzzles-lib","*uzzles*ib*.jar")):
                f,=(g/"mods").glob(pattern)
                sdks[g.name][key]={"filename":f.name,"sha512":qa.digest(f,"sha512")}
                if o.alternates:assert sdks[g.name][key]["sha512"]==upstream[key]["hashes"]["sha512"]
    o.install_review=fixtures;o.review_cases=review;o.check_review=installed
    beta27.run(o)
    result=o.out/o.host/"result.json"
    data=json.loads(result.read_bytes())
    if sdks:data["platformJars"]=sdks
    result.write_text(json.dumps(data,indent=2),"utf8")

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--host",choices=["paper","unity"],required=True)
    for k in ("dist","paper","authors","eula","samples","cache","out","machines"):
        p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    p.add_argument("--older-worldedit",action="store_true")
    p.add_argument("--alternates",type=lambda s:Path(s).resolve())
    p.add_argument("--sdk",type=lambda s:Path(s).resolve(),default=qa.ROOT/".work/v14/refs")
    run(p.parse_args())
