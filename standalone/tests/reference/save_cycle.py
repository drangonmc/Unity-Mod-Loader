"""Check real chunk persistence across a normal stop and an orderly JVM shutdown in disposable worlds."""
import argparse, contextlib, json, os, shutil, subprocess, sys, tempfile, zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import acceptance
from acceptance import ROOT,VERSION,Server,digest
def run(jar,eula,out,windows_control=False):
    out.mkdir(parents=True,exist_ok=True); acceptance.EVIDENCE=out
    assert "eula=true" in eula.read_text()
    cache=ROOT/"cache"/VERSION; bundle=cache/"mojang-server-26.2.jar"
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="save-reference-") as temp:
        tmp=Path(temp); classes=tmp/"classes";classes.mkdir()
        cp=[jar,cache/"versions/26.2/server-26.2.jar",*sorted((cache/"libraries").rglob("*.jar"))]
        subprocess.run(["javac","--release","25","-g:none","-cp",os.pathsep.join(map(str,cp)),"-d",classes,Path(__file__).with_name("SaveProbe.java")],check=True)
        results={}
        for mode in (["ctrl-c"] if windows_control else ["console","jvm"]):
            game=tmp/mode;(game/"unity-mods").mkdir(parents=True)
            with zipfile.ZipFile(game/"unity-mods/reference.jar","w") as z:
                for f in classes.rglob("*.class"):z.write(f,f.relative_to(classes))
                z.writestr("META-INF/unity.properties","api=1\nenvironment=server\nid=save-reference\nentrypoint=qa.reference.SaveProbe\n")
            shutil.copy2(eula,game/"eula.txt")
            (game/"server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\n")
            for step in ["write","read"]:
                name=mode+"-"+step
                with contextlib.closing(Server(game,name,jar,bundle,cache,private_console=mode=="ctrl-c" and step=="write")) as srv:
                    srv.expect("Standalone ready",150)
                    if step=="write":
                        srv.send("refseed","REFERENCE command refseed")
                        srv.send("save-all flush","Saved the game",180)
                        srv.send("refupdate","REFERENCE command refupdate")
                        if mode=="ctrl-c":
                            subprocess.run([sys.executable,Path(__file__).with_name("ctrl_c.py"),str(srv.process.pid)],
                                           check=True,creationflags=subprocess.CREATE_NO_WINDOW)
                        else:srv.send("refexit" if mode=="jvm" else "stop")
                        srv.process.wait(180)
                        assert any(p.read_text()=="after exception" for p in game.rglob("listener-ran")),mode
                    else:
                        srv.send("refread","REFERENCE read 7 14 true")
            results[mode]={"lastGood":7,"other":14,"goldBlock":True,"nextListener":True}
        data={"runtime":digest(jar),"results":results,"logs":{p.name:digest(p) for p in out.glob("*.log")}}
        (out/"saves.json").write_text(json.dumps(data,indent=2),"utf8")
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ("unity","eula","out"):p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    p.add_argument("--windows-control",action="store_true")
    o=p.parse_args();run(o.unity,o.eula,o.out,o.windows_control)
