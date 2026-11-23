"""Eight author JARs, two independent game clients, and persisted shared state on Minecraft 26.2."""
import argparse, contextlib, json, re, secrets, shutil, socket, subprocess, tempfile, time
from pathlib import Path
from acceptance import ROOT,VERSION,DIST,EVIDENCE,Server,digest,SERVER_HASH,clean_env,client_libraries,class_audit
from content import Window,observed,slot as player_view
A="UnityDrawerA"
B="UnityDrawerB"
CRATE="cratedelight:carrot_crate"
COOKIE="moreberryuses:jam_cookie"
DRAWER="storagedelight:oak_drawer"
G=(105,136,135)

def position(x):
    return dict(x=x,y=136,z=135)

def wait_slot(w,index,expected,inventory=False):
    end=time.monotonic()+12
    while time.monotonic()<end:
        state=w.act("menuInfo")
        key="inventory" if inventory else "index"
        slot=next(v for v in state["slots"] if v[key]==index)
        if (slot["id"],slot["count"])==expected and state["carried"]["count"]==0:return dict(id=slot["id"],count=slot["count"],stateId=state["stateId"])
        time.sleep(.15)
    raise AssertionError((index,expected,slot,state["carried"]))

def click(w,i,button=0,inventory=False):
    return w.act("menuClick",index=i,button=button,**({"inventory":i} if inventory else {}))

def drawer_slot(s,index,item,count):
    observed(s,f'data block 105 136 135 Items[{{Slot:{index}b,id:"{item}",count:{count}}}]',f"BOX_{index}_{count}")

def player_slot(s,name,index,item,count):
    observed(s,f'data entity {name} Inventory[{{Slot:{index}b,id:"{item}",count:{count}}}]',f"INV_{name}_{index}_{count}")

def snapshot(w,dest):
    shots=w.game/"screenshots";old=set(shots.glob("*.png"));w.act("screenshot")
    for _ in range(100):
        for f in set(shots.glob("*.png"))-old:
            if f.stat().st_size>1500 and f.read_bytes().endswith(b"IEND\xaeB`\x82"):
                shutil.copy2(f,dest);return digest(dest)
        time.sleep(.1)
    raise AssertionError("Unfinished client screenshot")

def shared(s,a,b,restart):
    if not restart:
        s.send("forceload add 97 129 114 140")
        observed(s,"loaded 97 135 129","CHUNK_6");observed(s,"loaded 114 135 140","CHUNK_7")
        s.send("fill 97 135 129 114 135 140 minecraft:stone")
        s.send("fill 97 136 129 114 141 140 minecraft:air")
        observed(s,"block 105 135 133 minecraft:stone","PLAYER_FLOOR")
        s.send("setblock 105 136 135 "+DRAWER)
        s.send(f"item replace entity {A} hotbar.6 with {CRATE} 17")
        s.send(f"item replace entity {B} hotbar.6 with {COOKIE} 9")
    observed(s,"block 105 136 135 "+DRAWER,"DRAWER_PRESENT")
    for name,x in ((A,105.5),(B,104.1)):s.send(f"tp {name} {x} 136 133.1 0 12","Teleported")
    for w,x in ((a,105.5),(b,104.1)):
        for _ in range(60):
            loc=w.act("status")
            if abs(loc["x"]-x)<.3 and abs(loc["y"]-136)<.3 and abs(loc["z"]-133.1)<.4:break
            time.sleep(.1)
        else:raise AssertionError(("Player not at drawer",loc))
        for _ in range(40):
            if w.act("block",**position(105))["id"]==DRAWER:break
            time.sleep(.2)
    screens=[w.act("menuOpen",handSlot=8,**position(105)) for w in (a,b)]
    if any(x["menu"]!="minecraft:generic_9x3" for x in screens):raise ValueError(screens)
    viewers=s.send("execute positioned 105 136 135 run unityqa_viewers",r"UNITY_SHARED 105, 136, 135 \[UnityDrawerA, UnityDrawerB\]")
    result={"viewers":viewers,"screens":[{k:v[k] for k in ("screen","menu","containerId")} for v in screens],"steps":[]}
    if not restart:
        for i,item,initial,owner,peer,owner_name,peer_name in ((0,CRATE,17,a,b,A,B),(1,COOKIE,9,b,a,B,A)):
            wait_slot(owner,6,(item,initial),True)
            click(owner,6,inventory=True);click(owner,i)
            pair=[wait_slot(w,i,(item,initial)) for w in (a,b)]
            drawer_slot(s,i,item,initial)
            s.send(f'execute unless data entity {owner_name} Inventory[{{Slot:6b}}] run say DEPOSIT_OK',r"\[Server\] DEPOSIT_OK")
            result["steps"].append({"by":owner_name,"operation":"deposit","clients":pair,"serverCount":initial})
            click(peer,i,button=1);click(peer,7,inventory=True)
            left=initial//2;taken=initial-left
            pair=[wait_slot(w,i,(item,left)) for w in (a,b)]
            drawer_slot(s,i,item,left);player_slot(s,peer_name,7,item,taken);wait_slot(peer,7,(item,taken),True)
            result["steps"].append({"by":peer_name,"operation":"withdraw","clients":pair,"serverCount":left,"playerCount":taken})
    for i,item,n in ((0,CRATE,8),(1,COOKIE,4)):
        drawer_slot(s,i,item,n)
        for w in (a,b):wait_slot(w,i,(item,n))
    for name,w,item,n in ((A,a,COOKIE,5),(B,b,CRATE,9)):
        player_slot(s,name,7,item,n);wait_slot(w,7,(item,n),True)
        wait_slot(w,6,("minecraft:air",0),True)
    result["fullInventories"]={}
    for name,w in ((A,a),(B,b)):
        view=w.act("menuInfo")
        slots={str(v["inventory"]):[v["id"],v["count"]] for v in view["slots"] if v["inventory"]>=0 and v["count"]}
        nbt=s.send(f"data get entity {name} Inventory",r"following entity data: \[")
        result["fullInventories"][name]={"slots":slots,"serverNBT":nbt.split("following entity data: ",1)[1]}
    result["drawer"]={CRATE:8,COOKIE:4};result["bags"]={A:{COOKIE:5},B:{CRATE:9}}
    result["replies"]=[]
    for w in (a,b):
        nonce=secrets.randbelow(2**30)
        reply=w.act("packetRoundTrip",nonce=nonce)
        if (reply["nonce"],reply["id"],reply["count"])!=(nonce,CRATE,8):raise AssertionError(reply)
        result["replies"].append(reply)
    if not restart:result["screenshots"]={name:snapshot(w,EVIDENCE/name) for name,w in (("drawer-a.png",a),("drawer-b.png",b))}
    for w in (a,b):w.act("menuClose")
    return result

def custom(s,w,restart):
    slot="inventorystorages:player_slot_storage";color="laserbladetools:colorizer"
    if not restart:
        for x,id in ((108,slot),(110,color)):
            s.send(f"tp {A} {x+.5} 136 133.1 0 15","Teleported")
            s.send(f"item replace entity {A} hotbar.5 with {id}");player_view(w,5,id,1)
            w.act("place",item=id,slot=5,block=id,**position(x))
    for x,id in ((108,slot),(110,color)):observed(s,f"block {x} 136 135 {id}","CUSTOM_"+str(x))
    s.send(f"tp {A} 108.5 136 133.1 0 15","Teleported")
    opened=w.act("menuOpen",handSlot=8,**position(108))
    if opened["menu"]!=slot:raise AssertionError(opened)
    if not restart:
        for _ in range(40):
            sync=w.act("menuInfo").get("syncedData",[])
            if sync[4:7]==[108,136,135]:break
            time.sleep(.1)
        else:raise AssertionError(("Menu did not receive block position",sync))
        edit=w.act("menuEdit",value="17")
        if edit.get("value")!="17":raise AssertionError(edit)
        observed(s,'data block 108 136 135 {slotId:17}',"SLOT_ID_SAVED")

    line=s.send("data get block 108 136 135 slotId",r"following block data: 17")
    result={"storageScreen":opened["screen"],"slotId":17,"server":line}
    if not restart:
        nonce=secrets.randbelow(2**30);reply=w.act("packetRoundTrip",nonce=nonce);result["storageReply"]=reply
        local=w.act("menuInfo")["slots"][0]
        if (reply["nonce"],reply["id"],reply["count"])!=(nonce,local["id"],local["count"]):raise AssertionError(reply)
    w.act("menuClose")
    s.send(f"tp {A} 110.5 136 133.1 0 15","Teleported")
    opened=w.act("menuOpen",handSlot=8,**position(110))
    if opened["menu"]!=color:raise AssertionError(opened)
    result["colorizerScreen"]=opened["screen"]
    if not restart:
        before=s.send(f"execute as {A} run unityqa_colors",r"UNITY_COLORS \[[0-9, ]+\]");result["before"]=before.split("UNITY_COLORS ")[-1]
        w.act("menuButton",nth=0)
        for _ in range(35):
            after=s.send(f"execute as {A} run unityqa_colors",r"UNITY_COLORS \[[0-9, ]+\]");result["after"]=after.split("UNITY_COLORS ")[-1]
            if result["after"]!=result["before"]:break
            time.sleep(.12)
        if result["before"]==result["after"]:raise AssertionError(result)
    nonce=secrets.randbelow(2**30);local=w.act("menuInfo")["slots"][0];reply=w.act("packetRoundTrip",nonce=nonce)
    if (reply["nonce"],reply["id"],reply["count"])!=(nonce,local["id"],local["count"]):raise AssertionError("Colorizer bidirectional reply")
    result["colorizerReply"]=reply
    w.act("menuClose")
    return result

def run(options):
    home=options.client_home.resolve();bundle=options.bundle.resolve();eula=options.eula_from.read_text()
    if digest(bundle)!=SERVER_HASH or not re.search(r"(?im)^eula\s*=\s*true\s*$",eula):raise ValueError("Expected local EULA and official server")
    jar=DIST/f"unity-loader-{VERSION}.jar";cache=ROOT/"cache"/VERSION
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    subprocess.run(["java","-jar",str(jar),"--install-client",str(home)],env=clean_env(),check=True)
    installed=home/f"libraries/mc/unity/unity-loader/{VERSION}/unity-loader-{VERSION}.jar"
    specs={n:json.loads((ROOT/"standalone"/n).read_bytes()) for n in ("forge-content.json","forge-containers.json","forge-samples.json")}
    common=specs["forge-content.json"]+specs["forge-containers.json"];commands=specs["forge-samples.json"]
    fixtures=[options.platform_samples/(name+".jar") for name in ("unity_fabric_sample","unity_neo_sample")]
    extras=sorted(options.interop_samples.glob("*.jar")) if options.interop_samples else []
    result=dict(interopSamples={f.name:digest(f) for f in extras},version=VERSION,minecraft="26.2",loaderSHA256=digest(jar),worldMarkerSHA256=digest(DIST/"unity-world-marker.jar"),clientWitnessSHA256=digest(DIST/"unity-client-witness.jar"),originalSHA512={e["filename"]:e["sha512"] for e in common+commands},authoredTestMods={f.name:digest(f) for f in fixtures},runs={})
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="Dual Unity ") as tmp:
        root=Path(tmp);game=root/"server";games=[root/"a",root/"b"]
        with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
        for d in [game,*games]:
            (d/"mods").mkdir(parents=True);(d/"unity-mods").mkdir()
            for spec in common+(commands if d==game else []):
                path=Path(shutil.copy2(options.samples/spec["filename"],d/"mods"))
                if digest(path,"sha512")!=spec["sha512"]:raise ValueError(path)
            for f in fixtures+extras:shutil.copy2(f,d/"mods")
            shutil.copy2(DIST/("unity-world-marker.jar" if d==game else "unity-client-witness.jar"),d/"unity-mods")
            if d!=game:(d/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:4\nsimulationDistance:4\nmaxFps:35\nfullscreen:false\ntutorialStep:none\n")
        (game/"eula.txt").write_text(eula)
        (game/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nview-distance=4\nsimulation-distance=3\npause-when-empty-seconds=-1\nspawn-protection=0\nlevel-seed=2608001\n")
        for phase in ("dual-mixed","dual-restart"):
            restart=phase.endswith("restart");run={};result["runs"][phase]=run
            s=Server(game,phase,jar,bundle,cache)
            try:
                s.expect("Standalone ready",180);banner=s.send("unity",r"standalone \| MC 26\.2")
                for name in ("storagedelight","inventorystorages","laserbladetools","moreberryuses","cratedelight","stackedblocks","randomitem","repair_commands","unity_fabric_sample","unity_neo_sample"):
                    if name not in banner:raise AssertionError(banner)
                run["banner"]=banner
                run["platformCommands"]=[s.send("unityfabric","FABRIC_TOKEN"),s.send("unityneo","NEO_TOKEN")]
                with contextlib.ExitStack() as stack:
                    windows=[];run["players"]=[]
                    for name,d,suffix in zip((A,B),games,("a","b")):
                        w=Window(home,d,phase+"-"+suffix,port,installed,name,True);windows.append(w)
                        stack.callback(lambda w=w:exited(w));state=w.act("status",timeout=160)
                        s.expect(name+" joined the game",45)
                        if state["player"]!=name or not state["remote"] or not state["gpu"]:raise AssertionError(state)
                        run["players"].append({"pid":w.process.pid,**state})
                    for id in ("unity_fabric_sample:token","unity_neo_sample:token"):
                        line=s.send("unityregistry "+id,r"UNITY_REGISTRY .* item=\d+");num=int(re.search(r"item=(\d+)",line)[1])
                        for w in windows:
                            proof=w.act("registry",id=id)
                            if proof["itemNumber"]!=num or not proof["renderLayers"]:raise AssertionError(proof)
                            run.setdefault("platformModels",{}).setdefault(id,[]).append(proof)
                        if not restart:
                            for name in (A,B):s.send(f"give {name} {id}")
                        for name in (A,B):observed(s,f'data entity {name} Inventory[{{id:"{id}"}}]',"TOKEN_"+name+str(num))
                    run["shared"]=shared(s,*windows,restart);run["custom"]=custom(s,windows[0],restart)
                    if extras:run["distributors"]=routes(s)
                s.send("save-all flush","Saved the game",160)
            finally:s.close()
            run["exit"]=s.process.returncode
            logs=[EVIDENCE/(phase+".log"),*(EVIDENCE/(phase+"-"+q+"-client.log") for q in ("a","b"))]
            run["logs"]={p.name:digest(p) for p in logs}
            st=logs[0].read_text(encoding="utf8");digests=re.findall(r"Verified client content registry ([0-9a-f]{64})",st)
            if "Couldn't parse data file" in st:raise AssertionError("Unresolved author data pack entries")
            if len(digests)!=2 or len(set(digests))!=1:raise AssertionError(digests)
            run["digest"]=digests[0]
            for txt in ["SERVERBOUND unity_graphical_qa:inspect_open_menu Server thread"]+([] if restart else ["SERVERBOUND inventorystorages:c2s_set_slot_id Server thread","SERVERBOUND laserbladetools:colorizer Server thread"]):
                if txt not in st:raise AssertionError(txt)
            for p in logs[1:]:
                if "CLIENTBOUND unity_graphical_qa:open_menu_slot Render thread" not in p.read_text(encoding="utf8",errors="replace"):raise AssertionError("Missing clientbound handler")
            if extras:
                for marker in ("UNITY_ABOUT_TO_START","UNITY_STARTING","UNITY_AUTO_SUBSCRIBER","UNITY_STARTED_ORDER","UNITY_STOPPING","UNITY_STOPPED","UNITY_LOGIN "+A,"UNITY_LOGIN "+B,"UNITY_LOGOUT "+A,"UNITY_LOGOUT "+B,"aa_bridge"):
                    if marker not in st:raise AssertionError(marker)
                if not st.index("UNITY_ABOUT_TO_START")<st.index("UNITY_STARTING")<st.index("UNITY_STARTED_ORDER")<st.index("UNITY_STOPPING")<st.index("UNITY_STOPPED"):raise AssertionError("Server lifecycle order")
                for log in logs[1:]:
                    clientText=log.read_text(encoding="utf8",errors="replace")
                    if "UNITY_RENDERER_TARGET" not in clientText or clientText.count("UNITY_EARLY_SETUP")!=1 or "qa-render.json" not in clientText:raise AssertionError("Client setup or Mixin declaration")
                if "qa-render.json" in st:raise AssertionError("Client Mixin on server")
                content_line=next(l for l in st.splitlines() if "[Unity] Registered content " in l)
                if "aa_bridge" not in content_line:raise AssertionError("Tab-only mod missing from content digest")
            for d,label in ((game,"server"),(games[0],"a"),(games[1],"b")):
                allowed=client_libraries(home) if d!=game else {p.resolve():"Mojang library" for p in (cache/"libraries").rglob("*.jar")}
                allowed[(home/"versions/26.2/26.2.jar" if d!=game else cache/"versions/26.2/server-26.2.jar").resolve()]="Mojang game"
                allowed[(installed if d!=game else jar).resolve()]="unity-loader"
                for area in ("mods","unity-mods"):
                    for f in (d/area).glob("*.jar"):allowed[f.resolve()]=f.name
                trace=phase+("" if d==game else "-"+label)+"-classes.log"
                audit=class_audit(d/trace,allowed,{"mc.unity.core.Content":"unity-loader","sample.FabricToken":"unity_fabric_sample.jar","sample.NeoToken":"unity_neo_sample.jar"})
                for f in extras:
                    if not audit["fileOrigins"].get(f.name):raise AssertionError("Missing interop classes: "+f.name)
                for e in common+(commands if d==game else []):
                    if not audit["fileOrigins"].get(e["filename"]) or digest(d/"mods"/e["filename"],"sha512")!=e["sha512"]:raise AssertionError(e["filename"])
                run[label+"Audit"]=audit
            print("PASS",phase,flush=True)
        first,last=result["runs"].values()
        if first["shared"]["fullInventories"]!=last["shared"]["fullInventories"]:raise AssertionError("Full player inventory changed across restart")
        if first["digest"]!=last["digest"] or [p["uuid"] for p in first["players"]]!=[p["uuid"] for p in last["players"]]:raise AssertionError("Restart identities")
    temp=EVIDENCE/"dual.tmp";temp.write_text(json.dumps(result,indent=2));temp.replace(EVIDENCE/"dual.json")

def routes(server):
    base=secrets.randbelow(1000000)*3
    def wait_for(command,pattern,suffix):
        end=time.monotonic()+30
        while time.monotonic()<end:
            line=server.send(command,pattern)
            if line.endswith(suffix):return line
            time.sleep(.15)
        raise AssertionError((command,suffix,line))
    together=f"[{A}, {B}]"; single=f"[{A}]"
    wait_for(f"execute as {A} run unitywatch",r"WATCH \[",together)
    expected={base:single,base+1:together,base+2:together}
    def send(n): server.send(f"execute as {A} run unityroute {n}")
    for n,want in expected.items():
        send(n);wait_for("unityack "+str(n),"ACK "+str(n),want)
    server.send("forceload add 1500 1500");observed(server,"loaded 1500 299 1500","FAR_READY")
    server.send("setblock 1500 299 1500 minecraft:stone")
    server.send(f"tp {B} 1500.5 300 1500.5","Teleported")
    wait_for(f"execute as {A} run unitywatch",r"WATCH \[",single)
    expected[base+5]=single;send(base+5)
    expected[base+7]=together;send(base+7)
    # The ALL message is an ordered fence, including the far player's connection.
    wait_for("unityack "+str(base+7),"ACK "+str(base+7),together)
    result={}
    for n,want in expected.items():
        line=server.send("unityack "+str(n),"ACK "+str(n))
        if not line.endswith(want):raise AssertionError(line)
        result[str(n)]=line.split("ACK ",1)[1]
    server.send(f"tp {B} 104.1 136 133.1 0 12","Teleported")
    wait_for(f"execute as {A} run unitywatch",r"WATCH \[",together)
    server.send("forceload remove 1500 1500")
    return result

def exited(w):
    if w.close()!=0:raise RuntimeError("Graphical client failed")

if __name__=="__main__":
    p=argparse.ArgumentParser()
    for n in ("client-home","bundle","samples","eula-from","platform-samples"):p.add_argument("--"+n,required=True,type=Path)
    p.add_argument("--interop-samples",type=Path)
    run(p.parse_args())
