"""Launch the original WorldEdit/WorldGuard pair and record the actual stage reached on Unity."""
import argparse,contextlib,json,shutil,sys,tempfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import acceptance
from acceptance import ROOT,Server,digest
from bukkit_transactions import use_originals

def run(o):
    assert "eula=true" in o.eula.read_text()
    rows=[r for r in json.loads(Path(__file__).with_name("protection-originals.json").read_bytes()) if r["slug"] in ("worldedit","worldguard")]
    o.out.mkdir(parents=True,exist_ok=True);acceptance.EVIDENCE=o.out
    cache=ROOT/"cache/0.2.4-Beta";bundle=cache/"mojang-server-26.2.jar"
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="admission25-") as t:
        game=Path(t);folder=game/"plugins";folder.mkdir();use_originals(rows,o.authors,folder)
        shutil.copy2(o.eula,game/"eula.txt")
        (game/"server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\n")
        cmd=["java","-XX:ActiveProcessorCount=4","-Xmx2g","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-jar",str(o.unity),"--server-jar",str(bundle),"--runtime-dir",str(cache),"nogui"]
        srv=Server(game,"admission",o.unity,bundle,cache,command=cmd)
        try:
            srv.expect("Standalone ready",150)
        except RuntimeError:
            if srv.process.poll() is None: srv.process.wait(20)
        finally:
            try:srv.close()
            except RuntimeError:
                if srv.process.returncode==0:raise
        file=o.out/"admission.log";log=file.read_text("utf8")
        ready="Standalone ready" in log
        enabled=all("Enabled "+name+" " in log for name in ("WorldEdit","WorldGuard"))
        reasons=[s for s in log.splitlines() if "Caused by:" in s or "does not implement" in s or "Unsupported" in s]
        result={"runtime":digest(o.unity),"authors":{r["slug"]:r["sha512"] for r in rows},"ready":ready,"enabled":enabled,"diagnostics":reasons[-10:],"log":digest(file)}
        (o.out/"admission.json").write_text(json.dumps(result,indent=2),"utf8");print(result)
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ("unity","authors","eula","out"):p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    run(p.parse_args())
