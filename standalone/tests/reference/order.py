"""Observe a normal hotbar change followed by a mod payload, before and after a player respawn."""
import argparse,contextlib,json,os,shutil,socket,subprocess,sys,tempfile,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import acceptance,content
from acceptance import ROOT,DIST,VERSION,Server,digest
from content import Window
from gameplay import eventually
from dual import exited
def run(o):
    out=o.out;out.mkdir(parents=True,exist_ok=True);acceptance.EVIDENCE=content.EVIDENCE=out
    assert "eula=true" in o.eula.read_text()
    c=ROOT/"cache"/VERSION;home=c/"client-home";bundle=c/"mojang-server-26.2.jar"
    with tempfile.TemporaryDirectory(dir=ROOT/".work") as tmp:
        t=Path(tmp);classes=t/"classes";classes.mkdir();server=t/"server";client=t/"client"
        cp=[o.unity,home/"versions/26.2/26.2.jar",*sorted((c/"libraries").rglob("*.jar"))]
        subprocess.run(["javac","--release","25","-g:none","-cp",os.pathsep.join(map(str,cp)),"-d",classes,Path(__file__).with_name("OrderProbe.java")],check=True)
        for g in [server,client]:
            native=g/"unity-mods";native.mkdir(parents=True)
            with zipfile.ZipFile(native/"order.jar","w") as z:
                for f in sorted(classes.rglob("*.class")):z.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix()),f.read_bytes())
                z.writestr(zipfile.ZipInfo("META-INF/unity.properties"),"api=1\nid=referenceorder\nentrypoint=qa.reference.OrderProbe\n")
        shutil.copy2(DIST/"unity-client-witness.jar",client/"unity-mods")
        (client/"options.txt").write_text("onboardAccessibility:false\npauseOnLostFocus:false\nrenderDistance:3\nfullscreen:false\n")
        shutil.copy2(o.eula,server/"eula.txt")
        with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
        (server/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nlevel-type=minecraft:flat\ngenerate-structures=false\n")
        with contextlib.ExitStack() as stack:
            srv=Server(server,"order",o.unity,bundle,c);stack.callback(srv.close);srv.expect("Standalone ready",120)
            win=Window(home,client,"order",port,o.unity,"OrderPlayer");stack.callback(exited,win)
            status=win.act("status",timeout=160);assert status["remote"] and not status["visible"]
            srv.expect("REFERENCE ordered slot 3 currentPlayer=true",30)
            srv.send("damage OrderPlayer 1000 minecraft:generic_kill")
            eventually(lambda:win.act("qaPlayer"),lambda p:p["dead"])
            win.act("qaRespawn")
            srv.expect("REFERENCE ordered slot 5 currentPlayer=true",40)
        (out/"order.json").write_text(json.dumps({"runtime":digest(o.unity),"slots":[3,5],"oldContextResolvesCurrentPlayer":True,"logs":{p.name:digest(p) for p in out.glob("*.log")}},indent=2))
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ("unity","out","eula"):p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    run(p.parse_args())
