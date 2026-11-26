"""Run the authors' actual region policy on 0.2.5 interactions. Dispenser decisions come from the plugin."""
import argparse,sys,time
from pathlib import Path
import beta25 as qa
from beta25 import NAMES,block,held,health,bags,mark,use,read,event
from gameplay import eventually,teleport
from content import observed

def cases(s,ws,file,first):
    a,b=ws;A,B=NAMES;result={}
    def barrier(w):w.act("qaCommand",cmd="v25 barrier");s.expect("BETA25 barrier phase",20)
    if first:
        for x,inside in [(8,True),(34,False)]:
            prefix="inside" if inside else "outside"
            for name,kind in [("pot","cauldron"),("slab","oak_slab[type=bottom,waterlogged=false]")]:
                pos=x if name=="pot" else x+2
                if name=="slab":s.send(f"fill {pos-1} 80 6 {pos+1} 80 8 stone")
                s.send(f"setblock {pos} 80 7 {kind}");s.send("item replace entity VolumeA hotbar.2 with water_bucket");held(s,a,A,2,"water_bucket")
                if name=="pot":
                    teleport(s,a,A,pos+.5,80,4.6);teleport(s,b,B,pos+2.5,80,5.0)
                else:
                    teleport(s,a,A,pos+.5,81,6.5);teleport(s,b,B,pos+1.5,81,8.5)
                phase=prefix+"-"+name;mark(s,phase);use(a,2,pos,7,block_use=name=="pot")
                barrier(a)
                if name=="pot":block(s,ws,pos,80,7,"cauldron" if inside else "water_cauldron","" if inside else "level=3")
                else:block(s,ws,pos,80,7,"oak_slab","waterlogged="+str(not inside).lower())
                held(s,a,A,2,"water_bucket" if inside else "bucket")
            teleport(s,a,A,x-2.5,80,12.5);teleport(s,b,B,x+3.5,80,12.5)
            eventually(lambda:a.act("qaPlayer",name=B),lambda p:abs(p["x"]-(x+3.5))<.25)
            s.send("execute as VolumeA at @s run tp @s ~ ~ ~ facing entity VolumeB eyes","Teleported")
            s.send('item replace entity VolumeA hotbar.3 with crossbow[minecraft:charged_projectiles=[{id:"minecraft:arrow",count:1}],minecraft:enchantments={"minecraft:piercing":2}]')
            held(s,a,A,3,"crossbow")
            mark(s,prefix+"-pierce");a.act("qaProtectedUse",slot=3,name=B)
            event(file,prefix+"-pierce","damage",str(inside).lower());health(s,ws,B,20 if inside else 17)
        # Mechanical source outside (x=16) aimed west into the region (x=15). Check the original policy.
        s.send("fill 14 80 3 17 80 5 stone");s.send("setblock 15 80 4 air")
        s.send("setblock 16 80 4 dispenser[facing=west]");s.send("item replace block 16 80 4 container.0 with water_bucket")
        teleport(s,a,A,17.5,81,5.5);teleport(s,b,B,16.5,81,5.5)
        phase="dispenser-boundary";mark(s,phase);s.send("setblock 16 81 4 redstone_block")
        rows=event(file,phase,"dispense.end","");denied=rows[-1][1].endswith("true")
        s.send("setblock 16 81 4 air");result["dispenserDenied"]=denied
        block(s,ws,15,80,4,"air" if denied else "water")
        observed(s,f'data block 16 80 4 Items[{{Slot:0b,id:"minecraft:{"water_bucket" if denied else "bucket"}",count:1}}]',"BOUNDARY_BUCKET")
    else:
        rows=read(file,"dispenser-boundary")
        result["dispenserDenied"]=next(v for k,v in rows if k=="dispense.end").endswith("true")
    result["blocks"]=[block(s,ws,8,80,7,"cauldron"),block(s,ws,10,80,7,"oak_slab","waterlogged=false"),
                      block(s,ws,34,80,7,"water_cauldron","level=3"),block(s,ws,36,80,7,"oak_slab","waterlogged=true"),
                      block(s,ws,15,80,4,"air" if result["dispenserDenied"] else "water")]
    result["health"]=[health(s,ws,A,20),health(s,ws,B,17)];result["bags"]=bags(s,ws)
    return result

if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--host",choices=["paper","unity"],required=True)
    for k in ("dist","paper","authors","eula","samples","cache","out"):p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    o=p.parse_args();o.regions=True;o.plain=False;qa.regions=cases;qa.run(o)
