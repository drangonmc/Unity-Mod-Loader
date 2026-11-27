"""Paper/Unity comparison for 0.2.5. Each run uses two hidden clients and a fresh temporary world."""
import argparse,contextlib,json,os,re,shutil,socket,subprocess,sys,tempfile,time,zipfile
from pathlib import Path
HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE.parent))
import acceptance,content
from acceptance import ROOT,Server,digest
from content import Window,observed,slot
from dual import exited
from gameplay import teleport,eventually
from bukkit_transactions import use_originals
import protection
NAMES=["VolumeA","VolumeB"]
def observer(tmp,cache):
    cls=tmp/"classes";cls.mkdir()
    cp=[*(ROOT/"standalone/vendor/bukkit").glob("*.jar"),*(cache/"libraries").rglob("guava-*.jar")]
    subprocess.run(["javac","-proc:none","--release","25","-g:none","-cp",os.pathsep.join(map(str,cp)),"-d",cls,HERE/"Beta25Probe.java"],check=True)
    target=tmp/"probe.jar"
    with zipfile.ZipFile(target,"w") as z:
        for f in sorted(cls.rglob("*.class")):z.writestr(zipfile.ZipInfo(f.relative_to(cls).as_posix()),f.read_bytes())
        z.writestr(zipfile.ZipInfo("plugin.yml"),'name: Beta25Probe\napi-version: "26.2"\nversion: 1\nmain: qa.beta25.Beta25Probe\ncommands:\n  v25: {}\npermissions:\n  unityprobe.op:\n    default: op\n')
    return target
def read(file,phase):
    return [l.split("\t")[1:] for l in file.read_text("utf8").splitlines() if l.startswith(phase+"\t")]
def mark(s,phase):s.send("v25 "+phase,r"BETA25 "+phase+" phase")
def event(file,phase,key,last):
    return eventually(lambda:read(file,phase),lambda rows:any(k==key and v.endswith(last) for k,v in rows),18)
def block(s,ws,x,y,z,id,props=""):
    cond="minecraft:"+id+("["+props+"]" if props else "")
    observed(s,f"block {x} {y} {z} {cond}","BLOCK_OK_"+id,20)
    v=[eventually(lambda:w.act("qaBlock",x=x,y=y,z=z),lambda b:b["id"]=="minecraft:"+id and (not props or props in b["state"]))["state"] for w in ws]
    return v
def strip(s):return re.sub(r"\x1b\[[0-9;]*m","",s)
def health(s,ws,n,expected):
    raw=strip(s.send("data get entity "+n+" Health","entity data:"))
    authoritative=float(re.search(r"entity data: ([0-9.]+)f",raw)[1])
    assert authoritative==expected,(n,raw,expected)
    return [authoritative,*[eventually(lambda:w.act("qaPlayer",name=n),lambda v:v["health"]==expected)["health"] for w in ws]]
def bags(s,ws):
    state={}
    for n,w in zip(NAMES,ws):
        bag=w.act("qaPlayer")["bag"]
        for index,entry in bag.items():
            ident,count=entry.split("*");count=count.split("@")[0]
            observed(s,f'data entity {n} Inventory[{{Slot:{index}b,id:"{ident}",count:{count}}}]',"MATCH_BAG_"+str(index),12)
        raw=strip(s.send("data get entity "+n+" Inventory","entity data:"))
        assert len(re.findall(r"\bSlot: -?\d+b",raw))==len(bag),(bag,raw)
        state[n]=bag
    return state
def prepare(s):
    s.send("forceload add 0 0 47 15");observed(s,"loaded 34 80 7","V25_AREA",60)
    for cmd in ["gamerule minecraft:natural_health_regeneration false","gamerule minecraft:keep_inventory true",
                "gamerule minecraft:spawn_mobs false","time set noon","fill 2 79 3 45 79 14 stone",
                "fill 2 80 3 45 86 14 air","setblock 8 80 7 dispenser[facing=south]",
                "fill 7 80 8 9 80 9 stone","setblock 8 80 8 air","setblock 12 80 7 cauldron",
                "fill 15 80 6 17 80 8 stone","setblock 16 80 7 oak_slab[type=bottom,waterlogged=false]"]:
        s.send(cmd)
def setup_game(o,root,reference,probe):
    c=o.cache;g=root/"server";players=[root/n for n in NAMES];pl=g/"plugins";pl.mkdir(parents=True)
    shutil.copy2(probe,pl)
    (pl/"bStats").mkdir();(pl/"bStats/config.yml").write_text("enabled: false\n")
    for game in players:
        native=game/"unity-mods";native.mkdir(parents=True);shutil.copy2(o.dist/"unity-client-witness.jar",native)
        (game/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:3\nmaxFps:30\nfullscreen:false\ntutorialStep:none\n")
    corpus=protection.mixed(o,c,g,players) if not reference and not o.plain else {}
    rows=[]
    if o.regions:
        rows=[r for r in json.loads((HERE/"protection-originals.json").read_bytes()) if r["slug"] in ("worldedit","worldguard")]
        if getattr(o,"edit_version",None):
            rows=[o.edit_version if r["slug"]=="worldedit" else r for r in rows]
        for row in rows:
            origin=o.alternates if row["slug"]=="worldedit" and getattr(o,"edit_version",None) else o.authors
            use_originals([row],origin,pl)
        guard=pl/"WorldGuard/worlds/world";guard.mkdir(parents=True)
        (guard/"regions.yml").write_text("regions:\n  protected:\n    min: {x: 0, y: 60, z: 0}\n    max: {x: 15, y: 110, z: 15}\n    members: {}\n    owners: {}\n    flags: {build: deny, pvp: deny}\n    type: cuboid\n    priority: 10\n")
    with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
    (g/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nlevel-seed=2525\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\npause-when-empty-seconds=-1\n")
    shutil.copy2(o.eula,g/"eula.txt")
    return g,players,port,corpus,rows
def held(s,w,n,i,item,count=1):
    slot(w,i,"minecraft:"+item,count);observed(s,f'data entity {n} Inventory[{{Slot:{i}b,id:"minecraft:{item}",count:{count}}}]',"HELD_"+item)
def use(w,i,x,z,block_use=False):
    if block_use:return w.act("qaRight",slot=i,x=x,y=80,z=z)
    return w.act("qaProtectedUse",slot=i,x=x+.5,y=80.22,z=z+.5)
def core(s,ws,file,first):
    a,b=ws;A,B=NAMES;result={}
    if first:
        s.send("spawnpoint VolumeA 5 80 6");s.send("damage VolumeA 1000 minecraft:generic_kill")
        eventually(lambda:a.act("qaPlayer"),lambda p:p["dead"]);a.act("qaRespawn");eventually(lambda:a.act("qaPlayer"),lambda p:not p["dead"])
        teleport(s,a,A,7.5,80,5.5)
        s.send("item replace entity VolumeA hotbar.1 with iron_ingot 6")
        s.send("experience set VolumeA 11 levels")
        for op in [True,False]:
            s.send(("op " if op else "deop ")+A);phase="respawn-"+str(op).lower();mark(s,phase)
            s.send("v25 view","BETA25 "+phase+" view")
            row=read(file,phase)[-1][1];assert row==f"true true true true 0.0 20.0 11 {str(op).lower()} {str(op).lower()} IRON_INGOT/6",row
        s.send("v25 write","BETA25 respawn-false write");held(s,a,A,1,"iron_ingot",4)
        teleport(s,a,A,8.5,80,5.4);teleport(s,b,B,10.5,80,6.6)
        s.send("item replace block 8 80 7 container.0 with water_bucket")
        for w in ws:w.act("menuOpen",handSlot=0,x=8,y=80,z=7)
        result["dispenser"]={}
        for fluid in ["water","lava"]:
            full=fluid+"_bucket"
            s.send("item replace block 8 80 7 container.0 with "+full)
            for suffix,world,item in [("pour-dispense-deny","air",full),("pour-dispense-ok",fluid,"bucket"),
                    ("take-dispense-deny",fluid,"bucket"),("take-dispense-ok","air",full)]:
                phase=fluid+"-"+suffix
                mark(s,phase);s.send("setblock 8 81 7 redstone_block")
                event(file,phase,"dispense.end",str(phase.endswith("deny")).lower())
                block(s,ws,8,80,8,world)
                items=[eventually(lambda:w.act("menuInfo"),lambda m:m["slots"][0]["id"]=="minecraft:"+item)["slots"][0]["id"] for w in ws]
                observed(s,f'data block 8 80 7 Items[{{Slot:0b,id:"minecraft:{item}",count:1}}]',"DISP_INV")
                result["dispenser"][phase]=[world,items,read(file,phase)]
                s.send("setblock 8 81 7 air");observed(s,"block 8 80 7 dispenser[triggered=false]","UNPOWERED")
        for w in ws:w.act("menuClose")
        teleport(s,a,A,12.5,80,4.8);s.send("item replace entity VolumeA hotbar.2 with water_bucket")
        for phase,m,kind,item,after in [("pot-bucket-deny",True,"cauldron","water_bucket",""),("pot-level-deny",True,"cauldron","water_bucket",""),
                ("pot-empty-ok",False,"water_cauldron","bucket","level=3"),("pot-fill-level-deny",True,"water_cauldron","bucket","level=3"),
                ("pot-fill-ok",False,"cauldron","water_bucket",""),("alter-level",False,"water_cauldron","bucket","level=2")]:
            mark(s,phase);use(a,2,12,7,True)
            event(file,phase,"bucket.end",str(phase=="pot-bucket-deny").lower())
            if phase!="pot-bucket-deny":event(file,phase,"cauldron.end",str(m).lower())
            else:assert not any(k.startswith("cauldron.") for k,_ in read(file,phase))
            block(s,ws,12,80,7,kind,after);held(s,a,A,2,item)
        # The accepted replacement above leaves two levels. Bottle actions have their own reason and no bucket callback.
        s.send("item replace entity VolumeA hotbar.5 with glass_bottle")
        for phase,level,item,reason in [("bottle-take-level-deny",2,"glass_bottle","BOTTLE_FILL"),
                ("bottle-take-ok",1,"potion","BOTTLE_FILL"),("bottle-pour-level-deny",1,"potion","BOTTLE_EMPTY"),
                ("bottle-pour-ok",2,"glass_bottle","BOTTLE_EMPTY")]:
            mark(s,phase);use(a,5,12,7,True);event(file,phase,"cauldron.end",str(phase.endswith("deny")).lower())
            assert all(reason in v for k,v in read(file,phase) if k.startswith("cauldron."))
            assert not any(k.startswith("bucket.") for k,_ in read(file,phase))
            block(s,ws,12,80,7,"water_cauldron","level="+str(level));held(s,a,A,5,item)
        teleport(s,b,B,16.5,81,6.5);s.send("item replace entity VolumeB hotbar.2 with water_bucket")
        for phase,wet,item in [("slab-empty-bucket-deny",False,"water_bucket"),("slab-empty-ok",True,"bucket"),("slab-fill-bucket-deny",True,"bucket"),("slab-fill-ok",False,"water_bucket"),("slab-saved",True,"bucket")]:
            mark(s,phase);use(b,2,16,7);event(file,phase,"bucket.end",str(phase.endswith("deny")).lower())
            block(s,ws,16,80,7,"oak_slab","waterlogged="+str(wet).lower());held(s,b,B,2,item)
        s.send("item replace entity VolumeA hotbar.3 with crossbow[minecraft:charged_projectiles=[{id:'minecraft:arrow',count:1}],minecraft:enchantments={'minecraft:piercing':2}]")
        teleport(s,a,A,20.5,80,12.5);teleport(s,b,B,25.5,80,12.5)
        s.send('summon cow 28.5 80 12.5 {NoAI:1b,PersistenceRequired:1b,Tags:["v25dummy"]}')
        eventually(lambda:a.act("qaPlayer",name=B),lambda p:abs(p["x"]-25.5)<.25 and abs(p["z"]-12.5)<.3)
        s.send("execute as VolumeA at @s run tp @s ~ ~ ~ facing entity VolumeB eyes","Teleported")
        held(s,a,A,3,"crossbow")
        s.send("data get entity VolumeA Rotation","entity data:")
        mark(s,"pierce-deny");a.act("qaProtectedUse",slot=3,name=B)
        event(file,"pierce-deny","hit","PLAYER true");event(file,"pierce-deny","damage","COW false")
        assert not any(k=="damage" and " PLAYER " in v for k,v in read(file,"pierce-deny"))
        health(s,ws,B,20)
        assert re.search(r"entity data: 7(?:\.0)?f",strip(s.send("data get entity @e[tag=v25dummy,limit=1] Health","entity data:")))
        s.send("tp @e[tag=v25dummy] 38.5 80 13.5")
        def clock():return int(re.search(r"time is (\d+)",s.send("time query gametime",r"time is \d+"))[1])
        start=clock();eventually(clock,lambda t:t>=start+22)
        s.send("item replace entity VolumeA hotbar.3 with crossbow[minecraft:charged_projectiles=[{id:'minecraft:arrow',count:1}],minecraft:enchantments={'minecraft:piercing':2}]")
        s.send('summon cow 28.5 80 12.5 {NoAI:1b,PersistenceRequired:1b,Tags:["v25dummy2"]}')
        time.sleep(.35)
        mark(s,"pierce-ok");a.act("qaProtectedUse",slot=3,name=B)
        event(file,"pierce-ok","damage","PLAYER false");event(file,"pierce-ok","damage","COW false")
        assert re.search(r"entity data: 7(?:\.0)?f",strip(s.send("data get entity @e[tag=v25dummy2,limit=1] Health","entity data:")))
        for phase in ["pierce-deny","pierce-ok"]:
            hits=[v for k,v in read(file,phase) if k=="hit"]
            assert len(hits)==2 and "PLAYER" in hits[0] and "COW" in hits[1],hits
    result["blocks"]=[block(s,ws,12,80,7,"water_cauldron","level=2"),block(s,ws,16,80,7,"oak_slab","waterlogged=true"),block(s,ws,8,80,8,"air")]
    observed(s,'data block 8 80 7 Items[{Slot:0b,id:"minecraft:lava_bucket",count:1}]',"DISP_SAVED")
    if not first:
        teleport(s,a,A,8.5,80,5.4);teleport(s,b,B,10.5,80,6.6)
        for w in ws:
            view=w.act("menuOpen",handSlot=0,x=8,y=80,z=7)
            assert view["slots"][0]["id"]=="minecraft:lava_bucket" and view["slots"][0]["count"]==1
            w.act("menuClose")
    result["health"]=[health(s,ws,A,20),health(s,ws,B,17)]
    result["bags"]=bags(s,ws)
    return result

def regions(s,ws,file,first):
    a,b=ws;A,B=NAMES;result={}
    if first:
        info=strip(s.send("rg info -w world protected",r"Region: .*protected"))
        assert "priority=" in info and "cuboid" in info,info
        for x,denied in [(8,True),(34,False)]:
            phase="region-in" if denied else "region-out"
            s.send(f"fill {x-1} 80 6 {x+3} 80 8 stone");s.send(f"setblock {x} 80 7 air");s.send(f"setblock {x+2} 80 7 water")
            s.send(f"item replace entity {A} hotbar.2 with water_bucket");teleport(s,a,A,x+.5,81,6.5)
            mark(s,phase+"-bucket");a.act("qaProtectedUse",slot=2,x=x+.5,y=79.98,z=7.5)
            event(file,phase+"-bucket","bucket.end",str(denied).lower())
            block(s,ws,x,80,7,"air" if denied else "water");held(s,a,A,2,"water_bucket" if denied else "bucket")
            s.send(f"item replace entity {B} hotbar.2 with bucket");teleport(s,b,B,x+2.5,81,6.5)
            mark(s,phase+"-fill");use(b,2,x+2,7)
            event(file,phase+"-fill","bucket.end",str(denied).lower())
            block(s,ws,x+2,80,7,"water" if denied else "air");held(s,b,B,2,"bucket" if denied else "water_bucket")
            teleport(s,a,A,x-2.5,80,12.5);teleport(s,b,B,x+3.5,80,12.5)
            s.send(f"item replace entity {A} hotbar.3 with bow");s.send(f"item replace entity {A} hotbar.4 with arrow 8")
            eventually(lambda:a.act("qaPlayer",name=B),lambda p:abs(p["x"]-(x+3.5))<.25 and abs(p["z"]-12.5)<.3)
            mark(s,phase+"-arrow");a.act("qaProtectedBow",slot=3,name=B)
            event(file,phase+"-arrow","damage",str(denied).lower())
            health(s,ws,B,20 if denied else 17)
        teleport(s,a,A,19.5,80,12.5);teleport(s,b,B,13.5,80,12.5)
        eventually(lambda:a.act("qaPlayer",name=B),lambda v:abs(v["x"]-13.5)<.25)
        mark(s,"region-border-arrow");a.act("qaProtectedBow",slot=3,name=B)
        event(file,"region-border-arrow","damage","true")
        health(s,ws,B,17)
    result["blocks"]=[block(s,ws,8,80,7,"air"),block(s,ws,10,80,7,"water"),block(s,ws,34,80,7,"water"),block(s,ws,36,80,7,"air")]
    result["health"]=[health(s,ws,A,20),health(s,ws,B,17)]
    result["bags"]=bags(s,ws)
    return result

def run(o):
    c=o.cache;bundle=c/"mojang-server-26.2.jar";loader=o.dist/("unity-loader-"+getattr(o,"version","0.2.5-Beta")+".jar")
    ref=o.host=="paper";out=o.out/o.host;out.mkdir(parents=True,exist_ok=True);acceptance.EVIDENCE=content.EVIDENCE=out
    assert "eula=true" in o.eula.read_text() and digest(o.paper)=="0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="beta25-") as temp:
        tmp=Path(temp);fixture=observer(tmp,c);g,games,port,corpus,rows=setup_game(o,tmp,ref,fixture)
        cmd=["java","-Xmx2g","-XX:ActiveProcessorCount=4","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Dmc.unity.contentQA=true"]
        if ref:
            (g/"cache").mkdir();shutil.copy2(bundle,g/"cache/mojang_26.2.jar")
            cmd+=["-Dterminal.jline=false","-Dterminal.ansi=false","-jar",str(o.paper),"--nogui"]
        else:cmd+=["-jar",str(loader),"--server-jar",str(bundle),"--runtime-dir",str(c),"nogui"]
        record={"plugin":digest(fixture),"runtime":digest(o.paper if ref else loader),"client":digest(loader),"witness":digest(o.dist/"unity-client-witness.jar"),"mixed":corpus,"authors":{r["slug"]:r["sha512"] for r in rows},"runs":[]}
        if getattr(o, 'configure', None): o.configure(g)
        file=g/"plugins/Beta25Probe/events.tsv"
        for phase in ["first","restart"]:
            with contextlib.ExitStack() as cleanup:
                srv=Server(g,phase,loader,bundle,c,cmd);cleanup.callback(srv.close)
                srv.expect(r"Done \([0-9.,]+s\)!" if ref else "Standalone ready",180)
                if phase=="first":prepare(srv)
                ws=[];ids=[]
                for game,name in zip(games,NAMES):
                    w=Window(c/"client-home",game,phase+"-"+name,port,loader,name);cleanup.callback(exited,w);ws.append(w)
                    state=w.act("status",timeout=180);assert state["gpu"] and state["remote"] and not state["visible"];ids.append(state["uuid"])
                try:
                    data=(regions if o.regions else core)(srv,ws,file,phase=="first");data["players"]=ids
                finally:
                    if file.exists():shutil.copy2(file,out/"events.tsv")
                for w in ws:exited(w)
                srv.send("save-all flush","Saved the game",180)
            text=(out/(phase+".log")).read_text("utf8",errors="replace")
            assert not any(err in text for err in ["failed handling ","Could not pass event","Could not dispatch event:","UnsupportedCall:","Could not register flag","Exception in thread"]),phase
            record["runs"].append(data)
        for key in ["players","blocks","bags","health"]:assert record["runs"][0][key]==record["runs"][1][key],key
        for row in rows:assert digest(g/"plugins"/row["filename"],"sha512")==row["sha512"],row["filename"]
        for game in [g,*games]:
            for filename,h in corpus.get(game.name,{}).items():assert digest(game/"mods"/filename,"sha512")==h
        if getattr(o,"check_installed",None): o.check_installed(g)
        record["logs"]={f.name:digest(f) for f in [*out.glob("*.log"),out/"events.tsv"]}
        (out/"result.json").write_text(json.dumps(record,indent=2),"utf8")
        print("PASS",o.host,"region" if o.regions else "core")
if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--host",choices=["paper","unity"],required=True);p.add_argument("--regions",action="store_true");p.add_argument("--plain",action="store_true")
    for k in ("dist","paper","authors","eula","samples","out"):p.add_argument("--"+k,type=lambda s:Path(s).resolve(),required=True)
    p.add_argument("--cache",type=lambda s:Path(s).resolve(),help="26.2 SDK cache containing Mojang bundle, client-home and locked corpus")
    options=p.parse_args()
    if not options.cache:
        options.cache=next(path for path in [ROOT/"cache/0.2.5-Beta",ROOT/"cache/0.2.4-Beta"] if (path/"client-home").is_dir())
    run(options)
