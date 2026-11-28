"""Exercise identical plugin hashes on Paper 26.2, using the same real client binary and separate saves."""
import argparse,contextlib,json,os,shutil,socket,sys,time
from pathlib import Path
from full import base,Window,Server,exited,content,acceptance,eventually,plugin_build,verify,teleport,digest
import services
def run(o):
    paper=o.paper
    assert digest(paper)=="0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
    out=o.out;out.mkdir(parents=True)
    base.acceptance.EVIDENCE=content.EVIDENCE=out
    test=out/"instance";test.mkdir()
    o.regions=False;o.plain=True
    fixture=base.observer(test,o.cache)
    g,games,port,_,_=base.setup_game(o,test,True,fixture)
    pl=g/"plugins"
    for f in o.plugins.glob("*.jar"):shutil.copy2(f,pl)
    plugin_build(base.ROOT,next(o.plugins.glob("Vault*.jar")),o.cache,pl/"Unity29Probe.jar")
    (g/"cache").mkdir();shutil.copy2(o.cache/"mojang-server-26.2.jar",g/"cache/mojang_26.2.jar")
    with (g/"server.properties").open("a") as file:file.write("difficulty=normal\n")
    loader=o.dist/"unity-loader-0.2.9-Beta.jar";rows=[]
    command=["java","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Dterminal.jline=false","-Dterminal.ansi=false","-XX:ActiveProcessorCount=4","-Xmx2g","-jar",str(paper),"--nogui"]
    os.environ["PAPER_DEFAULT_CENTRAL_REPOSITORY"]="https://repo.maven.apache.org/maven2/"
    for phase in ("first","restart"):
        with contextlib.ExitStack() as done:
            s=Server(g,phase,loader,o.cache/"mojang-server-26.2.jar",o.cache,command);done.callback(s.close);s.expect(r"Done \([0-9.,]+s\)!",150)
            if phase=="first":
                s.send("gamerule minecraft:natural_health_regeneration false")
                s.send("gamerule minecraft:spawn_mobs false");s.send("time set noon")
                s.send("forceload add 24 8");s.send("fill 20 79 4 31 79 14 stone")
            ws=[]
            for gd,n in zip(games,base.NAMES):
                w=Window(o.cache/"client-home",gd,phase+n,port,loader,n);done.callback(exited,w)
                status=w.act("status",timeout=160);assert status["remote"] and status["gpu"] and not status["visible"];ws.append(w)
            if phase=="first":
                for i,(w,n) in enumerate(zip(ws,base.NAMES)):
                    s.send(f"minecraft:tp {n} {24.5+i*3} 80 8.5 0 0","Teleported")
                    eventually(lambda:w.act("qaPlayer"),lambda v:abs(v["x"]-(24.5+i*3))<0.4)
                    s.send("lp user "+n+" permission set unity29.confirm "+str(i==0).lower())
                time.sleep(2)
            record=[]
            for w,n in zip(ws,base.NAMES):
                mode="write" if phase=="first" else "read"
                w.act("qaCommand",cmd="u29 "+mode)
                message=s.expect("UNITY29 "+mode+" "+n+" permission=LuckPerms economy=EssentialsX Economy balance="+str(291 if n=="VolumeA" else 292),30)
                if mode=="write":
                    eventually(lambda:w.act("menuInfo"),lambda v:any(c["id"]=="minecraft:gold_ingot" and c["count"]==2 for c in v["slots"]))
                    w.act("menuClose")
                record.append(base.health(s,ws,n,17 if n=="VolumeA" else 15))
            record.append(services.homes(s,ws[0],phase=="first",True))
            rows.append(record)
            for w in ws:exited(w)
            s.send("save-all flush","Saved the game",120)
    assert rows[0]==rows[1]
    (out/"result.json").write_text(json.dumps({"runs":rows,"paper":digest(paper),"client":digest(loader),"probe":digest(pl/"Unity29Probe.jar"),
        "plugins":{f.name:digest(f,"sha512") for f in o.plugins.glob("*.jar")},"logs":{f.name:digest(f) for f in out.glob("*.log")}},indent=2),"utf8")
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for name in ("paper","dist","cache","eula","out","plugins"):p.add_argument("--"+name,type=lambda s:Path(s).resolve(),required=True)
    run(p.parse_args())
