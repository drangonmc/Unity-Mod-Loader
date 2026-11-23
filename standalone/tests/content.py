"""End-to-end Forge content acceptance with two independent 26.2 JVMs."""
import argparse, json, os, queue, re, shutil, socket, subprocess, tempfile, time, uuid
from pathlib import Path
from acceptance import ROOT, VERSION, DIST, EVIDENCE, Server, SERVER_HASH, NAME, clean_env, digest, class_audit, client_libraries

FOOD="moreberryuses:sweet_berry_pie"
CRATE="cratedelight:apple_crate"
STACK="stackedblocks:stacked_cobblestone_blocks"
IDS=(FOOD,CRATE,STACK)
A=(101,136,134)
B=(102,136,134)

def launch(home,game,port,installed,io,phase,log,name=NAME,dual=False):
    native=game/"natives";native.mkdir(exist_ok=True)
    args=["java","-Xms384m","-Xmx2304m","--enable-native-access=ALL-UNNAMED",
          "-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8",f"-Djava.library.path={native}",
          f"-Dmc.unity.dualQA={str(dual).lower()}",f"-Dmc.unity.clientHome={home}",f"-Dunity.qa.io={io}","-Dmc.unity.standaloneQA=true","-Dmc.unity.hiddenQA=true",f"-Xlog:class+load=info:file={phase}-classes.log",
          "-cp",str(installed),"mc.unity.bootstrap.ClientEntry","--username",name,
          "--version","26.2","--versionType","release","--gameDir",str(game),
          "--assetsDir",str(home/"assets"),"--assetIndex","32","--uuid",uuid.uuid5(uuid.NAMESPACE_DNS,name).hex,
          "--accessToken","0","--clientId","0","--xuid","0","--width","960","--height","540",
          "--quickPlayPath",str(game/"quickplay.json"),"--quickPlayMultiplayer",f"127.0.0.1:{port}"]
    if port is None:
        del args[-4:]
        args.insert(1,"-Dmc.unity.integratedQA=true")
    return subprocess.Popen(args,cwd=game,env={**clean_env(),"ALSOFT_DRIVERS":"null"},creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0),stdout=log,stderr=subprocess.STDOUT)

class Window:
    def __init__(self,home,game,phase,port,installed,name=NAME,dual=False):
        self.io=game/"unity-qa";self.io.mkdir(exist_ok=True)
        for filename in ("request.json","response.json","response.tmp","quit"):(self.io/filename).unlink(missing_ok=True)
        self.seq=0;self.game=game;self.stop_timeout=150 if port is None else 30;self.log=(EVIDENCE/(phase+"-client.log")).open("wb")
        try:self.process=launch(home,game,port,installed,self.io,phase,self.log,name,dual)
        except BaseException:self.log.close();raise
    def act(self,action,timeout=35,**kwargs):
        self.seq+=1
        response=self.io/"response.json";response.unlink(missing_ok=True)
        tmp=self.io/"request.tmp";tmp.write_text(json.dumps({"action":action,"seq":self.seq,**kwargs}));tmp.replace(self.io/"request.json")
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            if response.is_file():
                obj=json.loads(response.read_bytes())
                if obj["seq"]!=self.seq or "error" in obj:raise ValueError(f"Client {action}: {obj}")
                return obj
            if self.process.poll() is not None:raise RuntimeError(f"Client exited {self.process.returncode} during {action}")
            if action=="status":
                output=Path(self.log.name)
                if output.exists():
                    match=re.search(r"Client disconnected with reason: ([^\r\n]+)",output.read_text(encoding="utf8",errors="replace"))
                    if match:raise RuntimeError(match[1])
            time.sleep(.1)
        raise TimeoutError(f"Real client action {action}")

    def close(self):
        try:
            if self.process.poll() is None:
                try:(self.io/"quit").touch();self.process.wait(self.stop_timeout)
                except (RuntimeError,TimeoutError,subprocess.TimeoutExpired):
                    if self.process.poll() is None:self.process.terminate()
                    self.process.wait(20)
        finally:self.log.close()
        return self.process.returncode


def observed(server, condition, label, timeout=12):
    """Wait for the authoritative world, independently of client-side prediction."""
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        server.send("execute if "+condition+" run say "+label)
        line=server.send("execute unless "+condition+" run say WAIT_"+label,rf"\[Server\] (?:WAIT_)?{label}\b")
        if "WAIT_"+label not in line:return
        time.sleep(.2)
    raise AssertionError(condition)


def slot(window,index,id,count):
    end=time.monotonic()+10
    while time.monotonic()<end:
        view=window.act("slot",slot=index)
        if (view["id"],view["count"])==(id,count):return view
        time.sleep(.2)
    raise AssertionError(f"Client did not see slot {index}: {view}")


def server_slot(server,index,id,count):
    observed(server,f'data entity {NAME} Inventory[{{Slot:{index}b,id:"{id}",count:{count}}}]',f"UNITY_SLOT_{index}_{count}")


def block(server,window,where,id):
    x,y,z=where
    observed(server,f"block {x} {y} {z} {id}","UNITY_BLOCK_"+str(x)+"_"+id.split(":")[1])
    end=time.monotonic()+8
    while time.monotonic()<end:
        state=window.act("block",x=x,y=y,z=z)
        if state["id"]==id:return state
        time.sleep(.2)
    raise AssertionError(f"Client still sees {state}")


def registries(server,window):
    result={}
    for id in IDS:
        s=server.send("unityregistry "+id,rf"UNITY_REGISTRY {re.escape(id)} item=\d+ block=-?\d+")
        numbers=list(map(int,re.search(r"item=(\d+) block=(-?\d+)",s).groups()))
        c=window.act("registry",id=id)
        if c["assetPack"]!="unity/"+id.split(":")[0]:raise AssertionError(c)
        if [c["itemNumber"],c.get("blockNumber",-1)]!=numbers or not c["renderLayers"] or c["id"]!=id:
            raise AssertionError(f"Registry or model mismatch: {id} {numbers} {c}")
        result[id]={"server":numbers,"client":c}
    return result


def actions(server,window,game,evidence):
    server.send(f"gamemode survival {NAME}")
    server.send("difficulty normal")
    server.send("time set noon"); server.send("weather clear")
    server.send("forceload add 96 128")
    server.send("forceload query 96 128",r"Chunk at \[6, 8\].*is marked")
    server.send("fill 97 135 128 107 135 139 minecraft:stone")
    server.send("fill 97 136 128 107 141 139 minecraft:air")
    server.send(f"tp {NAME} 101.5 136 131.8 0 17","Teleported")
    server.send(f"clear {NAME}")
    for i,(id,n) in enumerate(((FOOD,3),(CRATE,4),(STACK,5),("minecraft:diamond_pickaxe",1),("minecraft:iron_axe",1))):
        server.send(f"item replace entity {NAME} hotbar.{i} with {id} {n}")
        slot(window,i,id,n)
    server.send(f"execute as {NAME} run unityqa_setup_food")
    observed(server,f"data entity {NAME} {{foodLevel:8}}","UNITY_HUNGRY")
    eat=window.act("use",slot=0,item=FOOD)
    after=slot(window,0,FOOD,2)
    if (eat["before"],eat["after"])!=(3,2):raise AssertionError(eat)
    server_slot(server,0,FOOD,2)
    line=server.send(f"data get entity {NAME} foodLevel",r"entity data: \d+")
    hunger=int(re.search(r"entity data: (\d+)",line)[1])
    if hunger<=8:raise AssertionError("Server did not apply the food effect")
    ops=[]
    for pos,id,idx,tool,tool_slot in [(A,CRATE,1,"minecraft:iron_axe",4),(B,STACK,2,"minecraft:diamond_pickaxe",3)]:
        for action,held,i,expected in [("place",id,idx,id),("break",tool,tool_slot,"minecraft:air"),("place",id,idx,id)]:
            x,y,z=pos
            if action == "break":
                slot(window, i, held, 1)
                outcome = window.act("qaMine", x=x, y=y, z=z, slot=i)
                if outcome["id"] != expected:
                    raise AssertionError(f"Survival mining failed at {pos}: {outcome}")
            else:
                outcome=window.act(action,x=x,y=y,z=z,item=held,slot=i,block=id)
            state=block(server,window,pos,expected)
            ops.append({"action":action,"target":id,"client":outcome,"observed":state})
    window.act("look",x=101,y=136,z=134);time.sleep(1)
    screens=game/"screenshots"
    before=set(screens.glob("*.png"))
    window.act("screenshot")
    for _ in range(100):
        recent=[p for p in set(screens.glob("*.png"))-before if p.stat().st_size>1000]
        if recent:break
        time.sleep(.1)
    if not recent:raise AssertionError("Minecraft did not produce a screenshot")
    shot=evidence/"content-world.png";shutil.copyfile(recent[0],shot)
    snapshots={}
    # The server may have picked up broken block drops. Compare actual normal inventory counts.
    for i,id in enumerate(IDS):
        view=window.act("slot",slot=i)
        assert view["id"]==id and view["count"]>0
        server_slot(server,i,id,view["count"]); snapshots[id]=view["count"]
    return dict(foodBefore=8,foodAfter=hunger,consumption=eat,inventory=snapshots,actions=ops,
                screenshot=shot.name,screenshotSHA256=digest(shot))


def verify(options):
    home=options.client_home.resolve()
    bundle=options.bundle.resolve()
    eula=options.eula_from.read_text()
    if digest(bundle)!=SERVER_HASH or not re.search(r"(?im)^eula\s*=\s*true\s*$",eula):raise ValueError("Original server and accepted local test instance required")
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    jar=DIST/f"unity-loader-{VERSION}.jar"
    subprocess.run(["java","-jar",str(jar),"--install-client",str(home)],env=clean_env(),check=True)
    installed=home/f"libraries/mc/unity/unity-loader/{VERSION}/unity-loader-{VERSION}.jar"
    contents=json.loads((ROOT/"standalone/forge-content.json").read_bytes())
    commands=json.loads((ROOT/"standalone/forge-samples.json").read_bytes())
    results={"version":VERSION,"minecraft":"26.2","loaderSHA256":digest(jar),"worldMarkerSHA256":digest(DIST/"unity-world-marker.jar"),
             "clientWitnessSHA256":digest(DIST/"unity-client-witness.jar"),"originalSHA512":{},"runs":{}}
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="Unity content ") as folder:
        scratch=Path(folder); server_game=scratch/"server"; client_game=scratch/"client";cache=scratch/"runtime"
        for game,example in ((server_game,"unity-world-marker.jar"),(client_game,"unity-client-witness.jar")):
            (game/"unity-mods").mkdir(parents=True);shutil.copy2(DIST/example,game/"unity-mods")
            (game/"mods").mkdir()
            for spec in (contents+commands if game==server_game else contents):
                src=options.samples/spec["filename"];dest=Path(shutil.copy2(src,game/"mods"))
                if digest(dest,"sha512")!=spec["sha512"]:raise ValueError("Changed author JAR: "+str(dest))
                results["originalSHA512"][spec["filename"]]=spec["sha512"]
        with socket.socket() as s:s.bind(("127.0.0.1",0));port=s.getsockname()[1]
        (server_game/"eula.txt").write_text(eula)
        (server_game/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\npause-when-empty-seconds=-1\nview-distance=4\nsimulation-distance=3\nspawn-protection=0\nlevel-seed=2607007\n")
        (client_game/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:5\nsimulationDistance:5\nmaxFps:40\nfullscreen:false\ntutorialStep:none\n")
        for phase in ("content-mixed","content-restart","content-mismatch"):
            negative=phase=="content-mismatch"
            if negative:
                missing=next(s for s in contents if s["slug"]=="stacked-blocks")["filename"]
                (client_game/"mods"/missing).rename(scratch/missing)
            server=Server(server_game,phase,jar,bundle,cache)
            run={};results["runs"][phase]=run
            try:
                server.expect(r"\[Unity\] Standalone ready",150)
                banner=server.send("unity",r"standalone \| MC 26\.2 \| native")
                if any(i not in banner for i in ("cratedelight","stackedblocks","moreberryuses","repair_commands","randomitem","world-marker")):raise AssertionError(banner)
                client=Window(home,client_game,phase,port,installed)
                try:
                    if negative:
                        reason=server.expect("content registry mismatch",150)
                        run.update(rejection=reason,omitted=missing)
                    else:
                        server.expect(NAME+r".*joined the game",150)
                        status=client.act("status",timeout=90)
                        if not status["remote"] or not status["gpu"] or status["player"]!=NAME:raise AssertionError(status)
                        run["window"]=status
                        run["registries"]=registries(server,client)
                        if phase=="content-mixed":
                            server.send(f"clear {NAME}")
                            server.send(f"execute as {NAME} run getRandomItem 1 1")
                            item=server.send(f"data get entity {NAME} Inventory[0]",r'entity data: .*id: "[a-z0-9_.-]+:')
                            if 'count: 1' not in item:raise ValueError(item)
                            run["randomItem"]=re.search(r'id: "([a-z0-9_./:-]+)"',item)[1]
                            server.send(f"item replace entity {NAME} weapon.mainhand with minecraft:iron_pickaxe[minecraft:damage=77]")
                            for damage in (77,0):
                                if damage==0:server.send(f"execute as {NAME} run repair")
                                observed(server,f"items entity {NAME} weapon.mainhand minecraft:iron_pickaxe[minecraft:damage={damage}]","PICKAXE_"+str(damage))
                            run["pickaxeDamage"]=[77,0]
                            run.update(actions(server,client,client_game,EVIDENCE))
                        else:
                            first=results["runs"]["content-mixed"]
                            if status["uuid"]!=first["window"]["uuid"]:raise AssertionError("Player identity changed")
                            run["inventory"]={}
                            for i,id in enumerate(IDS):
                                count=first["inventory"][id];server_slot(server,i,id,count)
                                run["inventory"][id]=slot(client,i,id,count)["count"]
                            run["world"]=[block(server,client,A,CRATE),block(server,client,B,STACK)]
                finally:
                    run["clientExit"]=client.close()
                if run["clientExit"]:raise RuntimeError("Client exit "+str(run["clientExit"]))
                server.send("save-all flush","Saved the game",150)
            finally:server.close()
            run["exit"]=server.process.returncode
            server_log=EVIDENCE/(phase+".log");client_log=EVIDENCE/(phase+"-client.log")
            st=server_log.read_text(encoding="utf8");ct=client_log.read_text(encoding="utf8",errors="replace")
            if negative:
                if NAME+" joined the game" in st or "Server registry match=false" not in ct:raise AssertionError("Mismatch entered play or no negative response")
            else:
                match=re.search(r"Verified client content registry ([0-9a-f]{64})",st)
                if not match or "Server registry match=true local="+match[1] not in ct:raise AssertionError("Two-sided registry negotiation missing")
                run["digest"]=match[1]
            server_allowed={p.resolve():"Mojang library" for p in (cache/"libraries").rglob("*.jar")}
            server_allowed[(cache/"versions/26.2/server-26.2.jar").resolve()]="Mojang server"
            client_allowed=client_libraries(home);client_allowed[(home/"versions/26.2/26.2.jar").resolve()]="Mojang client"
            for game,allowed,binary in ((client_game,client_allowed,installed),(server_game,server_allowed,jar)):
                allowed[binary.resolve()]="unity-loader"
                for sub in ("mods","unity-mods"):
                    for mod in (game/sub).glob("*.jar"):allowed[mod.resolve()]=mod.name
            run["serverAudit"]=class_audit(server_game/(phase+"-classes.log"),server_allowed,{"net.minecraft.server.Main":"Mojang server","mc.unity.core.Content":"unity-loader"})
            run["clientAudit"]=class_audit(client_game/(phase+"-classes.log"),client_allowed,{"net.minecraft.client.Minecraft":"Mojang client","mc.unity.core.Content":"unity-loader"})
            for side,expected in (("serverAudit",contents+commands),("clientAudit",[s for s in contents if not negative or s["filename"]!=missing])):
                for spec in expected:
                    if not run[side]["fileOrigins"].get(spec["filename"]):raise AssertionError("No original classes from "+spec["filename"])
            run["logs"]={p.name:digest(p) for p in (server_log,client_log)}
            print("PASS",phase,flush=True)
        if results["runs"]["content-mixed"]["digest"]!=results["runs"]["content-restart"]["digest"]:raise AssertionError("Registry changed on restart")
        for spec in contents+commands:
            if digest(server_game/"mods"/spec["filename"],"sha512")!=spec["sha512"]:raise ValueError("Modified original")
    temp=EVIDENCE/"content.tmp";temp.write_text(json.dumps(results,indent=2));temp.replace(EVIDENCE/"content.json")

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    for name in ("client-home","bundle","eula-from","samples"):parser.add_argument("--"+name,required=True,type=Path)
    verify(parser.parse_args())
