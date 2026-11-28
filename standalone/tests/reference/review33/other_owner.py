"""Replay the previously denied ore coordinates with a different physical placer, on a copy of the accepted save."""
import argparse,contextlib,json,os,shutil,socket,subprocess,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path[:0]=[str(HERE),str(HERE.parent),str(HERE.parent.parent)]
import acceptance,content,beta25 as qa,machines
from gameplay import eventually
from dual import exited

def run(o):
    report=json.loads((o.source/"result.json").read_bytes())
    engine=o.dist/"unity-loader-0.3.3-Beta.jar";assert acceptance.digest(engine)==report["engine"]
    original=o.source/"instance";o.out.mkdir();copy=o.out/"instance";g=copy/"server";g.mkdir(parents=True)
    for name in ("world","mods","plugins","config","serverconfig","unity-mods","defaultconfigs"):
        p=original/"server"/name
        if p.is_dir():shutil.copytree(p,g/name)
    for filename in ("server.properties","eula.txt","usercache.json"):
        p=original/"server"/filename
        if p.is_file():shutil.copy2(p,g/filename)
    with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
    prop=g/"server.properties"
    import re
    prop.write_text(re.sub(r"(?m)^server-port=\d+$","server-port="+str(port),prop.read_text("utf8")),"utf8")
    clients=[copy/n for n in qa.NAMES]
    for n,d in zip(qa.NAMES,clients):
        d.mkdir()
        for name in ("mods","unity-mods"):shutil.copytree(original/n/name,d/name)
        shutil.copy2(original/n/"options.txt",d/"options.txt")
    os.environ["PAPER_DEFAULT_CENTRAL_REPOSITORY"]="https://repo.maven.apache.org/maven2/"
    acceptance.EVIDENCE=content.EVIDENCE=o.out
    bundle=o.cache/"mojang-server-26.2.jar"
    cmd=["java","-Xmx2g","-XX:ActiveProcessorCount=4","-Dmc.unity.machineQA=true","-Dmc.unity.contentQA=true","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-jar",str(engine),"--server-jar",str(bundle),"--runtime-dir",str(o.cache),"nogui"]
    phases=[]
    for first in [True,False]:
        phase="replace" if first else "restart";s=acceptance.Server(g,phase,engine,bundle,o.cache,cmd)
        state={"phase":phase,"players":[]}
        try:
            s.expect("Standalone ready",150)
            with contextlib.ExitStack() as clean:
                ws=[]
                for n,d in zip(qa.NAMES,clients):
                    w=content.Window(o.cache/"client-home",d,phase+n,port,engine,n);ws.append(w);clean.callback(exited,w)
                    v=w.act("status",timeout=180);assert v["remote"] and v["gpu"] and not v["visible"]
                    state["players"].append({"name":n,"pid":w.process.pid,"visible":False})
                if first:
                    a,b=ws
                    for x,ore in ((66,"gold_block"),(67,"emerald_block"),(68,"lapis_block")):machines.check_block(s,ws,x,80,25,"minecraft:"+ore)
                    before=s.send("data get block 64 81 24","block data:");assert "VolumeA" in before and "unity:machine_owner" in before
                    s.send("setblock 64 81 24 air","Changed the block");s.send("setblock 69 80 25 copper_block")
                    s.send("tp VolumeB 64.5 81.0 21.65 0 0","Teleported")
                    eventually(lambda:b.act("qaPlayer"),lambda v:abs(v["x"]-64.5)<.09 and abs(v["y"]-81)<.09)
                    machines.give(s,b,"VolumeB",7,"quietquarry:quarry_core")
                    b.act("qaRight",slot=7,x=64,y=80,z=24,top=True)
                    content.observed(s,"block 64 81 24 quietquarry:quarry_core","U33_SECOND_OWNER")
                    # Author serialization uses a child compound with the signed int energy.
                    s.send("data merge block 64 81 24 {Energy:{energy:1200}}","Modified block data")
                for x,ore in ((66,"air"),(67,"air"),(68,"lapis_block"),(69,"air")):machines.check_block(s,ws,x,80,25,"minecraft:"+ore)
                core=s.send("data get block 64 81 24","block data:")
                assert "VolumeB" in core and "VolumeA" not in core and "unity:machine_owner" in core
                s.send("u33quarry",r"U33 QUARRY FE 0\b")
                for ore in ("gold_block","emerald_block","copper_block"):assert '"minecraft:'+ore+'"' in core
                assert '"minecraft:lapis_block"' not in core
                machines.chest(s,66,"diamond",9);machines.chest(s,69,"emerald",12)
                state["core"]=core.split("block data:")[-1].strip()
            s.send("save-all flush","Saved the game",120)
        finally:s.close()
        phases.append(state)
    cp=os.pathsep.join(map(str,[o.cache/"versions/26.2/server-26.2.jar",*sorted((o.cache/"libraries").rglob("*.jar"))]))
    cls=o.out/"nbt-reader";cls.mkdir()
    subprocess.run(["javac","-proc:none","--release","25","-cp",cp,"-d",cls,HERE/"SavedMachines.java",HERE/"SavedSecond.java",HERE.parent/"review30/SavedWorld.java"],check=True)
    subprocess.run(["java","-cp",str(cls)+os.pathsep+cp,"SavedSecond",g/"world",o.out/"world.json"],check=True)
    result={"engine":report["engine"],"sourceReport":acceptance.digest(o.source/"result.json"),"runs":phases,"logs":{p.name:acceptance.digest(p) for p in sorted(o.out.glob("*.log"))}}
    (o.out/"result.json").write_text(json.dumps(result,indent=2),"utf8")
if __name__=="__main__":
    parser=argparse.ArgumentParser()
    for key in ("source","out","dist","cache"):parser.add_argument("--"+key,required=True,type=lambda s:Path(s).resolve())
    run(parser.parse_args())
