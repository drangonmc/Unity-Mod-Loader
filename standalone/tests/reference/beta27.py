"""Paper chest control and original NeoForge machine WorldEdit history on the mixed Unity server."""
import argparse
import json
import re
import shutil
from pathlib import Path
import beta25 as qa
import beta26
import neo26
from gameplay import eventually, teleport
from content import observed

PINS = Path(__file__).with_name("machines-26.2.json")
MACHINES = [
    ("advancedfurnace:advanced_furnace","me.andandsf.advancedfurnace.AdvancedFurnaceScreen",22,34,10,1),
    ("copperhopper:copper_hopper","net.pcal.copperhopper.common.CohoScreen",25,39,13,2),
]
VALUES = {
    "source": ("east","emerald",11,73),
    "previous": ("west","diamond",3,41),
    "protected": ("north","lapis_lazuli",7,19),
}

def seed(s,x,kind,tag,slot):
    facing,item,count,number = VALUES[tag]
    s.send(f"setblock {x} 80 12 {kind}[facing={facing}]")
    s.send(f'item replace block {x} 80 12 container.{slot} with minecraft:{item}[minecraft:custom_data={{unity_machine:{number}}}] {count}')
    fields = f'CustomName:"Machine-{tag}"'
    if kind.startswith("advancedfurnace:"): fields += f",lit_total_time:{number}s"
    s.send(f"data merge block {x} 80 12 {{" + fields + "}")
    observed(s,f'data block {x} 80 12 Items[{{Slot:{slot}b,id:"minecraft:{item}",count:{count}}}]',"SEEDED_MACHINE")

def inspect(s,windows,x,kind,screen,slot,tag,open_menu):
    facing,item,count,number = VALUES[tag]
    observed(s,f"block {x} 80 12 {kind}[facing={facing}]","MACHINE_STATE")
    raw=qa.strip(s.send(f"data get block {x} 80 12","block data:"))
    assert f"Machine-{tag}" in raw and re.search(r'\bunity_machine["\']?\s*:\s*'+str(number)+r'\b',raw),raw
    tile_id=kind if kind.startswith("advancedfurnace:") else "copperhopper:copper_hopper_entity"
    assert f'id: "{tile_id}"' in raw and re.search(rf'\bx: {x}\b',raw) and "y: 80" in raw and "z: 12" in raw,raw
    if kind.startswith("advancedfurnace:"):assert f'lit_total_time: {number}s' in raw,raw
    assert raw.count("Slot:")==1,raw
    observed(s,f'data block {x} 80 12 Items[{{Slot:{slot}b,id:"minecraft:{item}",count:{count}}}]',"MACHINE_INVENTORY")
    result={"id":kind,"facing":facing,"name":"Machine-"+tag,"slot":slot,"item":item,"count":count,"marker":number,"clients":[]}
    for w,n in zip(windows,qa.NAMES):
        view=eventually(lambda:w.act("qaBlock",x=x,y=80,z=12),lambda b:b["id"]==kind and "facing="+facing in b["state"])
        display={"state":view["state"]}
        if open_menu:
            teleport(s,w,n,x+.5,80,9.6)
            menu=w.act("menuOpen",handSlot=0,x=x,y=80,z=12)
            try:
                assert menu["screen"]==screen,menu["screen"]
                def contents(m):return [c for c in m["slots"] if c["inventory"]==-1 and c["count"]>0]
                cells=contents(menu)
                if not cells:cells=eventually(lambda:contents(w.act("menuInfo")),bool,8)
                assert len(cells)==1,cells
                cell=cells[0]
                assert (cell["containerSlot"],cell["id"],cell["count"])==(slot,"minecraft:"+item,count),cell
                assert re.search(r'unity_machine["\']?\s*:\s*'+str(number)+r'\b',cell["custom"]),cell
                display.update(screen=screen,slot=slot,count=count,marker=number)
            finally:w.act("menuClose")
        result["clients"].append(display)
    return result

def running_machines(s,first):
    def inventory(x,y,z,index,item,count,label):
        observed(s,f'data block {x} {y} {z} Items[{{Slot:{index}b,id:"minecraft:{item}",count:{count}}}]',label,35)
    if first:
        for command in ("setblock 26 80 6 advancedfurnace:advanced_furnace[facing=north]",
                        "item replace block 26 80 6 container.1 with raw_iron",
                        "item replace block 26 80 6 container.0 with coal",
                        "setblock 28 80 4 furnace", "item replace block 28 80 4 container.0 with raw_gold",
                        "item replace block 28 80 4 container.1 with clay_ball[minecraft:custom_data={unity_fuel:1}]",
                        "setblock 30 80 14 advancedfurnace:advanced_furnace[facing=north]",
                        "setblock 31 80 14 copperhopper:copper_hopper[facing=west]",
                        "item replace block 31 80 14 container.0 with coal 3",
                        "setblock 29 80 6 chest", "setblock 29 81 6 copperhopper:copper_hopper[facing=down]",
                        "setblock 29 82 6 barrel", "item replace block 29 81 6 container.0 with emerald",
                        "item replace block 29 82 6 container.0 with emerald 4",
                        "item replace block 29 82 6 container.1 with diamond 3"):
            s.send(command)
    inventory(26,80,6,2,"iron_ingot",1,"FURNACE_COOKED")
    inventory(28,80,4,2,"gold_ingot",1,"CUSTOM_FUEL_COOKED")
    inventory(30,80,14,0,"coal",2,"MACHINE_FUEL_TRANSFER")
    inventory(31,80,14,0,"coal",1,"MACHINE_FUEL_FILTER")
    inventory(29,80,6,0,"emerald",4,"HOPPER_OUTPUT")
    inventory(29,81,6,0,"emerald",1,"HOPPER_FILTER")
    inventory(29,82,6,1,"diamond",3,"HOPPER_REJECTED")

def machine_cases(s,ws,first):
    running_machines(s,False)
    results={}
    for kind,screen,source,dest,guard,slot in MACHINES:
        a=ws[0]
        scratch = 42 if kind.startswith("advancedfurnace:") else 44
        def move(x):teleport(s,a,qa.NAMES[0],x+.5,80,9.5)
        def check(x,tag,opened=True):return inspect(s,ws,x,kind,screen,slot,tag,opened)
        if first:
            for x,tag in ((source,"source"),(dest,"previous"),(guard,"protected")):seed(s,x,kind,tag,slot)
            check(source,"source")
            check(dest,"previous")
            move(source)
            for i in (1,2):
                beta26.command(a,f"/pos{i} {source},80,12",r"(?:First|Second) position")
            beta26.command(a,"/copy",r"copied|clipboard|1 blocks? affected")
            move(guard)
            beta26.command(a,"/paste",r"pasted|clipboard|blocks? affected")
            check(guard,"protected",False)
            move(dest)
            beta26.command(a,"/paste",r"pasted|clipboard|blocks? affected")
            check(dest,"source")
            beta26.command(a,"/undo",r"undo|undid")
            check(dest,"previous")
            beta26.command(a,"/redo",r"redo|redid")
            # The second editor has clipboard read permission. The region's build rule protects its destination.
            b=ws[1]
            s.send(f"setblock {scratch} 80 12 air")
            teleport(s,b,qa.NAMES[1],guard+.5,80,9.5)
            for i in (1,2):
                beta26.command(b,f"/pos{i} {guard},80,12",r"(?:First|Second) position")
            beta26.command(b,"/copy",r"copied|clipboard|1 blocks? affected")
            teleport(s,b,qa.NAMES[1],scratch+.5,80,9.5)
            beta26.command(b,"/paste",r"pasted|clipboard|blocks? affected")
            check(scratch,"protected")
            beta26.command(b,"/undo",r"undo|undid")
            qa.block(s,ws,scratch,80,12,"air")
            beta26.command(b,"/redo",r"redo|redid")
        results[kind]={"source":check(source,"source"),"destination":check(dest,"source"),
                       "region":check(guard,"protected",False),"regionalCopy":check(scratch,"protected")}
    return results

def run(o):
    pins=json.loads(PINS.read_bytes())
    receipts={}
    def configure(game):
        shutil.copy2(o.dist/"UnityEditRegions.jar",game/"plugins")
        if getattr(o,"install_review",None):o.install_review(game)
        if o.host=="unity":
            target=game/"mods/unity-neo26-probe.jar"
            neo26.fixture(qa.ROOT,o.cache,o.sdk,target,[Path(__file__).with_name('Neo27FuelProbe.java')])
            for p in qa.NAMES:shutil.copy2(target,game.parent/p/"mods")
            for base in [game,*(game.parent/p for p in qa.NAMES)]:
                for row in pins:
                    src=o.machines/row["filename"]
                    assert qa.digest(src,"sha512")==row["sha512"]
                    actual=base/"mods"/src.name
                    shutil.copy2(src,actual)
                    assert qa.digest(actual,"sha512")==row["sha512"]
            receipts["neoForgeWitness"]=qa.digest(target)
            receipts["machineOriginals"]={r["project"]:r["sha512"] for r in pins}
    def check_installed(game):
        if o.host=="unity":
            for base in [game,*(game.parent/p for p in qa.NAMES)]:
                for row in pins: assert qa.digest(base/"mods"/row["filename"],"sha512")==row["sha512"]
    def check_all(game):
        check_installed(game)
        if getattr(o,"check_review",None):o.check_review(game)
    o.check_installed=check_all
    def cases(s,ws,file,first):
        review=o.review_cases(s,ws,first) if o.host=="unity" and getattr(o,"review_cases",None) else None
        data=beta26.cases(s,ws,file,first)
        if o.host=="unity":
            data["machines"]=machine_cases(s,ws,first)
            if review is not None:data["review28"]=review
        return data
    prepare_world=qa.prepare
    def prepare(server):
        prepare_world(server)
        if o.host=="unity": running_machines(server,True)
    qa.prepare=prepare
    o.version=getattr(o,"version","0.2.7-Beta");o.regions=True;o.plain=False;o.configure=configure
    qa.observer=beta26.observer;qa.regions=cases;qa.run(o)
    result=o.out/o.host/"result.json"
    data=json.loads(result.read_bytes())
    assert data["runs"][0]==data["runs"][1]
    if o.host=="unity":
        for phase in ("first","restart"):
            log=(result.parent/(phase+".log")).read_text("utf8",errors="replace")
            assert log.count("UNITY_NEO26_TICKS_OK extension priority thread unregister")==1
    data.update(receipts,regionIntegration=qa.digest(o.dist/"UnityEditRegions.jar"))
    result.write_text(json.dumps(data,indent=2),"utf8")
if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--host",choices=["paper","unity"],required=True)
    for k in ("dist","paper","authors","eula","samples","cache","out","machines"):
        p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    p.add_argument("--sdk",type=lambda s:Path(s).resolve(),default=qa.ROOT/".work/v14/refs")
    run(p.parse_args())
