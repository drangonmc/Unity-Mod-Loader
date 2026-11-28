"""Paper control and independent Unity shutdown/restore cycles in a loopback-only test world."""
import argparse,contextlib,hashlib,json,os,re,shutil,subprocess,sys,threading,time,zipfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path[:0]=[str(HERE.parent/"review31"),str(HERE.parent/"review30"),str(HERE.parent),str(HERE.parent.parent)]
import social,beta25 as qa,acceptance,content
from gameplay import eventually
from acceptance import Server,digest
from content import Window,observed
from dual import exited

def install(o,g,games):
    expected=json.loads(o.baseline.read_bytes());pl=g/"plugins";pins=dict(expected["plugins"])
    for name,h in pins.items():
        f=o.original_matrix/"server/plugins"/name;assert digest(f,"sha512")==h;shutil.copy2(f,pl)
    for row in json.loads((HERE/"originals.json").read_bytes()):
        f=o.authors/row["filename"];assert digest(f,"sha512")==row["sha512"];shutil.copy2(f,pl);pins[f.name]=row["sha512"]
    for name in ("CommandBlocker","MuteChat","EasyChatFilter"):
        src=o.original_matrix/"server/plugins"/name
        if src.is_dir():shutil.copytree(src,pl/name,dirs_exist_ok=True)
    sort=pl/"NeatSort";sort.mkdir(exist_ok=True)
    sortjar=next(o.authors.glob("neatsort-*.jar"))
    with zipfile.ZipFile(sortjar) as z:(sort/"config.yml").write_bytes(z.read("config.yml").replace(b"containers-default: false",b"containers-default: true"))
    spawn=pl/"LSpawn";spawn.mkdir(exist_ok=True)
    (spawn/"config.yml").write_text('language: en_us\nspawn:\n  enabled: true\n  world: world\n  x: 62.5\n  y: 80.0\n  z: 20.5\n  yaw: 0.0\n  pitch: 0.0\nfirst-spawn:\n  enabled: false\nrespawn:\n  at-spawn: true\n  at-bed: false\n  at-anchor: false\njoin:\n  teleport-to-spawn: false\n',"utf8")
    import run as builders
    orig=builders.HERE;builders.HERE=HERE
    try:builders.fixture(["LifeProbe.java","Extras.java"],[*sorted((qa.ROOT/"standalone/vendor/bukkit").glob("*.jar")),*sorted((o.cache/"libraries").rglob("*.jar"))],
        "plugin.yml",'name: Unity32Observer\napi-version: "26.2"\nmain: qa.unity32.LifeProbe\nversion: 1\ncommands:\n  u32status: {}\n  u32world: {}\npermissions:\n  unity32.chest:\n    default: false\n',pl/"Unity32Observer.jar")
    finally:builders.HERE=orig
    if o.host=="unity":
        shutil.copy2(o.dist/"UnityEditRegions.jar",pl)
        for d in [g,*games]:
            src=o.original_matrix/d.name/"mods";mods=d/"mods";mods.mkdir(exist_ok=True)
            for name,h in expected["matrix"][d.name].items():
                assert digest(src/name,"sha512")==h;shutil.copy2(src/name,mods)
            jar,=src.glob("fabric-api-[0-9]*.jar")
            if not (mods/jar.name).exists():shutil.copy2(jar,mods)
        native=g/"mods/unity32sync.jar";prior=builders.HERE;builders.HERE=HERE
        try:
            fixture=builders.fixture
            fixture("SyncProbe.java",[*(o.sdk/n for n in ("neo.jar","neo-bus.jar","neo-loader.jar","dist.jar")),o.dist/"unity-loader-0.3.2-Beta.jar",o.cache/"versions/26.2/server-26.2.jar",*sorted((o.cache/"libraries").rglob("*.jar"))],
                "META-INF/neoforge.mods.toml",'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="unity32sync"\nversion="1"\ndisplayName="Attachment witness"\n',native)
            for client in games:shutil.copy2(native,client/"mods")
        finally:builders.HERE=prior
        (g/"config").mkdir(exist_ok=True);(g/"config/keep_but_penalty.toml").write_text("[death]\nkeepInventory=false\n","utf8")
    if o.host=="unity":
        import language
        pin=json.loads((HERE/"fabric-kotlin.json").read_bytes());fabric=o.language/pin["filename"];assert digest(fabric,"sha512")==pin["sha512"]
        kt=g/"mods/unity32kotlin.jar"
        language.compile_probe(o.kotlin_compiler,fabric,o.sdk/"fabric-loader.jar",g.parent,kt)
        kffpin=json.loads((HERE/"kotlin-for-forge.json").read_bytes());kff=o.language/kffpin["filename"];assert digest(kff,"sha512")==kffpin["sha512"]
        machinejar=g/"mods/unity32kff.jar";language.compile_kff(o.kotlin_compiler,kff,o.sdk,g.parent,machinejar)
        for folder in [g,*games]:
            shutil.copy2(kff,folder/"mods")
            if folder!=g:shutil.copy2(machinejar,folder/"mods")
            expected["matrix"][folder.name][kff.name]=kffpin["sha512"]
            shutil.copy2(fabric,folder/"mods")
            if folder!=g:shutil.copy2(kt,folder/"mods")
            expected["matrix"][folder.name][fabric.name]=pin["sha512"]
        region=pl/"WorldGuard/worlds/world";region.mkdir(parents=True,exist_ok=True)
        (region/"regions.yml").write_text("regions:\n  machine32:\n    min: {x: 71, y: 78, z: 28}\n    max: {x: 73, y: 82, z: 30}\n    members: {}\n    owners: {unique-ids: [d72a93df-334e-34e6-81f8-67ddcdeb22db]}\n    flags: {}\n    type: cuboid\n    priority: 20\n","utf8")
    return pins,digest(pl/"Unity32Observer.jar"),expected["matrix"] if o.host=="unity" else {}

def timed_stop(server,folder,phase):
    if server.process.poll() is not None:raise RuntimeError("Server exited before stop")
    start=time.monotonic();snapshots=[]
    def watch():
        for threshold in (35,90,145,170):
            try:server.process.wait(timeout=max(.05,threshold-(time.monotonic()-start)));return
            except subprocess.TimeoutExpired:pass
            out=subprocess.run(["jcmd",str(server.process.pid),"Thread.print"],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=8).stdout
            path=folder/f"{phase}-shutdown-{threshold}.txt";path.write_bytes(out)
            lines=out.decode("utf8","replace").splitlines()
            snapshots.append({"afterSeconds":threshold,"nonDaemon":[l.split('"')[1] for l in lines if l.startswith('"') and ' #' in l and " daemon " not in l],"sha256":digest(path)})
    worker=threading.Thread(target=watch,daemon=True);worker.start()
    server.send("stop")
    try:server.process.wait(timeout=180)
    except subprocess.TimeoutExpired:
        server.process.kill();server.process.wait(15)
        server.thread.join(5);server.log.close();raise
    elapsed=round(time.monotonic()-start,3)
    server.close();worker.join(9)
    assert elapsed<180 and server.process.returncode==0,(elapsed,server.process.returncode)
    return dict(phase=phase,seconds=elapsed,exitCode=server.process.returncode,diagnostics=snapshots)

def main(o):
    d=o.out;d.mkdir(parents=True);qa.acceptance.EVIDENCE=content.EVIDENCE=d;inst=d/"instance";inst.mkdir()
    o.regions=False;o.plain=True
    g,games,port,_,_=qa.setup_game(o,inst,True,qa.observer(inst,o.cache))
    pins,witness,matrix=install(o,g,games)
    engine=o.dist/"unity-loader-0.3.2-Beta.jar";bundle=o.cache/"mojang-server-26.2.jar"
    paper=o.host=="paper"
    if paper:(g/"cache").mkdir();shutil.copy2(bundle,g/"cache/mojang_26.2.jar")
    os.environ["PAPER_DEFAULT_CENTRAL_REPOSITORY"]="https://repo.maven.apache.org/maven2/"
    cmd=["java","-Xmx2g","-XX:ActiveProcessorCount=4","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Dterminal.jline=false","-Dterminal.ansi=false","-Dmc.unity.contentQA=true","-Dmc.unity.shutdownDiagnostics=true","-jar",str(o.paper if paper else engine)]
    cmd+=["--nogui"] if paper else ["--server-jar",str(bundle),"--runtime-dir",str(o.cache),"nogui"]
    runs=[];stops=[];sessions={};prefix="minecraft:" if paper else ""
    for idx in range(1 if o.pilot else (2 if paper else 4)):
        phase=["first","restart","third","fourth"][idx]
        s=Server(g,phase,engine,bundle,o.cache,cmd)
        try:
            s.expect(r"Done \([0-9.,]+s\)!" if paper else "Standalone ready",135)
            if o.pilot:continue
            if idx==0:
                s.send("forceload add 48 16 79 31");observed(s,"loaded 64 80 22","U32_READY",50)
                for c in ["fill 58 79 16 79 79 30 stone","fill 58 80 16 79 85 30 air","fill 64 80 25 70 83 30 glass hollow","setblock 65 80 23 chest","item replace block 65 80 23 container.0 with iron_ingot 6","item replace block 65 80 23 container.8 with iron_ingot 7","gamerule minecraft:keep_inventory false","gamerule minecraft:spawn_mobs false"]:s.send(prefix+c)
            with contextlib.ExitStack() as done:
                ws=[];sessions[phase]=[]
                for gd,n in zip(games,qa.NAMES):
                    w=Window(o.cache/"client-home",gd,phase+n,port,engine,n);done.callback(exited,w);ws.append(w)
                    st=w.act("status",timeout=180);assert st["remote"] and st["gpu"] and not st["visible"];sessions[phase].append(dict(name=n,pid=w.process.pid,visible=False))
                a,b=ws
                def tp(w,n,x,z):
                    s.send(prefix+f"tp {n} {x} 80 {z} 0 0","Teleported");eventually(lambda:w.act("qaPlayer"),lambda v:abs(v["x"]-x)<.09 and abs(v["z"]-z)<.09)
                def contents(w,n):
                    return eventually(lambda:w.act("menuInfo")["slots"],lambda cells:any(v["id"]=="minecraft:iron_ingot" and v["count"]==n for v in cells))
                def chest(n):observed(s,'data block 65 80 23 {Items:[{Slot:0b,id:"minecraft:iron_ingot",count:'+str(n)+'}]}',"U32_CHEST_"+str(n))
                if idx==0:
                    for w,n,x in [(a,"VolumeA",65.5),(b,"VolumeB",67.5)]:tp(w,n,x,26.5)
                    for n,slot,item,count in [("VolumeA",3,"gold_ingot",9),("VolumeB",2,"emerald",7)]:
                        s.send(prefix+"clear "+n);s.send(prefix+f"item replace entity {n} hotbar.{slot} with {item} {count}","Replaced")
                    s.send("lp user VolumeA permission set playerkeepinventory.use true");time.sleep(.5)
                    a.act("qaCommand",cmd="keepinventory true");time.sleep(.5)
                    for w,n,keep in [(a,"VolumeA",True),(b,"VolumeB",False)]:
                        s.send(prefix+"damage "+n+" 100 minecraft:generic_kill")
                        s.expect("U32 death-monitor "+n+" keep="+str(keep).lower(),20)
                        eventually(lambda:w.act("qaPlayer"),lambda v:v["dead"]);w.act("qaRespawn")
                        s.expect("U32 respawn-monitor "+n+" 62 20",18)
                        eventually(lambda:w.act("qaPlayer"),lambda v:not v["dead"] and abs(v["x"]-62.5)<.09)
                        if n=="VolumeA":
                            a.act("qaWalk");s.expect("U32 first-step 62 20",10);social.views(s,ws,n,[62.5,80,20.5])
                            a.act("qaCommand",cmd="u32status release");s.expect("U32 permission VolumeA",10)
                            tp(a,n,60.5,18.5)
                    observed(s,'entity @e[type=item,x=67,y=80,z=26,distance=..6,nbt={Item:{id:"minecraft:diamond",count:3}}]',"U32_REPLACED_DROP")
                    for kind in ("gold_ingot","emerald"):
                        s.send('execute unless entity @e[type=item,x=66,y=80,z=26,distance=..5,nbt={Item:{id:"minecraft:'+kind+'"}}] run say NO_'+kind,r"\[Server\] NO_"+kind)
                    qa.held(s,a,"VolumeA",3,"gold_ingot",9)
                    a.act("qaChat",text="u32-console-check");s.expect("U32 console-removed true",15)
                    s.send(prefix+"item replace entity VolumeA hotbar.6 with ender_pearl","Replaced");qa.held(s,a,"VolumeA",6,"ender_pearl",1)
                    a.act("qaProtectedUse",slot=6,x=62.5,y=79.6,z=20.5);s.expect("U32 cause ENDER_PEARL",15);social.views(s,ws,"VolumeA",[60.5,80,18.5])
                    s.send(prefix+"item replace entity VolumeA hotbar.6 with chorus_fruit","Replaced");qa.held(s,a,"VolumeA",6,"chorus_fruit",1)
                    a.act("qaEat",slot=6);s.expect("U32 cause CONSUMABLE_EFFECT",15);social.views(s,ws,"VolumeA",[60.5,80,18.5])
                    s.send(prefix+"item replace entity VolumeA hotbar.6 with air")
                    s.send(prefix+"fill 75 79 18 78 83 18 obsidian")
                    s.send(prefix+"fill 76 80 18 77 82 18 air");s.send(prefix+"setblock 76 80 18 fire")
                    observed(s,"block 76 80 18 nether_portal","U32_PORTAL_READY")
                    if not paper:
                        for rule in ("disable_nether","disable_end","disable_gateway"):
                            s.send("gamerule minecraft:"+rule+" false",r"false")
                        s.send("item replace entity VolumeA hotbar.5 with glowstone 8","Replaced")
                        s.send("item replace entity VolumeA hotbar.8 with end_crystal 8","Replaced")
                    s.send(prefix+"gamemode creative VolumeA")
                    for cause,x,z,block in [("NETHER_PORTAL",76,18,None),("END_PORTAL",77,20,"end_portal"),
                                             ("END_GATEWAY",76,22,"end_gateway{Age:400L,exit_portal:[I;60,80,18],ExactTeleport:1b}")]:
                        if block:s.send(prefix+f"setblock {x} 80 {z} "+block)
                        a.act("qaCommand",cmd="u32status cooldown");s.expect("U32 cooldown reset",10)
                        s.send(prefix+f"tp VolumeA {x+.5} 80 {z+.5} 0 0","Teleported")
                        s.expect("U32 cause "+cause,45)
                        # Dismantle the portal first so a second contact on this tick cannot race cleanup.
                        s.send(prefix+f"setblock {x} 80 {z} air")
                        s.send("execute in minecraft:overworld run "+prefix+"tp VolumeA 60.5 80 18.5 0 0","Teleported")
                        time.sleep(.25)
                        s.send("execute in minecraft:overworld run "+prefix+"tp VolumeA 60.5 80 18.5 0 0","Teleported")
                        eventually(lambda:a.act("qaPlayer"),lambda v:abs(v["x"]-60.5)<.07)
                    if not paper:
                        s.send("item replace entity VolumeA hotbar.5 with air");s.send("item replace entity VolumeA hotbar.8 with air")
                        for rule in ("disable_nether","disable_end","disable_gateway"):s.send("gamerule minecraft:"+rule+" true",r"true")
                    s.send(prefix+"gamemode spectator VolumeA")
                    s.send(prefix+"spectate VolumeB VolumeA");s.expect("U32 cause SPECTATE",15)
                    s.send(prefix+"gamemode survival VolumeA")
                    tp(a,"VolumeA",60.5,18.5)
                    a.act("qaCommand",cmd="u32world");s.expect("U32 world-meta ok",15)
                    observed(s,"block 71 80 23 oak_stairs[facing=east]","U32_WORLD_WRITE")
                    s.send(prefix+'summon tnt 74.5 80.5 23.5 {fuse:5}');s.expect("U32 entity-blast filtered",15)
                    observed(s,"block 74 80 23 dirt","U32_EXPLOSION_CONTROL")
                    assert "unity32-completion" in a.act("qaCompletions",command="u32world u")["choices"]
                    tp(a,"VolumeA",65.5,20.5);tp(b,"VolumeB",66.5,20.5)
                    s.send("lp user VolumeA permission set neatsort.use true");time.sleep(.5)
                    a.act("menuOpen",handSlot=0,x=65,y=80,z=23);contents(a,6);a.act("menuClose")
                    chest(13);a.act("menuOpen",handSlot=0,x=65,y=80,z=23);contents(a,13)
                    b.act("qaRight",slot=0,x=65,y=80,z=23);s.expect("U32 open-monitor VolumeB true",15)
                    assert b.act("qaMenuClosed")["closed"];chest(13);contents(a,13)
                    bags=qa.bags(s,ws);assert bags["VolumeB"]=={} and bags["VolumeA"]["3"].startswith("minecraft:gold_ingot*9")
                    s.send("lp user VolumeB permission set unity32.chest true");time.sleep(.8)
                    b.act("qaCommand",cmd="u32status");s.expect("U32 permission VolumeB true",10)
                    b.act("menuOpen",handSlot=0,x=65,y=80,z=23);contents(b,13)
                    b.act("menuClick",index=0,button=1);b.act("menuClick",index=0,inventory=4,button=0)
                    contents(a,6);contents(b,6);chest(6);b.act("menuClose");a.act("menuClose")
                    tp(a,"VolumeA",64.5,20.5);tp(b,"VolumeB",67.5,20.5)
                else:
                    for w,n,x in [(a,"VolumeA",64.5),(b,"VolumeB",67.5)]:social.views(s,ws,n,[x,80,20.5])
                    for w in ws:w.act("menuOpen",handSlot=0,x=65,y=80,z=23);contents(w,6)
                    for w in ws:w.act("menuClose")
                if not paper:
                    if idx==0:
                        for tag in ("u32abs","u32parry"):
                            s.send('summon cow 73 80 26 {NoAI:1b,Tags:["'+tag+'"]}',"Summoned")
                            if tag=="u32abs":s.send("effect give @e[tag=u32abs] minecraft:absorption 60 0 true","Applied")
                            s.send("damage @e[tag="+tag+",limit=1] 3 minecraft:generic")
                            s.expect("U32ABS 3.0 0.0 3.0" if tag=="u32abs" else "U32PARRY unarmed 3",12)
                            observed(s,"entity @e[tag="+tag+",nbt={Health:10.0f}]","U32_INTACT_"+tag)
                        tp(a,"VolumeA",69.5,21.5)
                        s.send("item replace entity VolumeA hotbar.7 with copperhopper:copper_hopper","Replaced")
                        ca=eventually(lambda:a.act("qaPlayer")["bag"],lambda bag:"copperhopper:copper_hopper" in bag.get("7",""))
                        a.act("qaRight",slot=7,x=69,y=79,z=24,top=True);observed(s,"block 69 80 24 copperhopper:copper_hopper","U32_MACHINE_PLACED")
                        tp(a,"VolumeA",64.5,20.5)
                        s.send("u32data write","U32SYNC server write 3201 3202 3211")
                        for w,private in [(a,"3211"),(b,"null")]:eventually(lambda:w.act("qaAttachmentView"),lambda v:v["values"]=="3201 3202 "+private)
                        s.send("u32data remove","U32SYNC server remove 3201 3202 null")
                    s.send("u32mine","U32MACHINE owner VolumeA allowed; VolumeB denied",16)
                    s.send("u32data check","U32SYNC server check 3201 3202 null")
                    for w in ws:eventually(lambda:w.act("qaAttachmentView"),lambda v:v["values"]=="3201 3202 null")
                chest(6);bags=qa.bags(s,ws)
                assert bags["VolumeA"]=={"3":"minecraft:gold_ingot*9@0"} and bags["VolumeB"]=={"4":"minecraft:iron_ingot*7@0"},bags
                runs.append(dict(bags=bags,chest=6,positions={n:social.views(s,ws,n,[x,80,20.5]) for n,x in zip(qa.NAMES,[64.5,67.5])}))
            s.send("save-all flush","Saved the game",110)
        except BaseException:
            import traceback
            (d/(phase+"-failure.txt")).write_text(traceback.format_exc(),"utf8")
            raise
        finally:stops.append(timed_stop(s,d,phase) if s.process.poll() is None else (s.close() or {}))
    if o.pilot:return
    assert all(v==runs[0] for v in runs)
    assert all(digest(g/"plugins"/n,"sha512")==h for n,h in pins.items())
    for side,files in matrix.items():assert all(digest(inst/side/"mods"/n,"sha512")==h for n,h in files.items())
    cp=[o.cache/"versions/26.2/server-26.2.jar",*sorted((o.cache/"libraries").rglob("*.jar"))];cp=os.pathsep.join(map(str,cp));cls=d/"nbt-reader";cls.mkdir()
    subprocess.run(["javac","--release","25","-proc:none","-cp",cp,"-d",cls,HERE/"SavedChest.java",HERE.parent/"review30/SavedWorld.java",HERE.parent/"review31/SavedPlayers.java"],check=True)
    for reader,file in (("SavedPlayers","players"),("SavedChest","world")):
        subprocess.run(["java","-cp",str(cls)+os.pathsep+cp,reader,g/"world",d/(file+".json")],check=True)
    disk=json.loads((d/"world.json").read_bytes());assert disk["chest"]=={"0":"minecraft:iron_ingot*6"} and disk["state"]=="minecraft:chest",disk
    if not paper:assert (disk["playerAttachment"],disk["chunkAttachment"])==(3201,3202),disk
    players=json.loads((d/"players.json").read_bytes())
    for n in qa.NAMES:
        assert players[n]["position"]==runs[-1]["positions"][n]
        assert players[n]["bag"]=={k:v.split("@",1)[0] for k,v in runs[-1]["bags"][n].items()}
    result=dict(engine=digest(engine),clientWitness=digest(o.dist/"unity-client-witness.jar"),host=o.host,paper=digest(o.paper),witness=witness,plugins=pins,matrix=matrix,runs=runs,stops=stops,sessions=sessions,logs={f.name:digest(f) for f in d.glob("*.log")})
    (d/"result.json").write_text(json.dumps(result,indent=2),"utf8")
if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--host",required=True,choices=["unity","paper"]);p.add_argument("--pilot",action="store_true")
    for k in ["dist","cache","eula","paper","baseline","original_matrix","authors","out"]:p.add_argument("--"+k,type=lambda s:Path(s).resolve(),required=True)
    for k in ("sdk","language","kotlin_compiler"):p.add_argument("--"+k,type=lambda s:Path(s).resolve())
    options=p.parse_args()
    if options.host=="unity" and any(getattr(options,k) is None for k in ("sdk","language","kotlin_compiler")):
        p.error("Unity's SDK witnesses require --sdk, --language and --kotlin_compiler")
    main(options)
