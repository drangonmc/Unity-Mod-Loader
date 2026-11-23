"""Original Forge, Fabric and NeoForge releases on one dedicated world, with authoritative cancellation checks."""
import argparse,contextlib,json,re,shutil,socket,tempfile,time,subprocess,uuid
from pathlib import Path
from acceptance import ROOT,VERSION,DIST,EVIDENCE,Server,digest,SERVER_HASH,clean_env,class_audit,client_libraries
from content import Window,observed,slot
from dual import snapshot
A,B="UnityGuardA","UnityGuardB"
def at(x,y=201,z=18):return dict(x=x,y=y,z=z)
def eventually(fn,test,timeout=14):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        value=fn()
        if test(value):return value
        time.sleep(.12)
    raise AssertionError(("State did not converge",value))
def command(s,w,name,cmd):
    w.act("qaCommand",cmd=cmd);w.act("qaCommand",cmd="unityactionbarrier")
    s.expect("CHECK BARRIER "+name+"$",15)
def teleport(s,w,name,x,y,z):
    s.send(f"tp {name} {x} {y} {z} 0 14","Teleported")
    eventually(lambda:w.act("qaPlayer"),lambda p:abs(p["x"]-x)<.35 and abs(p["y"]-y)<.35 and abs(p["z"]-z)<.35)
def place_player(s,a,x):
    teleport(s,a,A,x+.5,201,15.8)
def world_check(s,windows,x,block,y=201,property=None):
    marker=f"WORLD_{x}_{y}_"+block.split(":")[1]
    observed(s,f"block {x} {y} 18 {block}"+(f"[{property}]" if property else ""),marker)
    ret=[]
    for w in windows:
        state=eventually(lambda:w.act("qaBlock",**at(x,y)),lambda v:v["id"]==block and (not property or property in v["state"]))
        ret.append(state["state"])
    return ret
def bag(s,w,who):
    line=s.send(f"data get entity {who} Inventory",r"following entity data: \[")
    data=s.send(f"execute as {who} run unityauthoritybag","CHECK AUTH_BAG "+who+" ")
    server=json.loads(data.split("CHECK AUTH_BAG "+who+" ",1)[1])
    matched=eventually(lambda:w.act("qaPlayer")["bag"],lambda local:local==server)
    return {"client":matched,"server":server,"serverNBT":line.split("following entity data: ",1)[1]}
def health(s,windows):
    line=s.send(f"data get entity {B} Health",r"following entity data: [0-9.]+f")
    value=float(re.search(r"entity data: ([0-9.]+)f",line)[1])
    views=[eventually(lambda:w.act("qaPlayer",name=B),lambda q:abs(q["health"]-value)<.025)["health"] for w in windows]
    return {"server":value,"clients":views}
def held(s,windows,id,count,damage,index):
    observed(s,f'data entity {A} Inventory[{{Slot:{index}b,id:"{id}",count:{count}}}]',"SLOT_MATCH")
    line=s.send(f'data get entity {A} Inventory[{{Slot:{index}b}}]',r"following entity data: \{")
    if damage and f'"minecraft:damage": {damage}' not in line:raise AssertionError(line)
    result=[]
    for w in windows:
        v=eventually(lambda:w.act("qaPlayer",name=A),lambda q:(q["hand"],q["held"],q["damage"])==(id,count,damage))
        result.append({"item":v["hand"],"count":v["held"],"damage":v["damage"]})
    return result
def setup(s,a,b):
    s.send("forceload add 16 12 47 27")
    for x in (16,47):
        for z in (12,27):observed(s,f"loaded {x} 200 {z}",f"LOADED_{x}_{z}")
    s.send("fill 16 200 12 47 200 27 minecraft:stone");s.send("fill 16 201 12 47 207 27 minecraft:air")
    s.send("gamerule minecraft:random_tick_speed 0");s.send("difficulty normal");s.send("time set noon");s.send("weather clear")
    s.send("gamerule minecraft:keep_inventory true");s.send("gamerule minecraft:natural_health_regeneration false")
    s.send(f"spawnpoint {A} 18 201 20");s.send(f"spawnpoint {B} 18 201 20")
    for name,w,x in ((A,a,22.5),(B,b,22.5)):
        s.send("clear "+name);teleport(s,w,name,x,201,15.8 if name==A else 20.9)
        s.send(f"execute as {name} run unityqa_setup_food")
    s.send("setblock 39 201 18 minecraft:stone");s.send("setblock 26 201 18 minecraft:stone");s.send("setblock 28 201 18 minecraft:lever[face=floor,powered=false]")
    s.send("setblock 32 200 18 minecraft:farmland[moisture=7]");s.send("setblock 32 201 18 minecraft:wheat[age=7]")
    s.send("setblock 32 200 19 minecraft:water")
    s.send("fill 34 201 18 34 203 18 minecraft:oak_log")
    for index,item in enumerate(("minecraft:barrel 3","minecraft:oak_door 2","minecraft:diamond_pickaxe[minecraft:damage=3]","minecraft:iron_axe","minecraft:oak_log 2","minecraft:shield","minecraft:wooden_sword","minecraft:wheat_seeds 6","minecraft:snowball 4")):
        s.send(f"item replace entity {A} hotbar.{index} with {item}")
    s.send(f"item replace entity {B} hotbar.0 with minecraft:amethyst_shard 5")
    s.send(f"item replace entity {B} inventory.10 with minecraft:iron_nugget 11")
    s.send("unitycanceltest none","CHECK ARMED none")
def exercise(s,a,b):
    windows=[a,b];run={};setup(s,a,b)
    a.act("qaCreative");b.act("qaCreative")
    # The author SKC command kills the test avatar; Fabric /back returns it to its recorded death position.
    a.act("qaCommand",cmd="fixturedeath")
    eventually(lambda:a.act("qaPlayer"),lambda p:p["dead"],20)
    a.act("qaRespawn");eventually(lambda:a.act("qaPlayer"),lambda p:not p["dead"] and abs(p["x"]-18.5)<3,20)
    command(s,a,A,"back");loc=eventually(lambda:a.act("qaPlayer"),lambda p:abs(p["x"]-22.5)<.8 and abs(p["z"]-15.8)<.8,20)
    run["backAfterSKC"]=[loc["x"],loc["y"],loc["z"]]
    baseline={A:bag(s,a,A),B:bag(s,b,B)}
    for x,index,id,n,label in ((22,0,"minecraft:barrel",3,"PLACE"),(24,1,"minecraft:oak_door",2,"MULTI")):
        place_player(s,a,x);slot(a,index,id,n);trial=a.act("qaRight",slot=index,top=True,**at(x,200))
        s.expect("CHECK "+label+" MONITOR true",15)
        run[label]={"prediction":trial,"world":world_check(s,windows,x,"minecraft:air"),"held":held(s,windows,id,n,0,index)}
        if label=="MULTI":run[label]["upper"]=world_check(s,windows,x,"minecraft:air",202)
    place_player(s,a,26);trial=a.act("qaMine",slot=2,**at(26));s.expect("CHECK BREAK MONITOR true",20)
    run["BREAK"]={"prediction":trial,"world":world_check(s,windows,26,"minecraft:stone"),"held":held(s,windows,"minecraft:diamond_pickaxe",1,3,2)}
    place_player(s,a,28);a.act("qaRight",slot=8,**at(28));s.expect("CHECK USE MONITOR true",12)
    run["USE"]=world_check(s,windows,28,"minecraft:lever",property="powered=false")
    place_player(s,a,30);slot(a,8,"minecraft:snowball",4)
    run["itemPrediction"]=a.act("qaUseOnce",slot=8);s.expect("CHECK ITEM MONITOR true",15)
    run["itemHeld"]=held(s,windows,"minecraft:snowball",4,0,8)
    s.send('summon minecraft:armor_stand 30.5 201 18.5 {Tags:["unity_cancel_test"],NoGravity:1b,ShowArms:1b}')
    time.sleep(.6);a.act("qaStandUse",slot=8);s.expect("CHECK ENTITY_USE MONITOR true",15)
    a.act("qaStandAttack",slot=6);s.expect("CHECK ENTITY_ATTACK MONITOR true",15)
    observed(s,'entity @e[type=minecraft:armor_stand,tag=unity_cancel_test]',"STAND_ALIVE")
    corrected={A:bag(s,a,A),B:bag(s,b,B)}
    if baseline!=corrected:raise AssertionError(("Cancelled block actions changed full inventory",baseline,corrected))
    run["beforeCancelBags"]=baseline;run["afterCancelBags"]=corrected
    observed(s,"block 22 201 18 minecraft:air","NO_CANCELLED_BARREL")
    s.send("unitycanceltest off","CHECK ARMED off")
    for switch in ("true","false"):
        a.act("qaRight",slot=8,**at(28));world_check(s,windows,28,"minecraft:lever",property="powered="+switch)
    s.send("unitycanceltest none","CHECK ARMED none")
    place_player(s,a,32);a.act("qaRight",slot=8,**at(32));run["crop"]=world_check(s,windows,32,"minecraft:wheat",property="age=0")
    # Author's place tracking protects a player-placed log alongside naturally generated logs.
    place_player(s,a,35);a.act("qaRight",slot=4,top=True,**at(35,200));world_check(s,windows,35,"minecraft:oak_log")
    place_player(s,a,34);a.act("qaSneak",down=True);time.sleep(.5);a.act("qaMine",slot=3,**at(34));a.act("qaSneak",down=False)
    run["tree"]=[world_check(s,windows,34,"minecraft:air",yy) for yy in (201,202,203)]
    run["protectedLog"]=world_check(s,windows,35,"minecraft:oak_log")
    s.send(f"experience set {A} 0 levels");s.send(f"experience set {A} 0 points")
    place_player(s,a,39);a.act("qaMine",slot=2,**at(39));s.expect("CHECK XP 0 -> 7",12)
    run["experienceBlock"]=world_check(s,windows,39,"minecraft:air")
    teleport(s,a,A,39.5,201,18.5)
    observed(s,f"data entity {A} {{XpTotal:7}}","SEVEN_XP",timeout=20)
    run["experienceAwarded"]=7
    trial=a.act("qaHoldShield",slot=5)
    if trial["immediate"]!="minecraft:shield" or trial["ticks"]>=5:raise AssertionError(trial)
    run["shield"]=trial;a.act("qaRelease")
    teleport(s,a,A,36.5,201,16.5);teleport(s,b,B,36.5,201,18.5)
    command(s,b,B,"pvp off")
    old=health(s,windows);a.act("qaAttack",name=B,slot=6);s.expect("CHECK ATTACK MONITOR true",15);run["pvpOff"]=health(s,windows)
    if run["pvpOff"]!=old:raise AssertionError("PvPControl did not protect player")
    command(s,b,B,"pvp on")
    for mode in ("hurt","damage","none"):
        s.send("unitycanceltest "+mode,"CHECK ARMED "+mode);time.sleep(1.3)
        teleport(s,a,A,36.5,201,16.5);teleport(s,b,B,36.5,201,18.5)
        eventually(lambda:a.act("qaPlayer",name=B),lambda p:abs(p["x"]-36.5)<.25 and abs(p["z"]-18.5)<.3)
        a.act("qaAttack",name=B,slot=6)
        s.expect("CHECK "+("HURT" if mode=="hurt" else "DAMAGE")+" MONITOR "+("false" if mode=="none" else "true"),15)
        run[mode]=health(s,windows)
        if mode!="none" and run[mode]!=old:raise AssertionError((mode,run[mode],old))
    if run["none"]["server"]>=old["server"]:raise AssertionError("Uncancelled positive control did not damage target")
    s.send(f"effect give {B} minecraft:absorption 30 1 true")
    def absorption():
        line=s.send(f"execute as {B} run unityabsstat",r"CHECK ABSORBED [0-9]+ [0-9.]+")
        count,hearts=line.split("CHECK ABSORBED ",1)[1].split()
        return [int(count),float(hearts)]
    absorbed=[absorption()]
    for mode in ("damage","none"):
        s.send("unitycanceltest "+mode,"CHECK ARMED "+mode);time.sleep(1.3)
        s.send(f"damage {B} 2 minecraft:generic");s.expect("CHECK DAMAGE MONITOR "+("true" if mode=="damage" else "false"),15)
        absorbed.append(absorption())
    if not(absorbed[1][0]==absorbed[0][0] and absorbed[2][0]>absorbed[1][0] and absorbed[0][1]>absorbed[1][1]>absorbed[2][1]):raise AssertionError(("Late damage cancellation and accepted zero damage",absorbed))
    run["absorptionStat"]=absorbed
    s.send(f"effect clear {B} minecraft:absorption")
    s.send("unitycanceltest damage","CHECK ARMED damage");time.sleep(1.3)
    teleport(s,a,A,36.5,201,16.5);teleport(s,b,B,36.5,201,18.5)
    eventually(lambda:a.act("qaPlayer",name=B),lambda p:abs(p["z"]-18.5)<.3 and abs(p["x"]-36.5)<.3)
    a.act("qaAttack",name=B,slot=6);s.expect("CHECK DAMAGE MONITOR true",15)
    run["finalHealth"]=health(s,windows)
    if run["finalHealth"]!=run["none"]:raise AssertionError("Last cancelled hit altered health before save")
    teleport(s,a,A,44.5,201,16.5);teleport(s,b,B,44.5,201,18.5)
    run["bags"]={A:bag(s,a,A),B:bag(s,b,B)}
    a.act("look",**at(35));run["screenshots"]={n:snapshot(w,EVIDENCE/n) for w,n in ((a,"event-client-a.png"),(b,"event-client-b.png"))}
    return run
def restored(s,a,b,initial):
    result={"bags":{A:bag(s,a,A),B:bag(s,b,B)},"health":health(s,[a,b])}
    if result["bags"]!=initial["bags"] or result["health"]!=initial["finalHealth"]:raise AssertionError(("Persistence failed",result,initial["bags"],initial["finalHealth"]))
    checks=[(22,201,"minecraft:air",None),(24,201,"minecraft:air",None),(24,202,"minecraft:air",None),(26,201,"minecraft:stone",None),(28,201,"minecraft:lever","powered=false"),(32,201,"minecraft:wheat","age=0"),(34,203,"minecraft:air",None),(35,201,"minecraft:oak_log",None)]
    result["blocks"]=[world_check(s,[a,b],x,block,y,prop) for x,y,block,prop in checks]
    return result
def verify(options):
    cache=ROOT/"cache"/VERSION;home=options.client_home.resolve();bundle=options.bundle.resolve();eula=options.eula_from.read_text()
    if digest(bundle)!=SERVER_HASH or not re.search(r"(?im)^eula\s*=\s*true\s*$",eula):raise ValueError("Original server and accepted local EULA required")
    jar=DIST/f"unity-loader-{VERSION}.jar";subprocess.run(["java","-jar",str(jar),"--install-client",str(home)],env=clean_env(),check=True)
    installed=home/f"libraries/mc/unity/unity-loader/{VERSION}/unity-loader-{VERSION}.jar"
    def locked(n):return json.loads((ROOT/"standalone"/n).read_bytes())
    common=locked("forge-content.json")+locked("forge-containers.json")+locked("ecosystem.json");commands=locked("forge-samples.json");fixture=options.rules.resolve()
    results=dict(version=VERSION,minecraft="26.2",loader=digest(jar),serverProbe=digest(DIST/"unity-world-marker.jar"),clientProbe=digest(DIST/"unity-client-witness.jar"),rulesSHA256=digest(fixture),originals={x["filename"]:x["sha512"] for x in common+commands},runs={})
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="Unity ecology ") as tmp:
        root=Path(tmp);server=root/"server";games=[root/"client-a",root/"client-b"]
        with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
        for g in [server,*games]:
            (g/"mods").mkdir(parents=True);(g/"unity-mods").mkdir()
            for spec in common+(commands if g==server else []):
                src=options.samples/spec["filename"];dst=Path(shutil.copy2(src,g/"mods"))
                if digest(dst,"sha512")!=spec["sha512"]:raise ValueError(dst)
            shutil.copy2(fixture,g/"mods");shutil.copy2(DIST/("unity-world-marker.jar" if g==server else "unity-client-witness.jar"),g/"unity-mods")
            if g!=server:(g/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:5\nsimulationDistance:4\nmaxFps:35\nfullscreen:false\ntutorialStep:none\n")
        (server/"config").mkdir();(server/"config"/"simplekillcommand.properties").write_text("command.name=fixturedeath\ncommand.override-existing=false\n")
        (server/"eula.txt").write_text(eula)
        (server/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nview-distance=5\nsimulation-distance=3\npause-when-empty-seconds=-1\nspawn-protection=0\nlevel-seed=261001\n")
        for phase in ("ecology-mixed","ecology-restart"):
            s=Server(server,phase,jar,bundle,cache);r={};results["runs"][phase]=r
            try:
                s.expect("Standalone ready",160);r["banner"]=s.send("unity",r"standalone \| MC 26\.2")
                with contextlib.ExitStack() as stack:
                    windows=[];r["players"]=[]
                    for who,g,suffix in zip((A,B),games,("a","b")):
                        w=Window(home,g,phase+"-"+suffix,port,installed,who,False);windows.append(w);stack.callback(close,w)
                        player=w.act("status",timeout=150);s.expect(who+" joined the game",30);r["players"].append({"pid":w.process.pid,**player})
                        if player["player"]!=who or not player["remote"] or not player["gpu"]:raise ValueError(player)
                    r["actions"]=exercise(s,*windows) if phase=="ecology-mixed" else restored(s,*windows,results["runs"]["ecology-mixed"]["actions"])
                s.send("save-all flush","Saved the game",150)
            finally:s.close()
            logs=[EVIDENCE/(phase+suffix+".log") for suffix in ("","-a-client","-b-client")];r["logs"]={p.name:digest(p) for p in logs}
            st=logs[0].read_text("utf8")
            if phase=="ecology-mixed":
                r["priority"]={}
                for label in ("PLACE","MULTI","BREAK","USE","ITEM","ENTITY_USE","ENTITY_ATTACK"):
                    marks=re.findall(r"CHECK "+label+r" (HIGH|RULE|LOW|MONITOR (?:true|false))",st)
                    if marks!=["HIGH","RULE","MONITOR true"]:raise AssertionError((label,marks))
                    r["priority"][label]=marks
                if not re.search(r"\[CommandsSpy\].*UnityGuardA.*fixturedeath",st):raise AssertionError("No original CommandsSpy audit")
                for log in logs[1:]:
                    if "CHECK MOD CREATIVE" not in log.read_text("utf8",errors="replace"):raise AssertionError("Creative contents subscriber was skipped")
            for label,g,log in zip(("server","a","b"),(server,*games),logs):
                allowed=client_libraries(home) if g!=server else {p.resolve():"Mojang library" for p in (cache/"libraries").rglob("*.jar")}
                allowed[(jar if g==server else installed).resolve()]="unity-loader"
                allowed[(cache/"versions/26.2/server-26.2.jar" if g==server else home/"versions/26.2/26.2.jar").resolve()]="Mojang game"
                for area in ("mods","unity-mods"):
                    for m in (g/area).glob("*.jar"):allowed[m.resolve()]=m.name
                audit=class_audit(g/(phase+("" if g==server else "-"+label)+"-classes.log"),allowed,{"mc.unity.core.Actions":"unity-loader"})
                for spec in common+(commands if g==server else []):
                    if not (g!=server and spec.get("slug")=="skc") and not audit["fileOrigins"].get(spec["filename"]):raise AssertionError((label,spec["filename"]))
                    if digest(g/"mods"/spec["filename"],"sha512")!=spec["sha512"]:raise ValueError("Changed original")
                r[label+"Audit"]=audit
            print("PASS",phase,flush=True)
        if [p["uuid"] for p in results["runs"]["ecology-mixed"]["players"]]!=[p["uuid"] for p in results["runs"]["ecology-restart"]["players"]]:raise AssertionError("Player identity changed")
    temp=EVIDENCE/"ecology.tmp";temp.write_text(json.dumps(results,indent=2));temp.replace(EVIDENCE/"ecology.json")
def close(window):
    if window.close()!=0:raise RuntimeError("Client exit failure")
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for n in ("client-home","bundle","samples","eula-from","rules"):p.add_argument("--"+n,required=True,type=Path)
    verify(p.parse_args())
