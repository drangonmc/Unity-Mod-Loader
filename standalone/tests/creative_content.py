"""Four unmodified content JARs, two nested probe packs, two game clients, full restart."""
import argparse,contextlib,hashlib,json,secrets,shutil,socket,subprocess,tempfile,time,zipfile,re
from pathlib import Path
from acceptance import ROOT,VERSION,DIST,EVIDENCE,Server,SERVER_HASH,clean_env,class_audit,client_libraries,digest
from content import Window,observed,slot,block
from dual import snapshot,exited
from gameplay import teleport,eventually
A,B="UnityBuilderA","UnityBuilderB"
CASES=[
 ("bedrock-crafter","bedrockcrafter:bedrock_lamp","block/bedrock_lamp","unity/bedrockcrafter-fabric"),
 ("extra-more-blocks","verticalslabs:blue_stained_glass_door","item/blue_stained_glass_door","unity/verticalslabs"),
 ("morebricks","morebricks:granite_bricks","block/granite_bricks","unity/morebricks"),
 ("macaws-fences-and-walls","mcwfences:acorn_metal_fence","block/acorn_fence_bottom","unity/mcwfences")]
def startup(s,a,b,first):
    s.send("forceload add 32 14 64 24")
    for x in (32,63):observed(s,f"loaded {x} 200 18","CHUNK_"+str(x))
    if first:
        s.send("fill 32 200 14 63 200 24 minecraft:stone");s.send("fill 32 201 14 63 207 24 minecraft:air")
        s.send(f"item replace entity {B} hotbar.0 with minecraft:amethyst_shard 7")
    for name,w,x,z in ((A,a,45.5,15.9),(B,b,45.5,21.5)):teleport(s,w,name,x,201,z)
def show(s,w,name,id,tab,filename):
    s.send("gamemode creative "+name);time.sleep(.5)
    result=w.act("qaTabScreen",tab=tab);time.sleep(.6)
    if result["selected"]!=tab or result["items"]<1:raise AssertionError(result)
    result["image"]=filename;result["sha256"]=snapshot(w,EVIDENCE/filename);w.act("qaCloseTab");s.send("gamemode survival "+name)
    return result
def check_content(s,windows,game,options,first,by_slug):
    result={"registrations":{},"blocks":{},"slots":{},"screens":{}}
    a,b=windows
    for index,(slug,id,texture,pack) in enumerate(CASES):
        ns,key=id.split(":");tex=f"textures/{texture}.png";spec=by_slug[slug]
        with zipfile.ZipFile(options.samples/spec["filename"]) as z:expected=hashlib.sha256(z.read(f"assets/{ns}/{tex}")).hexdigest()
        serverLine=s.send("unityregistry "+id,rf"UNITY_REGISTRY {id} item=\d+ block=\d+")
        numbers=[int(x) for x in re.search(r"item=(\d+) block=(\d+)",serverLine).groups()]
        results=[]
        for w in windows:
            view=w.act("registry",id=id);assets=w.act("qaNewContent",id=id,texture=f"{ns}:{tex}")
            if [view["itemNumber"],view["blockNumber"]]!=numbers or not view["renderLayers"] or view["assetPack"]!=pack or assets["pack"]!=pack or assets["textureSHA256"]!=expected or not assets["tabs"]:raise AssertionError((id,view,assets))
            results.append({"model":view,"texturesAndTabs":assets})
        result["registrations"][id]={"numbers":numbers,"clients":results}
        if first and index in (0,3):
            tab=next(t for t in results[0]["texturesAndTabs"]["tabs"] if t.startswith(ns+":"))
            result["screens"][id]=show(s,a if index==0 else b,A if index==0 else B,id,tab,f"creative-tab-{index}.png")
        x=38+index*3
        if first:
            teleport(s,a,A,x+.5,201,15.8)
            s.send(f"item replace entity {A} hotbar.{index} with {id} 3");slot(a,index,id,3)
            a.act("qaRight",slot=index,top=True,x=x,y=200,z=18)
        observed(s,f"block {x} 201 18 {id}","CUSTOM_BLOCK_"+str(x))
        result["blocks"][id]=[eventually(lambda:w.act("qaBlock",x=x,y=201,z=18),lambda v:v["id"]==id)["state"] for w in windows]
        observed(s,f'data entity {A} Inventory[{{Slot:{index}b,id:"{id}",count:2}}]',"BAG_"+str(index))
        result["slots"][id]=slot(a,index,id,2)["count"]
    result["neoPayloads"]=[]
    for w in windows:
        n=secrets.randbelow(2**28)+1;reply=w.act("qaNeoPayload",nonce=n)
        if reply["reply"]!=n:raise AssertionError(reply)
        result["neoPayloads"].append(n)
    result["authorityBag"]=s.send(f"data get entity {A} Inventory",r"following entity data: \[").split("following entity data: ",1)[1]
    return result
def transactions(s,a,b):
    x=58;target="59 200 18";teleport(s,a,A,x+.5,201,15.8)
    s.send(f"setblock {target} minecraft:barrel");s.send(f"item replace block {target} container.0 with minecraft:emerald 7")
    result={}
    for mode in ("pass","fail","commit"):
        id="qa_neo_content:"+mode
        s.send(f"item replace entity {A} hotbar.8 with {id} 3");slot(a,8,id,3)
        a.act("qaRight",x=x,y=200,z=18,slot=8,top=True)
        s.expect("QA_WRITE "+mode+" "+target.replace(" ",", "),15)
        if mode=="commit":
            s.expect("QA_PLACEMENT_SINGLE",12);s.expect("QA_COUNTER_PLACE",12)
        block="qa_neo_content:counter" if mode=="commit" else "minecraft:barrel";count=2 if mode=="commit" else 3
        observed(s,f"block {target} {block}","TX_"+mode)
        if mode!="commit":observed(s,'data block '+target+' Items[{Slot:0b,id:"minecraft:emerald",count:7}]',"NBT_"+mode)
        views=[eventually(lambda:w.act("qaBlock",x=59,y=200,z=18),lambda v:v["id"]==block)["state"] for w in (a,b)]
        observed(s,f'data entity {A} Inventory[{{Slot:8b,id:"{id}",count:{count}}}]',"HAND_"+mode);slot(a,8,id,count)
        result[mode]={"block":block,"hand":count,"views":views}
    return result
def run(o):
    EVIDENCE.mkdir(parents=True,exist_ok=True);bundle=o.bundle.resolve();home=o.client_home.resolve();cache=ROOT/"cache"/VERSION;eula=o.eula_from.read_text()
    if digest(bundle)!=SERVER_HASH or not re.search(r"(?im)^eula\s*=\s*true\s*$",eula):raise ValueError("Requires official game and accepted local EULA")
    jar=DIST/f"unity-loader-{VERSION}.jar";subprocess.run(["java","-jar",str(jar),"--install-client",str(home)],env=clean_env(),check=True)
    installed=home/f"libraries/mc/unity/unity-loader/{VERSION}/unity-loader-{VERSION}.jar"
    def spec(n):return json.loads((ROOT/"standalone"/n).read_bytes())
    common=sum((spec(n) for n in ("forge-content.json","forge-containers.json","ecosystem.json","creative-content.json")),[]);commands=spec("forge-samples.json")
    fixtures=[o.nested/(id+".jar") for id in ("qa_fabric_content","qa_neo_content")]
    nested=json.loads((o.nested/"nested-lock.json").read_bytes());approved={v:k for k,v in nested.items() if k!="rejected-helper"}
    result={"version":VERSION,"minecraft":"26.2","executables":{p.name:digest(p) for p in (jar,DIST/"unity-world-marker.jar",DIST/"unity-client-witness.jar")},"originals":{sp["filename"]:sp["sha512"] for sp in common+commands},"fixtures":{p.name:digest(p) for p in fixtures},"nested":nested,"runs":{}}
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="Unity content packs ") as tmp:
        base=Path(tmp);srv=base/"server";games=[base/"a",base/"b"]
        for d in (srv,*games):
            (d/"mods").mkdir(parents=True);(d/"unity-mods").mkdir()
            for row in common+(commands if d==srv else []):
                copy=Path(shutil.copy2(o.samples/row["filename"],d/"mods"))
                if digest(copy,"sha512")!=row["sha512"]:raise ValueError(copy)
            for p in fixtures:shutil.copy2(p,d/"mods")
            shutil.copy2(DIST/("unity-world-marker.jar" if d==srv else "unity-client-witness.jar"),d/"unity-mods")
            if d!=srv:(d/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:4\nsimulationDistance:3\nmaxFps:35\nfullscreen:false\ntutorialStep:none\n")
        with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
        (srv/"eula.txt").write_text(eula);(srv/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=3\nlevel-seed=261101\npause-when-empty-seconds=-1\n")
        for phase in ("new-content","new-restart"):
            first=phase=="new-content";r={};result["runs"][phase]=r;s=Server(srv,phase,jar,bundle,cache)
            try:
                s.expect("Standalone ready",150);r["banner"]=s.send("unity",r"standalone \| MC 26\.2");r["aw"]=s.send("unityawcheck","QA_ACCESS_FABRIC 42")
                with contextlib.ExitStack() as stack:
                    windows=[];r["players"]=[]
                    for name,d,letter in zip((A,B),games,("a","b")):
                        w=Window(home,d,phase+"-"+letter,port,installed,name,False);windows.append(w);stack.callback(exited,w)
                        status=w.act("status",timeout=150);s.expect(name+" joined the game",30)
                        if not status["remote"] or status["player"]!=name or not status["gpu"]:raise AssertionError(status)
                        r["players"].append({"pid":w.process.pid,**status})
                    startup(s,*windows,first)
                    if first:r["tx"]=transactions(s,*windows)
                    else:observed(s,"block 59 200 18 qa_neo_content:counter","TX_RESTORED")
                    r["content"]=check_content(s,windows,srv,o,first,{sp["slug"]:sp for sp in common})
                    r["bagB"]=s.send(f"data get entity {B} Inventory",r"following entity data: \[").split("following entity data: ",1)[1]
                    slot(windows[1],0,"minecraft:amethyst_shard",7)
                    if first:
                        teleport(s,windows[0],A,43.5,201,14.5);windows[0].act("look",x=43,y=201,z=18)
                        r["worldImage"]=snapshot(windows[0],EVIDENCE/"new-content-world.png")
                s.send("save-all flush","Saved the game",160)
            finally:s.close()
            logs=[EVIDENCE/(phase+suffix+".log") for suffix in ("","-a-client","-b-client")];r["logs"]={p.name:digest(p) for p in logs}
            st=logs[0].read_text("utf8");cs=[l.read_text("utf8",errors="replace") for l in logs[1:]]
            if first and (st.count("QA_COUNTER_PLACE 59, 200, 18")!=1 or st.count("QA_PLACEMENT_SINGLE 59, 200, 18")!=1):raise AssertionError("Repeated or skipped placement notifications")
            for text in (st,*cs):
                if "QA_NESTED_NEO 14 HM" not in text:raise AssertionError("Nested version or priority failure")
            for n,c,name in zip(r["content"]["neoPayloads"],cs,(A,B)):
                if f"QA_NEO_C2S {name} {n} Server thread" not in st or f"QA_NEO_S2C {name} {n} Render thread" not in c:raise AssertionError("Payload dispatch")
            for d,side in ((srv,"server"),(games[0],"a"),(games[1],"b")):
                allowed={p.resolve():"Mojang library" for p in (cache/"libraries").rglob("*.jar")} if d==srv else client_libraries(home)
                allowed[(jar if d==srv else installed).resolve()]="unity-loader"
                allowed[(cache/"versions/26.2/server-26.2.jar" if d==srv else home/"versions/26.2/26.2.jar").resolve()]="Mojang game"
                for area in ("mods","unity-mods"):
                    for p in (d/area).glob("*.jar"):allowed[p.resolve()]=p.name
                audit=class_audit(d/(phase+("" if d==srv else "-"+side)+"-classes.log"),allowed,{"qa.inner.Secret":"nested:fabric-inner","qa.lib.Version":"nested:selected-helper","qa.stage.Audit":"nested:middle"},approved)
                for sp in common+(commands if d==srv else []):
                    if not(d!=srv and sp["slug"]=="skc") and not audit["fileOrigins"].get(sp["filename"]):raise AssertionError((side,sp["filename"]))
                    if digest(d/"mods"/sp["filename"],"sha512")!=sp["sha512"]:raise ValueError("Changed original")
                r[side+"Audit"]=audit
            print("PASS",phase,flush=True)
        a,b=result["runs"].values()
        if [p["uuid"] for p in a["players"]]!=[p["uuid"] for p in b["players"]] or a["content"]["blocks"]!=b["content"]["blocks"] or a["content"]["authorityBag"]!=b["content"]["authorityBag"] or a["bagB"]!=b["bagB"]:raise AssertionError("Restart mismatched saved state")
    tmp=EVIDENCE/"new-content.tmp";tmp.write_text(json.dumps(result,indent=2));tmp.replace(EVIDENCE/"new-content.json")
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for n in ("bundle","samples","client-home","eula-from","nested"):p.add_argument("--"+n,type=Path,required=True)
    run(p.parse_args())
