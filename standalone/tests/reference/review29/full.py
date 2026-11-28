"""Two independent hidden game clients, mixed author JARs and a clean restart of the same world."""
import argparse,contextlib,hashlib,json,os,re,shutil,socket,sys,time
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path[:0]=[str(HERE.parent),str(HERE.parent.parent)]
import beta25 as base
import beta26
import acceptance,content
from acceptance import Server,digest
from content import Window,observed
from gameplay import teleport,eventually
from verify25 import split_snbt
from dual import exited
import services
import machines
from plugin import build as plugin_build

def line(s,cmd,needle):
    return base.strip(s.send(cmd,needle,25))

def state(s,ws,x,z,kind):
    observed(s,f"block {x} 80 {z} {kind}","BLOCK29_OK")
    views=[eventually(lambda:w.act("qaTransferState",x=x,y=80,z=z),lambda v:v["state"].startswith(kind)) for w in ws]
    return views

def blockdata(s,x,z):
    return line(s,f"data get block {x} 80 {z}","block data:").split("block data: ",1)[1]

def check_quantity(s,x,z,key,n):
    raw=line(s,f"data get block {x} 80 {z} {key}","block data:")
    result=int(re.search(r'block data: (-?\d+)',raw)[1]);assert result==n,raw
    return result

def setup_machines(s):
    s.send("forceload add 16 0 47 15");observed(s,"loaded 24 80 8","AREA29_READY",60)
    for command in ("fill 18 79 3 43 79 14 stone","fill 18 80 6 43 84 13 air",
                    "setblock 24 80 8 irontanks:iron_tank","setblock 26 80 8 irontanks:iron_tank",
                    "setblock 24 80 10 copperhopper:copper_hopper[facing=north]","setblock 26 80 10 chest[facing=east]",
                    "item replace block 24 80 10 container.0 with emerald[minecraft:custom_data={unity29:1}] 12",
                    "setblock 24 80 12 logisticsdrones:charge_station[facing=east]","setblock 26 80 12 logisticsdrones:charge_station[facing=north]"):
        s.send(command)
    for z,kind in [(8,"fluid"),(10,"item"),(12,"energy")]:
        s.send(f"setblock 25 80 {z} tinypipes:pipe_block")
        line(s,"unity29setup "+kind,"UNITY29_PIPE_READY "+kind)
    for _ in range(12):
        status=[blockdata(s,x,z) for z in (8,10,12) for x in (24,25,26)]
        if re.search(r"Amount: 7250[Ll]",status[2]) and re.search(r"Energy: 12345",status[8]):break
        s.send("tick sprint 200t")
        time.sleep(1)
    observed(s,'data block 26 80 8 {Amount:7250L}',"FLUID_MOVED",45)
    observed(s,'data block 26 80 12 {Energy:12345}',"ENERGY_MOVED",45)
    observed(s,'data block 26 80 10 Items[{id:"minecraft:emerald"}]',"ITEM_MOVED",40)

def verify(s,ws,first):
    a,b=ws
    if first:
        s.send("gamerule minecraft:natural_health_regeneration false")
    for w,n,dx in zip(ws,base.NAMES,(0,1)):teleport(s,w,n,26.5+dx,80,5.5)
    if first:
        s.send("lp user VolumeA permission set unity29.confirm true")
        s.send("lp user VolumeB permission set unity29.confirm false")
        time.sleep(2)
    for w,n in zip(ws,base.NAMES):
        phase="write" if first else "read"
        w.act("qaCommand",cmd="u29 "+phase)
        s.expect("UNITY29 "+phase+" "+n+" permission=LuckPerms economy=EssentialsX Economy balance="+str(291 if n=="VolumeA" else 292),25)
        if first:
            eventually(lambda:w.act("menuInfo"),lambda m:any(c["id"]=="minecraft:gold_ingot" and c["count"]==2 for c in m["slots"]))
            w.act("menuClose")
        expected=17 if n=="VolumeA" else 15
        base.health(s,ws,n,expected)
    home=services.homes(s,ws[0],first)
    # Copy the destination column (tank, chest and energy cell) plus the configured pipes.
    if first:
        for node in beta26.PERMISSIONS:
            s.send("lp user VolumeA permission set "+node+" true")
        # Roundtrip a separate grant before sending privileged editor commands.
        s.send("lp user VolumeA permission set unity29.confirm true")
        time.sleep(1)
        check_quantity(s,24,8,"Amount",0);check_quantity(s,26,8,"Amount",7250)
        check_quantity(s,24,12,"Energy",0);check_quantity(s,26,12,"Energy",12345)
        teleport(s,a,base.NAMES[0],26.5,80,5.5)
        for i,x,z in [(1,25,8),(2,26,12)]:
            beta26.command(a,f"/pos{i} {x},80,{z}",r"position")
        beta26.command(a,"/copy",r"copied|clipboard|blocks? affected")
        teleport(s,a,base.NAMES[0],36.5,80,5.5)
        beta26.command(a,"/paste",r"pasted|clipboard|blocks? affected")
        check_quantity(s,36,8,"Amount",7250);check_quantity(s,36,12,"Energy",12345)
        beta26.command(a,"/undo",r"undo|undid")
        for z in (8,10,12):base.block(s,ws,36,80,z,"air")
        beta26.command(a,"/redo",r"redo|redid")
    record={}
    ledger=[]
    for pos in (24,26):
        raw=blockdata(s,pos,10)
        ledger.append(sum(int(n) for n in re.findall(r"count: (\d+)",raw)))
    assert ledger==[1,11],ledger
    for x in (26,36):
        volume=check_quantity(s,x,8,"Amount",7250);charge=check_quantity(s,x,12,"Energy",12345)
        tank=blockdata(s,x,8);assert "minecraft:water" in tank,tank
        fluid=state(s,ws,x,8,"irontanks:iron_tank")
        chest=state(s,ws,x,10,"minecraft:chest")
        assert all("facing=east" in c["state"] for c in chest)
        cell=state(s,ws,x,12,"logisticsdrones:charge_station")
        assert all("facing=north" in c["state"] for c in cell),cell
        assert all("7250" in c["nbt"] and "minecraft:water" in c["nbt"] for c in fluid),fluid
        pipe_rows=[];displays=[]
        for z in (8,10,12):
            pipe=blockdata(s,x-1,z)
            views=state(s,ws,x-1,z,"tinypipes:pipe_block")
            def faces(text):
                compact=text.replace(chr(92),"").replace(chr(34),"").replace(chr(39),"").replace(" ","")
                return re.search(r"sideStatus:\{(?=[^}]*WEST:PULLING)(?=[^}]*EAST:ENABLED)",compact) is not None
            assert faces(pipe),pipe
            assert all(faces(v["nbt"]) for v in views),views
            pipe_rows.append([kind for kind in ("ItemPipe","EnergyPipe","FluidPipe") if kind in pipe])
        raw=blockdata(s,x,10)
        assert "unity29" in raw
        for w,n in zip(ws,base.NAMES):
            teleport(s,w,n,x+.5,80,7.6)
            try:
                view=w.act("menuOpen",handSlot=0,x=x,y=80,z=10)
                cells=eventually(lambda:w.act("menuInfo")["slots"],lambda v:any(c["id"]=="minecraft:emerald" for c in v))
                emerald=next(c for c in cells if c["id"]=="minecraft:emerald")
                assert emerald["count"]==ledger[1] and "unity29" in emerald["custom"],emerald
            finally:w.act("menuClose")
        station=blockdata(s,x,12)
        assert "machine_serial: 29012" in station and "minecraft:paper" in station,station
        for w,n in zip(ws,base.NAMES):
            teleport(s,w,n,x+0.5,80,14.6)
            try:
                w.act("menuOpen",handSlot=0,x=x,y=80,z=12)
                screen=eventually(lambda:w.act("menuInfo"),lambda v:v.get("energy")==12345 and any(
                    c.get("count")==7 and "machine_serial" in c.get("custom","") for c in v["slots"]))
                assert screen["screen"].endswith(".ChargeStationScreen"),screen["screen"]
                stack=next(slot for slot in screen["slots"] if slot.get("count")==7 and "machine_serial" in slot.get("custom",""))
                displays.append({"player":n,"screen":screen["screen"],"energy":screen["energy"],"stack":stack["id"],"count":stack["count"],"component":stack["custom"]})
            finally:w.act("menuClose")
        record[str(x)]={"fluid":volume,"fluidId":"minecraft:water","energy":charge,"stationSerial":29012,
                        "facing":cell[0]["state"],"pipeFaces":"west pulls/east sends","pipes":pipe_rows,
                        "chestCount":ledger[1],"sourceCount":ledger[0],"clientViews":displays}
    assert record["26"]==record["36"]
    record["essentialsHome"]=home
    return record

def main(o):
    stage=o.dist;loader=stage/"unity-loader-0.2.9-Beta.jar";bundle=o.cache/"mojang-server-26.2.jar"
    dst=o.out;dst.mkdir(parents=True,exist_ok=True);base.acceptance.EVIDENCE=content.EVIDENCE=dst
    o.regions=True;o.plain=False
    game_root=dst/"instance";game_root.mkdir()
    probe=beta26.observer(game_root,o.cache)
    g,games,port,corpus,rows=base.setup_game(o,game_root,False,probe)
    shutil.copy2(o.region_bridge,g/"plugins")
    for p in o.plugins.glob("*.jar"):shutil.copy2(p,g/"plugins")
    vault=next(o.plugins.glob("Vault*.jar"));plugin_build(base.ROOT,vault,o.cache,g/"plugins/Unity29Probe.jar")
    oldpins=json.loads((base.ROOT/"standalone/tests/reference/machines-26.2.json").read_bytes())
    for root in [g,*games]:
        for row in oldpins:
            original=o.old_machines/row["filename"];assert digest(original,"sha512")==row["sha512"]
            shutil.copy2(original,root/"mods");corpus[root.name][row["filename"]]=row["sha512"]
        for slug in ('iron-tanks','tiny-pipes','logistics-drones'):
            spec=json.loads((o.machines/(slug+'.json')).read_bytes())
            f=o.machines/spec['filename'];assert digest(f,'sha512')==spec['sha512']
            shutil.copy2(f,root/'mods');corpus[root.name][f.name]=spec['sha512']
    (g/'unity-mods').mkdir(exist_ok=True)
    machines.build(base.ROOT,o.machines,o.sdk,o.cache,loader,g/'unity-mods/transfer-setup.jar')
    os.environ['PAPER_DEFAULT_CENTRAL_REPOSITORY']='https://repo.maven.apache.org/maven2/'
    cmd=['java','-Xmx2g','-XX:ActiveProcessorCount=4','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Dmc.unity.contentQA=true','-jar',str(loader),'--server-jar',str(bundle),'--runtime-dir',str(o.cache),'nogui']
    if os.environ.get('UNITY29_RECORD_THREADS'):cmd.insert(1,'-XX:StartFlightRecording=name=threadStart,settings=profile,filename=thread-starts.jfr,duration=75s')
    runs=[]
    for phase in ("first","restart"):
        with contextlib.ExitStack() as done:
            server=Server(g,phase,loader,bundle,o.cache,cmd);done.callback(server.close);server.expect("Standalone ready",150)
            if phase=="first":setup_machines(server)
            ws=[]
            for d,n in zip(games,base.NAMES):
                w=Window(o.cache/'client-home',d,phase+'-'+n,port,loader,n);done.callback(exited,w);ws.append(w)
                result=w.act('status',timeout=160);assert result['remote'] and result['gpu'] and not result['visible']
            try:runs.append(verify(server,ws,phase=="first"))
            except Exception:
                import traceback
                (dst/(phase+"-failure.txt")).write_text(traceback.format_exc(),"utf8")
                raise
            for w in ws:exited(w)
            server.send("save-all flush","Saved the game",120)
    assert runs[0]==runs[1],runs
    rawplugins={p.name:digest(p,"sha512") for p in o.plugins.glob("*.jar")}
    for name,h in rawplugins.items():assert digest(g/"plugins"/name,"sha512")==h
    for directory in [g,*games]:
        for name,h in corpus[directory.name].items():assert digest(directory/"mods"/name,"sha512")==h
    for phase in ("first","restart"):
        output=(dst/(phase+".log")).read_text("utf8",errors="replace")
        assert "[Unity] Standalone stopped" in output and all("Enabled "+n in output for n in ("Essentials","Vault","LuckPerms","WorldEdit","WorldGuard"))
        for msg in ("UNITY29_ACTUAL_FAILURE","Views$UnsupportedCall:","Could not pass event","failed handling ","Unable to inject LuckPerms","Failed to initialize provider","NoSuchMethodError:","NoClassDefFoundError:"):
            assert msg not in output,(phase,msg)

    (dst/'result.json').write_text(json.dumps({"loader":digest(loader),"runs":runs,"authors":corpus,"probe":digest(g/"plugins/Unity29Probe.jar"),"plugins":rawplugins,"worldEditGuard":{r["slug"]:r["sha512"] for r in rows},"clientWitness":digest(stage/"unity-client-witness.jar"),"logs":{f.name:digest(f) for f in dst.glob("*.log")}},indent=2),'utf8')
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for key in ('dist','cache','authors','eula','out','samples','plugins','machines','sdk','region_bridge','old_machines'):
        p.add_argument('--'+key,type=lambda s:Path(s).resolve(),required=True)
    main(p.parse_args())
