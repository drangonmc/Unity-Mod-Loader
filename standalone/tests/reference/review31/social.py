"""Run the same original plugins, witness, and real clients against Paper and Unity."""
import argparse,contextlib,json,os,re,shutil,socket,subprocess,sys,time
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path[:0]=[str(HERE.parent/"review30"),str(HERE.parent),str(HERE.parent.parent)]
from run import fixture
import beta25 as qa,acceptance,content
from acceptance import Server,digest
from gameplay import teleport,eventually
from content import Window,observed
from dual import exited

def command(w,cmd):w.act("qaCommand",cmd=cmd)
def views(s,ws,name,xyz):
    row=qa.strip(s.send("data get entity "+name+" Pos","entity data:"))
    coords=[float(x) for x in re.findall(r"(-?[0-9.]+)d",row.split("entity data: ")[1])]
    def same(pos):return len(pos)==3 and all(abs(a-b)<.06 for a,b in zip(pos,xyz))
    assert same(coords),(name,coords,xyz)
    for w in ws:eventually(lambda:[(p:=w.act("qaPlayer",name=name))["x"],p["y"],p["z"]],same,12)
    return [round(x,3) for x in coords]

def install(o,g,clients):
    plugins=g/"plugins";rows=json.loads((HERE/"originals.json").read_bytes());installed={}
    for original in [*o.plugins.glob("*.jar"),o.guard/"007-worldedit-bukkit-7.4.6.jar",o.guard/"022-worldguard-bukkit-7.0.19.jar"]:
        shutil.copy2(original,plugins);installed[original.name]=digest(original,"sha512")
    for row in rows:
        f=o.authors/row["filename"];assert digest(f,"sha512")==row["sha512"];shutil.copy2(f,plugins);installed[f.name]=row["sha512"]
    for name,cfg in {"CommandBlocker":"blocked-commands: [u31forbid]\nblocked-command-message: 'U31 author blocked'\n",
                     "MuteChat":"hide:\n  chat: true\n  join-messages: false\n  leave-messages: false\n  death-messages: false\n  advancement-messages: false\nremind-on-join: false\n"}.items():
        dest=plugins/name;dest.mkdir(exist_ok=True);(dest/"config.yml").write_text(cfg,"utf8")
    region=plugins/"WorldGuard/worlds/world";region.mkdir(parents=True,exist_ok=True)
    (region/"regions.yml").write_text("regions:\n  guard31:\n    min: {x: 73, y: 60, z: 17}\n    max: {x: 76, y: 100, z: 23}\n    members: {}\n    owners: {}\n    flags: {entry: deny}\n    type: cuboid\n    priority: 10\n","utf8")
    filters=plugins/"EasyChatFilter";filters.mkdir(exist_ok=True)
    (filters/"words.yml").write_text("words:\n  - {word: orangecactus, level: TVMA}\n","utf8")
    witness=plugins/"Unity31Observer.jar"
    fixture_path=HERE.parent/"review30"
    import run as builders
    prior=builders.HERE;builders.HERE=HERE
    try:
        fixture("SocialProbe.java",[*sorted((qa.ROOT/"standalone/vendor/bukkit").glob("*.jar")),*sorted((o.cache/"libraries").rglob("*.jar"))],
                "plugin.yml",'name: Unity31Observer\napi-version: "26.2"\nmain: qa.unity31.SocialProbe\nversion: 1\ncommands:\n'+''.join("  "+n+": {}\n" for n in ("u31permit","u31forbid","u31tp","u31alias","u31hold","u31status")),witness)
    finally:builders.HERE=prior
    if o.host=="unity":
        shutil.copy2(o.dist/"UnityEditRegions.jar",plugins)
        config=g/"config";config.mkdir(exist_ok=True)
        (config/"keep_but_penalty.toml").write_text("[death]\nkeepInventory = false\n","utf8")
        matrix=json.loads(o.baseline.read_bytes())["authors"]
        for directory in [g,*clients]:
            target=directory/"mods";target.mkdir(exist_ok=True)
            src=o.original_matrix/directory.name/"mods"
            for n,h in matrix[directory.name].items():
                assert digest(src/n,"sha512")==h,n
                shutil.copy2(src/n,target/n)
            api,=src.glob("fabric-api-[0-9]*.jar")
            if not (target/api.name).exists():shutil.copy2(api,target)
        libs=list((o.cache/"libraries").rglob("*.jar"));game=o.cache/"versions/26.2/server-26.2.jar"
        import run as builders
        prior=builders.HERE;builders.HERE=HERE
        try:
            for name,mod,loader in (("NeoReview","unity31review","neoforge"),("ForgeReview","unity31forgereview","forge")):
                test=g/"mods"/(mod+".jar")
                cp=([o.sdk/n for n in ("neo.jar","neo-bus.jar","neo-loader.jar","dist.jar")] if loader=="neoforge" else [o.dist/"unity-loader-0.3.1-Beta.jar"])
                desc="META-INF/neoforge.mods.toml" if loader=="neoforge" else "META-INF/mods.toml"
                fixture(name+".java",[*cp,game,*libs],desc,'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="'+mod+'"\nversion="1"\ndisplayName="Unity QA"\n',test)
                for client in clients:shutil.copy2(test,client/"mods")
        finally:builders.HERE=prior
        return installed,digest(witness),matrix
    return installed,digest(witness),{}

def run(o):
    d=o.out;d.mkdir(parents=True);qa.acceptance.EVIDENCE=content.EVIDENCE=d
    o.regions=False;o.plain=True
    inst=d/"instance";inst.mkdir();empty=qa.observer(inst,o.cache)
    g,games,port,_,_=qa.setup_game(o,inst,True,empty)
    plugins,witness,matrix=install(o,g,games)
    loader=o.dist/"unity-loader-0.3.1-Beta.jar";bundle=o.cache/"mojang-server-26.2.jar"
    if o.host=="paper":
        (g/"cache").mkdir();shutil.copy2(bundle,g/"cache/mojang_26.2.jar")
    os.environ["PAPER_DEFAULT_CENTRAL_REPOSITORY"]="https://repo.maven.apache.org/maven2/"
    cmd=["java","-Xmx2g","-XX:ActiveProcessorCount=4","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Dterminal.jline=false","-Dterminal.ansi=false",
         "-Dmc.unity.contentQA=true","-jar",str(o.paper if o.host=="paper" else loader)]
    cmd+=(["--nogui"] if o.host=="paper" else ["--server-jar",str(bundle),"--runtime-dir",str(o.cache),"nogui"])
    tp="minecraft:tp" if o.host=="paper" else "tp"
    item="minecraft:item" if o.host=="paper" else "item"
    states=[];sessions={};review=None
    for phase in ("first","restart"):
        with contextlib.ExitStack() as done:
            s=Server(g,phase,loader,bundle,o.cache,cmd);done.callback(s.close)
            if os.environ.get("UNITY31_SHUTDOWN_DIAGNOSTICS") and o.host=="unity":
                import threading
                def inspect_exit(proc,logfile,label):
                    while proc.poll() is None:
                        if "[Unity] Standalone stopped" in logfile.read_text("utf8",errors="replace"):break
                        time.sleep(1)
                    for attempt in range(2):
                        try:proc.wait(timeout=4 if attempt==0 else 20);return
                        except subprocess.TimeoutExpired:pass
                        raw=subprocess.run(["jcmd",str(proc.pid),"Thread.print"],capture_output=True,timeout=12).stdout
                        text=raw.decode("utf8",errors="replace").replace("\r\n","\n")
                        (d/(label+"-threads-"+str(attempt)+".txt")).write_text(text,"utf8")
                threading.Thread(target=inspect_exit,args=(s.process,d/(phase+".log"),phase),daemon=True).start()
            s.expect(r"Done \([0-9.,]+s\)!" if o.host=="paper" else "Standalone ready",160)
            if phase=="first":
                s.send("forceload add 48 16 79 47");observed(s,"loaded 64 80 20","U31_LOADED",50)
                for c in ["fill 55 79 10 78 79 40 stone","fill 55 80 10 78 85 40 air","gamerule minecraft:spawn_mobs false","gamerule minecraft:natural_health_regeneration false"]:s.send(c)
            ws=[];sessions[phase]=[]
            for folder,name in zip(games,qa.NAMES):
                w=Window(o.cache/"client-home",folder,phase+name,port,loader,name);done.callback(exited,w)
                st=w.act("status",timeout=180);assert st["remote"] and st["gpu"] and not st["visible"]
                ws.append(w);sessions[phase].append(dict(name=name,pid=w.process.pid,visible=st["visible"],remote=True))
            def failure(kind,exc,tb):
                if exc is not None:
                    import traceback
                    (d/(phase+"-failure.txt")).write_text("".join(traceback.format_exception(kind,exc,tb)),"utf8")
                return False
            done.push(failure)
            a,b=ws
            def marker(pattern):s.expect("U31 "+pattern,20)
            if phase=="first":
                for w,n,x in zip(ws,qa.NAMES,(64.5,66.5)):
                    s.send(f"{tp} {n} {x} 80 20.5 0 0","Teleported");eventually(lambda:w.act("qaPlayer"),lambda p:abs(p["x"]-x)<.04)
                if o.host=="unity":
                    from damage import review as damage_review
                    review=damage_review(s,ws)
                for name in qa.NAMES:s.send(("minecraft:clear" if o.host=="paper" else "clear")+" "+name)
                s.send(item+" replace entity VolumeA hotbar.3 with emerald 9","Replaced");s.send(item+" replace entity VolumeB hotbar.2 with bread 7","Replaced")
                def chat(w,t):w.act("qaChat",text=t)
                def lines(w):return [x for x in Path(w.log.name).read_text("utf8",errors="replace").splitlines() if "[CHAT]" in x]
                def seen(w,t):eventually(lambda:lines(w),lambda logs:any(t in l for l in logs),16)
                chat(a,"u31filter orangecactus");seen(b,"u31filter ###")
                chat(a,"u31cancelchat");marker("monitor-u31cancelchat true")
                chat(a,"u31barrierone");seen(b,"u31barrierone")
                assert not any("u31cancelchat" in l for l in lines(b))
                command(b,"mutechat");time.sleep(.7)
                chat(a,"u31hidden");marker("monitor-u31hidden false");seen(a,"u31hidden")
                command(b,"mutechat");time.sleep(.7)
                chat(a,"u31barriertwo");seen(b,"u31barriertwo")
                assert not any("u31hidden" in l for l in lines(b))
                command(a,"u31forbid");marker("cmd-u31forbid true")
                s.send("u31status","U31 marks 0")
                command(a,"u31alias");marker("executed-u31permit 1")
                s.send("u31hold true","U31 hold true");a.act("qaWalk")
                views(s,ws,"VolumeA",[64.5,80,20.5])
                s.send("u31hold false","U31 hold false");v=a.act("qaWalk");assert v["distance"]>1.0,v
                s.send(tp+" VolumeA 64.5 80 20.5 0 0","Teleported");eventually(lambda:a.act("qaPlayer"),lambda p:abs(p["z"]-20.5)<.03)
                s.send(tp+" VolumeA 70.5 80 20.5");marker("tp-cause COMMAND");marker("tp-monitor true")
                views(s,ws,"VolumeA",[64.5,80,20.5])
                command(a,"u31tp");marker("tp-cause PLUGIN");marker("plugin-teleport false")
                s.send(tp+" VolumeA 74.5 80 20.5");marker("region-tp true")
                views(s,ws,"VolumeA",[64.5,80,20.5])
                s.send(tp+" VolumeA 74.5 80 14.5 0 0","Teleported");eventually(lambda:a.act("qaPlayer"),lambda p:abs(p["z"]-14.5)<.1)
                walk=a.act("qaWalk");assert 14.5<walk["endZ"]<17,(o.host,walk)
                s.send(tp+" VolumeA 64.5 80 20.5 0 0","Teleported")
                eventually(lambda:a.act("qaPlayer"),lambda p:abs(p["x"]-64.5)<.05)
            row={n:views(s,ws,n,[x,80,20.5]) for n,x in zip(qa.NAMES,(64.5,66.5))}
            row["bags"]=qa.bags(s,ws)
            assert row["bags"]["VolumeA"]["3"].split("@")[0]=="minecraft:emerald*9"
            assert row["bags"]["VolumeB"]["2"].split("@")[0]=="minecraft:bread*7"
            assert set(row["bags"]["VolumeA"])=={"3"} and set(row["bags"]["VolumeB"])=={"2"},row["bags"]
            states.append(row)
            for w in ws:exited(w)
            s.send("save-all flush","Saved the game",120)
    assert states[0]==states[1]
    log=(d/"first.log").read_text("utf8",errors="replace")
    order=["U31 legacy-u31cancelchat true","U31 early-u31cancelchat false","U31 cancel-chat true","U31 monitor-u31cancelchat true"]
    assert [log.index(t) for t in order]==sorted(log.index(t) for t in order)
    assert "U31 uncancelled-u31cancelchat" not in log and "U31 executed-u31forbid" not in log and "U31 move-cancel true" in log
    assert all(digest(g/"plugins"/n,"sha512")==h for n,h in plugins.items())
    for folder,archives in matrix.items():
        assert all(digest(inst/folder/"mods"/name,"sha512")==pin for name,pin in archives.items())
    cp=[o.cache/"versions/26.2/server-26.2.jar",*sorted((o.cache/"libraries").rglob("*.jar"))]
    subprocess.run(["java","-cp",os.pathsep.join(map(str,cp)),HERE/"SavedPlayers.java",g/"world",d/"offline.json"],check=True)
    disk=json.loads((d/"offline.json").read_bytes())
    for name in qa.NAMES:
        assert disk[name]["position"]==states[-1][name]
        assert disk[name]["bag"]=={k:v.split("@",1)[0] for k,v in states[-1]["bags"][name].items()}
    (d/"result.json").write_text(json.dumps(dict(host=o.host,engine=digest(loader),paper=digest(o.paper),plugins=plugins,witness=witness,
        clientWitness=digest(o.dist/"unity-client-witness.jar"),matrix=matrix,review=review,states=states,clientSessions=sessions,chatOrder=order,
        logs={f.name:digest(f) for f in d.glob("*.log")}),indent=2),"utf8")
if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--host",choices=("paper","unity"),required=True)
    for key in ("out","dist","cache","eula","plugins","guard","authors","baseline","original_matrix","paper","sdk"):p.add_argument("--"+key,required=True,type=lambda v:Path(v).resolve())
    run(p.parse_args())
