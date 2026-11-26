"""Verify authoritative Bukkit inventory/damage decisions with unmodified plugins and two rendered clients."""
import argparse, contextlib, json, os, re, shutil, socket, subprocess, tempfile, time, zipfile
from pathlib import Path
from acceptance import ROOT, VERSION, DIST, EVIDENCE, Server, digest
from content import Window, observed, slot
from dual import exited
from gameplay import eventually,teleport
from server_configs import line

NAMES = ("LedgerA","LedgerB")
def install_probes(folder,cache):
    sources=ROOT/"standalone/tests/bukkit"
    cp=[*(ROOT/"standalone/vendor/bukkit").glob("*.jar"),*(cache/"libraries").rglob("*.jar")]
    with tempfile.TemporaryDirectory(dir=folder) as tmp:
        classes=Path(tmp)
        subprocess.run(["javac","-proc:none","-cp",os.pathsep.join(map(str,cp)),"-d",classes,sources/"InventoryAudit.java",sources/"deps/Types.java"],check=True)
        for name,package,main,meta in [
            ("Unity.Types","libc","qa.libc.Types","provides: [Inventory.Alias]"),
            ("Unity.InventoryQA","inventory","qa.inventory.InventoryAudit","depend: [Inventory.Alias]\ncommands:\n  inventoryaudit: {}")]:
            with zipfile.ZipFile(folder/(name+".jar"),"w") as z:
                for f in (classes/"qa"/package).rglob("*.class"): z.write(f,f.relative_to(classes))
                z.writestr("plugin.yml",f'name: {name}\nversion: 1\napi-version: "26.2"\nmain: {main}\n{meta}\n')
def use_originals(rows,src,dest):
    for row in rows:
        f=src/row["filename"]
        assert digest(f,"sha512")==row["sha512"],f
        shutil.copy2(f,dest)
def inv(player, index, button=0):
    return player.act("menuClick",index=0,inventory=index,button=button)
def state(w, cursor, counts):
    def match(view):
        return (view["carried"]["id"],view["carried"]["count"])==cursor and all(
            (view["slots"][i]["id"],view["slots"][i]["count"])==v for i,v in counts.items())
    current=eventually(lambda:w.act("menuInfo"),match)
    return {"cursor":current["carried"],"slots":{i:current["slots"][i] for i in counts}}
def health(s,ws,n):
    raw=s.send("data get entity "+n+" Health",r"entity data: [0-9.]+f")
    amount=float(re.search(r"entity data: ([0-9.]+)f",raw)[1])
    vals=[eventually(lambda:w.act("qaPlayer",name=n),lambda v:abs(v["health"]-amount)<0.01)["health"] for w in ws]
    return [amount,*vals]
def run(o):
    c=ROOT/"cache"/VERSION;jar=next(o.dist.resolve().glob("unity-loader*.jar"));bundle=c/"mojang-server-26.2.jar"
    authors=json.loads((ROOT/"standalone/bukkit-inventory-samples.json").read_bytes())
    corpus=json.loads((ROOT/"standalone/performance-pack.json").read_bytes())["files"]
    extra=json.loads((ROOT/"standalone/universal-samples.json").read_bytes())+[json.loads((ROOT/"standalone/forge-key-sample.json").read_bytes())]
    legacy=json.loads((ROOT/"tools/plugins.lock.json").read_bytes())
    result={"runtime":digest(jar),"authors":authors,"mixed":{},"runs":[]}
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="inventory24-") as tmp:
        base=Path(tmp);server=base/"server";games=[base/n for n in NAMES]
        for g in [server,*games]:
            mods=g/"mods";mods.mkdir(parents=True);native=g/"unity-mods";native.mkdir()
            files=c/("performance-samples" if g==server else "performance-client-samples")
            used=[r for r in corpus if (files/r["filename"]).is_file()]
            assert len(used)==(73 if g==server else 71)
            use_originals(used,files,mods);use_originals(extra,o.samples,mods)
            result["mixed"][g.name]={r["filename"]:r["sha512"] for r in used+extra}
            shutil.copy2(next((ROOT/"standalone/vendor").glob("fabric-api-*.jar")),mods)
            if g!=server:
                shutil.copy2(o.dist/"unity-client-witness.jar",native)
                (g/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:3\nmaxFps:30\nfullscreen:false\ntutorialStep:none\n")
        plugs=server/"plugins";plugs.mkdir()
        use_originals(authors,o.authors,plugs);use_originals(legacy,o.plugins,plugs);install_probes(plugs,c)
        for name,config in [("KeepChunks","updates:\n  check: false\n  notify: false\n"),("bStats","enabled: false\n"),("BundleBan","banned-items: [IRON_INGOT]\ndeny-message: 'BundleBan denied %item%'\n")]:
            place=plugs/name;place.mkdir();(place/"config.yml").write_text(config)
        assert re.search(r"(?im)^eula\s*=\s*true\s*$",o.eula.read_text());shutil.copy2(o.eula,server/"eula.txt")
        with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
        (server/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\npause-when-empty-seconds=-1\nlevel-seed=2424\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\n")
        empty=("minecraft:air",0); eggs=("minecraft:dragon_egg",6);iron=("minecraft:iron_ingot",4)
        chest={0:empty,1:empty,3:("minecraft:bundle",1),5:("minecraft:apple",5),6:("minecraft:apple",5)}
        for phase in ["first","restart"]:
            label="bukkit-transaction-"+phase;log=EVIDENCE/(label+".log");report={}
            with contextlib.ExitStack() as stack:
                s=Server(server,label,jar,bundle,c);stack.callback(s.close);s.expect("Standalone ready",190)
                for n in ["NoHidingDragonEggs","BundleBan","NoPvP","Unity.InventoryQA"]:line(log,"Enabled "+n+" ")
                if phase=="first":
                    s.send("forceload add 1360 1360");observed(s,"loaded 1360 201 1360","DISK_READY",120)
                    s.send("setblock 1360 201 1360 minecraft:emerald_block")
                    s.send("save-all flush","Saved the game",180);s.send("forceload remove 1360 1360")
                else:
                    s.send("inventoryaudit","INVENTORY24 DISK LOAD PASS")
                ws=[]
                for g,n in zip(games,NAMES):
                    w=Window(c/"client-home",g,label+"-"+n,port,jar,n);ws.append(w);stack.callback(exited,w)
                a,b=ws
                statuses=[w.act("status",timeout=200) for w in ws]
                assert all(v["gpu"] and v["remote"] and not v["visible"] for v in statuses)
                report["uuids"]=[v["uuid"] for v in statuses]
                s.send("forceload add 40 15 47 21");observed(s,"loaded 44 201 18","AREA_READY",30)
                if phase=="first":
                    s.send("gamerule minecraft:natural_health_regeneration false");s.send("gamerule minecraft:keep_inventory true");s.send("difficulty normal")
                    s.send("fill 40 200 15 47 200 21 minecraft:stone");s.send("fill 40 201 15 47 204 21 minecraft:air")
                    s.send("setblock 44 201 18 minecraft:chest");s.send("item replace block 44 201 18 container.3 with minecraft:bundle")
                    teleport(s,a,"LedgerA",44.5,201,15.7);teleport(s,b,"LedgerB",44.5,201,20.6)
                    s.send("effect give @a minecraft:instant_health 1 9 true")
                    for n in NAMES:s.send("clear "+n)
                    s.send("item replace entity LedgerA hotbar.1 with minecraft:dragon_egg 6")
                    s.send("item replace entity LedgerA hotbar.2 with minecraft:iron_ingot 4")
                    s.send("item replace entity LedgerB hotbar.2 with minecraft:apple 10")
                    s.send("item replace entity LedgerB hotbar.1 with minecraft:emerald 3")
                    s.send("spawnpoint LedgerA 44 201 16");s.send("experience set LedgerA 8 levels")
                    line(log,"INVENTORY24 LEVEL LedgerA 0 -> 8")
                    s.send("damage LedgerA 1000 minecraft:generic_kill");eventually(lambda:a.act("qaPlayer"),lambda q:q["dead"]);a.act("qaRespawn")
                    eventually(lambda:a.act("qaPlayer"),lambda q:not q["dead"])
                    s.send("experience set LedgerA 11 levels")
                    report["respawnLevel"]=s.expect(r"INVENTORY24 LEVEL LedgerA \d+ -> 11",15).split("INVENTORY24 ")[-1]

                teleport(s,a,"LedgerA",44.5,201,15.7);teleport(s,b,"LedgerB",44.5,201,20.6)
                for w in ws:w.act("menuOpen",handSlot=0,x=44,y=201,z=18,timeout=45)
                if phase=="first":
                    inv(a,1);state(a,eggs,{0:empty});a.act("menuClick",index=0,button=0)
                    line(log,"InventoryClickEvent 0/LEFT LOWEST=false MONITOR=true")
                    a.act("menuDrag",indices=[0,1],button=0)
                    line(log,"InventoryDragEvent [0, 1]/DRAGON_EGG LOWEST=false MONITOR=true")
                    report["deniedDrag"]=[state(a,eggs,{0:empty,1:empty}),state(b,empty,{0:empty,1:empty})]
                    s.send("inventoryaudit LedgerA","CURSOR LedgerA DRAGON_EGG 6")
                    observed(s,'data block 44 201 18 {Items:[{Slot:3b,id:"minecraft:bundle"}]}',"HAS_BUNDLE")
                    for i in (0,1):s.send(f'execute unless data block 44 201 18 Items[{{Slot:{i}b}}] run say EMPTY{i}',fr'\[Server\] EMPTY{i}')
                    inv(a,1);inv(a,2);a.act("menuClick",index=3,button=0)
                    line(log,"InventoryClickEvent 3/LEFT LOWEST=false MONITOR=true")
                    report["deniedBundle"]=[state(a,iron,{3:chest[3]}),state(b,empty,{3:chest[3]})]
                    s.send("inventoryaudit LedgerA","CURSOR LedgerA IRON_INGOT 4");inv(a,2)
                    inv(b,2);b.act("menuDrag",indices=[5,6],button=0)
                    line(log,"InventoryDragEvent [5, 6]/APPLE LOWEST=false HIGHEST MONITOR=false")
                    state(b,empty,{5:("minecraft:apple",5),6:("minecraft:apple",5)})
                report["container"]=[state(w,empty,chest) for w in ws]
                for i in (5,6):observed(s,f'data block 44 201 18 Items[{{Slot:{i}b,id:"minecraft:apple",count:5}}]',"APPLE"+str(i))
                report["serverContainer"]=s.send("data get block 44 201 18 Items","following block data").split("following block data: ",1)[1]
                for w in ws:w.act("menuClose")
                for n,w,i,item,count in [("LedgerA",a,1,"dragon_egg",6),("LedgerA",a,2,"iron_ingot",4),("LedgerB",b,1,"emerald",3)]:
                    slot(w,i,"minecraft:"+item,count)
                    observed(s,f'data entity {n} Inventory[{{Slot:{i}b,id:"minecraft:{item}",count:{count}}}]',"BAG_"+item)
                report["bags"]=[w.act("qaPlayer")["bag"] for w in ws]
                report["serverBags"]=[s.send("data get entity "+n+" Inventory",r"entity data: \[") for n in NAMES]
                for bag,raw in zip(report["bags"],report["serverBags"]):
                    saved={}
                    for entry in re.findall(r"\{[^{}]*\}",raw):
                        index=re.search(r"Slot:\s*(-?\d+)b",entry)
                        item=re.search(r'id:\s*"([^"]+)"',entry)
                        count=re.search(r"count:\s*(\d+)",entry)
                        if index and item and count:
                            saved[index[1]]=f"{item[1]}*{count[1]}@0"
                    assert bag==saved,(bag,raw)
                if phase=="first":
                    teleport(s,a,"LedgerA",42.5,201,16.2);teleport(s,b,"LedgerB",42.5,201,18.5)
                    before=health(s,ws,"LedgerB");a.act("qaAttack",name="LedgerB",slot=0)
                    line(log,"EntityDamageByEntityEvent ENTITY_ATTACK LOWEST=false MONITOR=true")
                    assert health(s,ws,"LedgerB")==before
                    report["cancelledHealth"]=before
                    s.send("damage LedgerB 3 minecraft:generic")
                    line(log,"EntityDamageEvent CUSTOM LOWEST=false HIGHEST MONITOR=false")
                report["health"]=[health(s,ws,n) for n in NAMES]
                assert report["health"]==[[20]*3,[17]*3],report["health"]
                for w in ws:exited(w)
                s.send("save-all flush","Saved the game",180)
                result["runs"].append(report)
            text=log.read_text("utf8")
            assert "failed handling " not in text and "INVENTORY24" in text
        for key in ["uuids","bags","health","serverContainer"]:
            assert result["runs"][0][key]==result["runs"][1][key],key
        result["logs"]={f.name:digest(f) for f in EVIDENCE.glob("bukkit-transaction-*.log")}
        (EVIDENCE/"inventory24.json").write_text(json.dumps(result,indent=2),"utf8")
        print("PASS original plugins / two clients / restart")
if __name__=="__main__":
    parser=argparse.ArgumentParser()
    for key in ["samples","plugins","authors","eula"]:parser.add_argument("--"+key,required=True,type=Path)
    parser.add_argument("--dist",type=Path,default=DIST)
    run(parser.parse_args())
