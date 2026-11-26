"""Two actual clients, one Paper-compiled observer and original author plugins, in disposable worlds."""
import argparse,contextlib,json,os,re,shutil,socket,subprocess,sys,tempfile,time,zipfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent))
import acceptance,content
from acceptance import ROOT,VERSION,Server,digest
from content import Window,observed,slot
from gameplay import eventually,teleport
from dual import exited
from bukkit_transactions import use_originals
NAMES=["ShieldA","ShieldB"]
def plugin(folder):
    classes=folder/"classes";classes.mkdir()
    cp=[*(ROOT/"standalone/vendor/bukkit").glob("*.jar"),*(ROOT/"cache"/VERSION/"libraries").rglob("guava-*.jar")]
    subprocess.run(["javac","-proc:none","--release","25","-g:none","-cp",os.pathsep.join(map(str,cp)),"-d",classes,HERE/"ProtectionProbe.java"],check=True)
    file=folder/"protection-probe.jar"
    with zipfile.ZipFile(file,"w") as z:
        for f in sorted(classes.rglob("*.class")):z.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix()),f.read_bytes())
        z.writestr(zipfile.ZipInfo("plugin.yml"),'name: ProtectionProbe\nversion: 1\napi-version: "26.2"\nmain: qa.protection.ProtectionProbe\nsoftdepend: [AntiLavaCast, SkippyPearls, LetMeDespawn]\ncommands:\n  shieldphase: {}\n')
    return file
def records(file,phase):
    if not file.exists():return []
    return [s.split("\t")[1:] for s in file.read_text("utf8").splitlines() if s.startswith(phase+"\t")]
def waitrow(file,phase,key,end):
    def poll():return records(file,phase)
    return eventually(poll,lambda rows:any(k==key and v.endswith(end) for k,v in rows),20)
def compare_health(s,ws,target,value):
    raw=re.sub(r"\x1b\[[0-9;]*m","",s.send("data get entity "+target+" Health",r"entity data:"))
    got=float(re.search(r"entity data: ([0-9.]+)f",raw)[1])
    vals=[eventually(lambda:w.act("qaPlayer",name=target),lambda p:p["health"]==value)["health"] for w in ws]
    assert got==value,(got,vals)
    return [got,*vals]
def block(s,ws,x,y,z,name):
    observed(s,f"block {x} {y} {z} minecraft:{name}","AUTH_"+name,20)
    seen=[eventually(lambda:w.act("qaBlock",x=x,y=y,z=z),lambda state:state["id"]=="minecraft:"+name)["id"] for w in ws]
    return [name,*seen]
def stacks(s,ws):
    result={}
    for n,w in zip(NAMES,ws):
        raw=re.sub(r"\x1b\[[0-9;]*m","",s.send("data get entity "+n+" Inventory",r"entity data:"))
        expected={}
        for entry in re.findall(r'\{(?:[^{}]|\{[^{}]*\})*\}',raw):
            index=re.search(r"Slot:\s*(-?\d+)b",entry);item=re.search(r'id:\s*"([^"]+)"',entry);count=re.search(r"count:\s*(\d+)",entry)
            if not (index and item and count):continue
            damage=re.search(r'"minecraft:damage":\s*(\d+)',entry)
            expected[index[1]]=f"{item[1]}*{count[1]}@{damage[1] if damage else 0}"
        bag=eventually(lambda:w.act("qaPlayer")["bag"],lambda value:value==expected)
        result[n]=bag
    return result
def setup(s):
    s.send("forceload add 0 0 31 15");observed(s,"loaded 18 80 8","READY_ARENA",60)
    for cmd in ["gamerule minecraft:natural_health_regeneration false","gamerule minecraft:spawn_mobs false","time set noon",
                "fill 3 79 3 13 79 13 stone","fill 3 80 3 13 84 13 air","fill 7 80 7 13 80 9 stone",
                "setblock 8 80 8 air","setblock 10 80 8 water","setblock 12 80 8 air","fill 17 79 7 19 79 8 stone",
                "fill 17 80 7 19 84 8 air","fill 4 79 10 27 79 14 stone","fill 4 80 10 27 84 14 air"]:
        s.send(cmd)
def mixed(o,cache,server,games):
    corpus=json.loads((ROOT/"standalone/performance-pack.json").read_bytes())["files"]
    extra=json.loads((ROOT/"standalone/universal-samples.json").read_bytes())+[json.loads((ROOT/"standalone/forge-key-sample.json").read_bytes())]
    hashes={}
    for g in [server,*games]:
        mods=g/"mods";mods.mkdir()
        folder=cache/("performance-samples" if g==server else "performance-client-samples")
        rows=[r for r in corpus if (folder/r["filename"]).is_file()]
        assert len(rows)==(73 if g==server else 71)
        use_originals(rows,folder,mods);use_originals(extra,o.samples,mods)
        shutil.copy2(next((ROOT/"standalone/vendor").glob("fabric-api-*.jar")),mods)
        hashes[g.name]={r["filename"]:r["sha512"] for r in rows+extra}
    return hashes

def run(which,o,folder,probe):
    c=ROOT/"cache"/VERSION;home=c/"client-home";bundle=c/"mojang-server-26.2.jar";loader=o.dist/f"unity-loader-{getattr(o,'version',VERSION)}.jar"
    out=o.out/which;out.mkdir(parents=True,exist_ok=True);acceptance.EVIDENCE=content.EVIDENCE=out
    server=folder/"server";games=[folder/n for n in NAMES];pl=server/"plugins";pl.mkdir(parents=True)
    authors=[r for r in json.loads((HERE/"protection-originals.json").read_bytes()) if r["slug"] not in ("worldedit","worldguard")]
    use_originals(authors,o.authors,pl);shutil.copy2(probe,pl)
    (pl/"bStats").mkdir();(pl/"bStats/config.yml").write_text("enabled: false\n")
    (pl/"AntiLavaCast").mkdir();(pl/"AntiLavaCast/config.yml").write_text("block-air-place: false\nwater:\n  potential-drop: 3\nlava:\n  potential-drop: 3\n")
    (pl/"SkippyPearls").mkdir();(pl/"SkippyPearls/config.yml").write_text("effects:\n  sound:\n    enabled: false\n  particles:\n    enabled: false\n")
    for g in games:
        (g/"unity-mods").mkdir(parents=True);shutil.copy2(o.dist/"unity-client-witness.jar",g/"unity-mods")
        (g/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:3\nfullscreen:false\nmaxFps:30\ntutorialStep:none\n")
    ispaper=which=="paper"
    pack=mixed(o,c,server,games) if not ispaper and not o.plain else {}
    assert "eula=true" in o.eula.read_text();shutil.copy2(o.eula,server/"eula.txt")
    with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
    (server/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nlevel-seed=824\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\npause-when-empty-seconds=-1\n")
    cmd=["java","-XX:ActiveProcessorCount=4","-Xmx2g","-Dmc.unity.contentQA=true","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-jar",str(loader),"--server-jar",str(bundle),"--runtime-dir",str(c),"nogui"]
    if ispaper:
        assert digest(o.paper)=="0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
        (server/"cache").mkdir();shutil.copy2(bundle,server/"cache/mojang_26.2.jar")
        cmd=["java","-XX:ActiveProcessorCount=4","-Xmx2g","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Dterminal.jline=false","-jar",str(o.paper),"--nogui"]
    report={"runtime":digest(o.paper if ispaper else loader),"client":digest(loader),"witness":digest(o.dist/"unity-client-witness.jar"),"plugin":digest(probe),"authors":{r["slug"]:r["sha512"] for r in authors},"mixed":pack,"runs":[]}
    events=pl/"ProtectionProbe/observed.tsv";previous=0
    for phase in ["first","restart"]:
        detail={};log=out/(phase+".log")
        with contextlib.ExitStack() as close:
            s=Server(server,phase,loader,bundle,c,command=cmd);close.callback(s.close)
            s.expect(r"Done \([0-9.,]+s\)!" if ispaper else "Standalone ready",190)
            if phase=="first":setup(s)
            ws=[]
            for g,n in zip(games,NAMES):
                w=Window(home,g,phase+"-"+n,port,loader,n);close.callback(exited,w);ws.append(w)
                st=w.act("status",timeout=180);assert st["remote"] and st["gpu"] and not st["visible"]
            a,b=ws
            joins=[row for row in records(events,"join") if row[0]=="join"][previous:];assert joins==[["join",n+" true true"] for n in NAMES],joins;previous+=2
            eventually(lambda:s.send("shieldphase census",r"PROTECT census \d+"),lambda line:line.endswith(" 2"),25)
            def mode(m):s.send("shieldphase "+m,"PROTECT "+m)
            def action(m,w,op,**args):mode(m);return w.act(op,**args)
            def checkbag(n,w,i,id,count):
                slot(w,i,"minecraft:"+id,count);observed(s,f'data entity {n} Inventory[{{Slot:{i}b,id:"minecraft:{id}",count:{count}}}]',"BAG_"+id,20)
            if phase=="first":
                for n in NAMES:s.send("clear "+n)
                for n,i,thing in [(NAMES[0],1,"water_bucket"),(NAMES[0],2,"bow"),(NAMES[0],3,"arrow 12"),(NAMES[0],4,"ender_pearl 2"),(NAMES[1],1,"emerald 4"),(NAMES[1],2,"lava_bucket")]:
                    s.send(f"item replace entity {n} hotbar.{i} with minecraft:{thing}")
                teleport(s,b,NAMES[1],10.5,81,7.5);teleport(s,a,NAMES[0],18.5,80,7.5)
                action("empty-plugin",a,"qaProtectedUse",slot=1,x=18.5,y=79.96,z=8.5)
                waitrow(events,"empty-plugin","monitor","true");block(s,ws,18,80,8,"air");checkbag(NAMES[0],a,1,"water_bucket",1)
                detail["cancelledWaterEmpty"]=stacks(s,ws)
                teleport(s,b,NAMES[1],18.5,80,7.5)
                action("lava-empty-plugin",b,"qaProtectedUse",slot=2,x=18.5,y=79.96,z=8.5)
                waitrow(events,"lava-empty-plugin","monitor","true");block(s,ws,18,80,8,"air");checkbag(NAMES[1],b,2,"lava_bucket",1)
                teleport(s,b,NAMES[1],12.5,81,7.5)
                action("lava-empty-ok",b,"qaProtectedUse",slot=2,x=12.5,y=79.98,z=8.5)
                waitrow(events,"lava-empty-ok","monitor","false");block(s,ws,12,80,8,"lava");checkbag(NAMES[1],b,2,"bucket",1)
                action("lava-fill-deny",b,"qaProtectedUse",slot=2,x=12.5,y=80.4,z=8.5)
                waitrow(events,"lava-fill-deny","monitor","true");block(s,ws,12,80,8,"lava");checkbag(NAMES[1],b,2,"bucket",1)
                detail["cancelledLavaFill"]=stacks(s,ws)
                teleport(s,a,NAMES[0],8.5,81,7.5)
                action("empty-ok",a,"qaProtectedUse",slot=1,x=8.5,y=79.98,z=8.5)
                waitrow(events,"empty-ok","monitor","false");block(s,ws,8,80,8,"water");checkbag(NAMES[0],a,1,"bucket",1)
                action("fill-deny",a,"qaProtectedUse",slot=1,x=8.5,y=80.4,z=8.5)
                waitrow(events,"fill-deny","monitor","true");block(s,ws,8,80,8,"water");checkbag(NAMES[0],a,1,"bucket",1)
                detail["cancelledWaterFill"]=stacks(s,ws)
                teleport(s,a,NAMES[0],10.5,81,7.5)
                action("fill-ok",a,"qaProtectedUse",slot=1,x=10.5,y=80.4,z=8.5)
                waitrow(events,"fill-ok","monitor","false");block(s,ws,10,80,8,"air");checkbag(NAMES[0],a,1,"water_bucket",1)
                teleport(s,a,NAMES[0],6.5,80,12.5);teleport(s,b,NAMES[1],13.5,80,12.5)
                s.send("effect give @a minecraft:instant_health 1 8 true")
                detail["shots"]={}
                for m,key,denied,hp in [("shoot-deny","shoot.monitor",True,20),("launch-deny","launch.monitor",True,20),("hit-deny","hit",True,20),("damage-deny","damage.monitor",True,20),("damage-ok","damage.monitor",False,16)]:
                    action(m,a,"qaProtectedBow",slot=2,name=NAMES[1])
                    waitrow(events,m,key,str(denied).lower());time.sleep(.65)
                    detail["shots"][m]={"health":compare_health(s,ws,NAMES[1],hp),"bags":stacks(s,ws),"events":records(events,m)}
                    lines=detail["shots"][m]["events"]
                    if m=="shoot-deny":assert not any(k.startswith(("launch","hit","damage")) for k,_ in lines),lines
                    if m=="launch-deny":assert not any(k.startswith(("hit","damage")) for k,_ in lines),lines
                    if m=="hit-deny":assert not any(k.startswith("damage") for k,_ in lines),lines
                action("pearl",a,"qaProtectedUse",slot=4,x=6.5,y=79.95,z=14.5)
                waitrow(events,"pearl","skippy.tracked","true");waitrow(events,"pearl","hit","false")
                def tick():return int(re.search(r"time is (\d+)",s.send("time query gametime",r"time is \d+"))[1])
                start=tick();eventually(tick,lambda t:t>=start+22)
                mode("cooldown")
                s.send("damage ShieldB 3 minecraft:generic\ndamage ShieldB 1 minecraft:generic")
                waitrow(events,"cooldown","damage.monitor","false")
                time.sleep(.25)
                assert len([k for k,_ in records(events,"cooldown") if k=="damage.low"])==1,records(events,"cooldown")
            detail["joins"]=joins
            detail["blocks"]=[block(s,ws,x,80,8,name) for x,name in [(18,"air"),(8,"water"),(10,"air"),(12,"lava")]]
            detail["actorHealth"]=compare_health(s,ws,NAMES[0],15)
            detail["health"]=compare_health(s,ws,NAMES[1],13)
            detail["bags"]=stacks(s,ws)
            if phase=="first":
                detail["events"]={k:records(events,k) for k in ["empty-plugin","lava-empty-plugin","lava-empty-ok","lava-fill-deny","empty-ok","fill-deny","fill-ok","shoot-deny","launch-deny","hit-deny","damage-deny","damage-ok","pearl","cooldown"]}
                assert not any(k=="active" for k,_ in detail["events"]["fill-deny"])
                assert any(k=="active" for k,_ in detail["events"]["fill-ok"])
            for w in ws:exited(w)
            s.send("save-all flush","Saved the game",180)
        text=log.read_text("utf8",errors="replace")
        if phase=="first":
            for player in NAMES:assert re.search(r"Blocked "+player+r" at .*18,80,8: potential cascade",text),log
        if not ispaper:
            for n in ["AntiLavaCast","LetMeDespawn","SkippyPearls"]:assert "Enabled "+n+" " in text,n
        assert not re.search(r"failed handling |Could not pass event|UnsupportedCall:|Task #?[0-9]+ of (?:AntiLavaCast|LetMeDespawn|SkippyPearls)",text),log
        report["runs"].append(detail)
        (out/"progress.json").write_text(json.dumps(detail,indent=2),"utf8")
    for field in ["bags","blocks","health","actorHealth","joins"]:assert report["runs"][0][field]==report["runs"][1][field],field
    shutil.copyfile(events,out/"events.tsv")
    report["logs"]={f.name:digest(f) for f in [*out.glob("*.log"),out/"events.tsv"]}
    (out/"result.json").write_text(json.dumps(report,indent=2),"utf8")
def main(o):
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="protection24-") as tmp:
        folder=Path(tmp);probe=plugin(folder)
        for which in ([o.only] if o.only else ["paper","unity"]):run(which,o,folder/which,probe)
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ["dist","paper","samples","authors","out","eula"]:p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    p.add_argument("--version",default=VERSION)
    p.add_argument("--only",choices=["paper","unity"]);p.add_argument("--plain",action="store_true")
    main(p.parse_args())
