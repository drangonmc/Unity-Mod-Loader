import time,re
from pathlib import Path
from gameplay import eventually
from content import observed
import beta25 as base

def review(s,ws):
    a,b=ws;log=Path(s.log.name)
    s.send("difficulty normal")
    def rows():
        return [line.split("NEO31 ",1)[1] for line in log.read_text("utf8",errors="replace").splitlines() if "NEO31 " in line]
    for tag in ("rev31zero","rev31forgehurt","rev31forgedamage","rev31lethal"):
        s.send(f'summon cow 60 80 27 {{NoAI:1b,Tags:["{tag}"]}}',"Summoned")
        s.send(f'damage @e[tag={tag},limit=1] '+('100' if tag=="rev31lethal" else '4')+" minecraft:generic")
        if tag=="rev31lethal":
            s.expect("NEO31 rev31lethal drops",15)
            series=[v.split()[1] for v in rows() if v.startswith(tag+" ")]
            assert series==["pre","post","death","drops"],series
        else:
            observed(s,'entity @e[tag='+tag+',nbt={Health:10.0f}]',"UNCHANGED_"+tag)
            assert not any(v.startswith(tag+" post") for v in rows())
    output=log.read_text("utf8",errors="replace")
    assert "FORGE31 hurt-cancel" in output and "FORGE31 damage-cancel" in output
    # Capture actual inventory drops from ServerPlayer after a remote player's lethal hit.
    s.send("gamerule minecraft:keep_inventory false")
    s.send("tp VolumeB 67.5 80 27.5 0 0","Teleported")
    s.send("item replace entity VolumeB hotbar.5 with amethyst_shard 11")
    s.send("tag VolumeB add rev31player","Added tag")
    s.send("damage VolumeB 100 minecraft:generic")
    s.expect("NEO31 player contained 11; replaced with 3",20)
    observed(s,'entity @e[type=item,x=67.5,y=80,z=27.5,distance=..4,nbt={Item:{id:"minecraft:diamond",count:3}}]',"PLAYER_REPLACEMENT")
    s.send('execute unless entity @e[type=item,x=67.5,y=80,z=27.5,distance=..5,nbt={Item:{id:"minecraft:amethyst_shard"}}] run say NO_ORIGINAL_PLAYER_DROP',r"\[Server\] NO_ORIGINAL_PLAYER_DROP")
    eventually(lambda:b.act("qaPlayer"),lambda p:p["dead"]);b.act("qaRespawn");eventually(lambda:b.act("qaPlayer"),lambda p:not p["dead"])
    s.send("tp VolumeB 66.5 80 20.5 0 0","Teleported");s.send("tag VolumeB remove rev31player")
    s.send("tp VolumeA 64.5 80 20.5 0 0","Teleported")
    s.send("summon zombie 64.5 80 23.5 {Tags:[\"rev31source\"],NoAI:1b,Silent:1b}","Summoned")
    s.send("item replace entity VolumeA hotbar.1 with shield");s.send("tag VolumeA add rev31shield","Added tag")
    base.held(s,a,"VolumeA",1,"shield",1)
    a.act("qaHoldShield",slot=1);time.sleep(.8)
    s.send("damage VolumeA 5 minecraft:mob_attack by @e[tag=rev31source,limit=1]");s.expect("NEO31 shield 5.0",15)
    base.health(s,ws,"VolumeA",20);a.act("qaRelease")
    assert not any(v.startswith("rev31shield post") for v in rows())
    observed(s,'data entity VolumeA Inventory[{id:"minecraft:shield",components:{"minecraft:damage":2}}]',"SHIELD_WEAR_2")
    s.send("tag VolumeA remove rev31shield");s.send("item replace entity VolumeA hotbar.1 with air")
    s.send("setblock 62 80 24 stone");s.send("tp VolumeA 62.5 80 21.5 0 0","Teleported")
    eventually(lambda:a.act("qaPlayer"),lambda p:abs(p["x"]-62.5)<.05 and abs(p["z"]-21.5)<.05)
    base.block(s,ws,62,80,24,"stone")
    s.send("item replace entity VolumeA hotbar.1 with diamond_pickaxe")
    base.held(s,a,"VolumeA",1,"diamond_pickaxe",1)
    a.act("qaMine",slot=1,x=62,y=80,z=24);base.block(s,ws,62,80,24,"air")
    s.expect("NEO31 break-server false",8) if "NEO31 break-server false" not in log.read_text("utf8",errors="replace") else None
    # The two callers use the same bus while each keeps its own prediction domain.
    cl=Path(a.log.name).read_text("utf8",errors="replace")
    for marker in ("client-tick-pre","client-tick-post","client-load","client-join","break-client false"):
        assert "NEO31 "+marker in cl,marker
    s.send("gamemode adventure VolumeA")
    s.send("setblock 62 80 24 stone")
    base.block(s,ws,62,80,24,"stone")
    a.act("qaMine",slot=1,x=62,y=80,z=24);base.block(s,ws,62,80,24,"stone")
    s.send("gamemode survival VolumeA")
    s.send("setblock 63 79 24 dirt");s.send("setblock 63 80 24 short_grass")
    s.send("item replace entity VolumeA hotbar.1 with bricks 2")
    s.send("tp VolumeA 63.5 80 21.8 0 0","Teleported")
    eventually(lambda:a.act("qaPlayer"),lambda p:abs(p["x"]-63.5)<.05 and abs(p["z"]-21.8)<.06)
    base.block(s,ws,63,80,24,"short_grass");base.held(s,a,"VolumeA",1,"bricks",2)
    a.act("qaRight",slot=1,x=63,y=80,z=24);base.block(s,ws,63,80,24,"bricks")
    assert re.search(r"NEO31 against .*short_grass",log.read_text("utf8",errors="replace"))
    s.send("item replace entity VolumeA hotbar.1 with air")
    # Force a real client chunk eviction.
    s.send("forceload add 400 20");observed(s,"loaded 400 80 20","REMOTE_CHUNK",40)
    s.send("setblock 400 79 20 stone");s.send("tp VolumeA 400.5 80 20.5","Teleported")
    eventually(lambda:Path(a.log.name).read_text("utf8",errors="replace"),lambda text:"NEO31 client-unload" in text,20)
    s.send("tp VolumeA 64.5 80 20.5 0 0","Teleported")
    # The comparison walk crosses the mining area. Remove test drops before seeding both hosts with identical bags.
    s.send("execute as @e[tag=rev31source] run tp @s 400.5 80 20.5")
    s.send("execute as @e[type=item,x=65,y=80,z=24,distance=..14] run tp @s 400.5 80 20.5")
    s.send("execute unless entity @e[type=item,x=65,y=80,z=24,distance=..14] run say U31_FIXTURES_CLEARED",r"\[Server\] U31_FIXTURES_CLEARED")
    return {"lethal":["pre","post","death","drops"],"zeroPost":False,"forgeCancelPost":False,"playerSource":11,"playerReplacedWith":3,"shieldDurability":2}
