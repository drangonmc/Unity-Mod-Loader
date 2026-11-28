"""Exercise unmodified machine AI, with all expected outcomes checked on the real server."""
import re
import time
from pathlib import Path
from content import observed,slot
from gameplay import eventually

ENDPOINTS={65:32,66:39,67:35,68:40,69:36}
IDS={"VolumeA":"d72a93df-334e-34e6-81f8-67ddcdeb22db","VolumeB":"0b731fd8-6b40-3e28-b59c-a5d6dcaf3d3d"}
ORES={65:"iron_block",66:"gold_block",67:"emerald_block",68:"lapis_block",69:"copper_block"}

def check_block(s,windows,x,y,z,id):
    observed(s,f"block {x} {y} {z} {id}","U33_BLOCK",22)
    for w in windows:
        eventually(lambda:w.act("qaBlock",x=x,y=y,z=z),lambda b:b["id"]==id,16)

def give(s,w,name,i,item):
    s.send(f"item replace entity {name} hotbar.{i} with {item}","Replaced")
    slot(w,i,item,1)

def chest(s,x,item,n,timeout=35):
    condition=f'data block {x} 80 {ENDPOINTS[x]} {{Items:[{{Slot:0b,id:"minecraft:{item}",count:{n}}}]}}' if n else f'data block {x} 80 {ENDPOINTS[x]} {{Items:[]}}'
    observed(s,condition,f"U33_CHEST_{x}_{n}",timeout)

def cargo(s,name,n):
    line=s.send("u33drone "+name+" read",r"U33 CARGO "+IDS[name]+r" \d+")
    assert int(line.split("U33 CARGO ")[1].split()[1])==n,line

def snapshot(s,windows):
    for x,ore in ORES.items():
        check_block(s,windows,x,80,25,"minecraft:"+("air" if x in (65,69) else ore))
    core=s.send("data get block 64 81 24","block data:")
    assert "unity:machine_owner" in core and "VolumeA" in core,core
    s.send("u33quarry",r"U33 QUARRY FE 0\b")
    for item in ("iron_block","copper_block"):
        assert re.search(r'count: 1, id: "minecraft:'+item+'"|id: "minecraft:'+item+'", count: 1',core),core
    for item in ("gold_block","emerald_block","lapis_block"):assert "minecraft:"+item not in core,core
    values={65:("emerald",0),66:("diamond",9),67:("diamond",0),68:("emerald",0),69:("emerald",12)}
    for x,(item,q) in values.items():chest(s,x,item,q)
    for name in IDS:cargo(s,name,0)
    return {"core":core.split("block data:")[-1].strip(),"chests":values}

def attachments(s,windows,tp,first):
    a,b=windows
    for w,n in zip(windows,("VolumeA","VolumeB")):
        tp(w,n,69.5,80,33.5 if n=="VolumeA" else 34.7)
        menu=w.act("menuOpen",handSlot=0,x=69,y=80,z=36)
        assert menu["slots"][0]["id"]=="minecraft:emerald" and menu["slots"][0]["count"]==12,menu["slots"][0]
    for w in windows:w.act("menuClose")
    tp(b,"VolumeB",66.5,80,36.7)
    menu=b.act("menuOpen",handSlot=0,x=66,y=80,z=39)
    assert menu["slots"][0]["id"]=="minecraft:diamond" and menu["slots"][0]["count"]==9,menu["slots"][0]
    b.act("menuClose")
    if first:s.send("u33sync","U33 SYNC WRITTEN")
    for w,n in zip(windows,("VolumeA","VolumeB")):tp(w,n,69.5,80,33.5 if n=="VolumeA" else 34.7)
    def check(w,expected):
        eventually(lambda:w.act("qaMachineAttachments"),lambda v:v["values"]==expected,25)
    check(a,"minecraft:overworld 3302 3303 3305");check(b,"minecraft:overworld null 3303 3305")
    s.send("execute in minecraft:the_nether run tp VolumeA 40.5 90.0 40.5","Teleported")
    check(a,"minecraft:the_nether 3302 3304 null")
    s.send("execute in minecraft:overworld run tp VolumeA 69.5 80.0 33.5","Teleported")
    check(a,"minecraft:overworld 3302 3303 3305")

def run(s,windows,tp,first):
    a,b=windows
    if not first:return snapshot(s,windows)
    # Validate the author's complete frame at native server tick.
    for cmd in ("fill 64 81 24 70 81 24 quietquarry:quarry_frame","fill 64 81 28 70 81 28 quietquarry:quarry_frame",
                "fill 64 81 25 64 81 27 quietquarry:quarry_frame","fill 70 81 25 70 81 27 quietquarry:quarry_frame",
                "setblock 64 81 24 air","fill 62 80 20 65 80 24 stone","fill 65 80 25 69 80 27 air"):
        s.send(cmd)
    for x,ore in ORES.items():s.send(f"setblock {x} 80 25 {ore}")
    tp(a,"VolumeA",64.5,81,21.65);tp(b,"VolumeB",62.5,81,22.5)
    give(s,a,"VolumeA",7,"quietquarry:quarry_core")
    a.act("qaRight",slot=7,x=64,y=80,z=24,top=True)
    observed(s,"block 64 81 24 quietquarry:quarry_core","U33_PLACED")
    s.send("u33power","U33 QUARRY FE 800")
    for x,ore in ORES.items():
        check_block(s,windows,x,80,25,"minecraft:"+("air" if x in (65,69) else ore))
    for x,z in ENDPOINTS.items():s.send(f"setblock {x} 80 {z} chest")
    for w,name,x in [(a,"VolumeA",63),(b,"VolumeB",71)]:
        tp(w,name,x+.5,80,29.65)
        give(s,w,name,7,"logisticsdrones:stone_drone")
        w.act("qaRight",slot=7,x=x,y=79,z=32,top=True)
        s.send("u33drone "+name+" off","U33 DRONE OFF",15)
    s.send("item replace block 65 80 32 container.0 with emerald 12","Replaced")
    s.send("item replace block 66 80 39 container.0 with diamond 4","Replaced")
    s.send("item replace block 67 80 35 container.0 with diamond 5","Replaced")
    log=Path(s.log.name)
    for src,dst,denied,op in [(65,67,67,"INSERT"),(66,69,66,"EXTRACT"),(67,69,67,"EXTRACT")]:
        start=log.stat().st_size
        s.send(f"u33drone VolumeA route {src} {dst}","U33 ROUTE")
        def trace():
            with log.open("rb") as f:f.seek(start);return f.read().decode("utf8","replace")
        eventually(trace,lambda t:any(f"U33 POLICY {op} {denied}, 80, {ENDPOINTS[denied]}" in l and "false" in l for l in t.splitlines()),30)
        cargo(s,"VolumeA",0);chest(s,65,"emerald",12);chest(s,67,"diamond",5);chest(s,66,"diamond",4);chest(s,69,"emerald",0)
        s.send("u33drone VolumeA off","U33 DRONE OFF")
    for owner,src,dst,item,q in [("VolumeA",65,69,"emerald",12),("VolumeB",67,66,"diamond",5)]:
        s.send(f"u33drone {owner} route {src} {dst}","U33 ROUTE")
        chest(s,dst,item,q+(4 if dst==66 else 0),100);chest(s,src,item,0);cargo(s,owner,0)
        s.send(f"u33drone {owner} off","U33 DRONE OFF")
    return snapshot(s,windows)
