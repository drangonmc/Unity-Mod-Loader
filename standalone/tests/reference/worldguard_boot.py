"""Historical 0.2.4 admission baseline. For 0.2.5 gameplay comparisons use beta25.py --regions."""
import argparse,contextlib,json,os,re,shutil,subprocess,sys,tempfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import acceptance
from acceptance import ROOT,VERSION,Server,digest
from bukkit_transactions import use_originals
def run(o):
    c=ROOT/"cache"/VERSION;bundle=c/"mojang-server-26.2.jar";assert "eula=true" in o.eula.read_text()
    spec=json.loads(Path(__file__).with_name("protection-originals.json").read_bytes())
    rows=[r for r in spec if r["slug"] in ("worldguard","worldedit")];assert len(rows)==2
    o.out.mkdir(parents=True,exist_ok=True);acceptance.EVIDENCE=o.out
    result={"authors":{r["slug"]:r["sha512"] for r in rows},"loader":digest(o.unity),"paper":digest(o.paper)}
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="wg-baseline-") as tmp:
        for side in ("paper","unity"):
            g=Path(tmp)/side;pl=g/"plugins";pl.mkdir(parents=True);use_originals(rows,o.authors,pl)
            shutil.copy2(o.eula,g/"eula.txt");(g/"server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nlevel-type=minecraft:flat\ngenerate-structures=false\nonline-mode=false\n")
            if side=="paper":
                (g/"cache").mkdir();shutil.copy2(bundle,g/"cache/mojang_26.2.jar")
                cmd=["java","-Xmx2g","-XX:ActiveProcessorCount=4","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Dterminal.jline=false","-jar",str(o.paper),"--nogui"]
                with contextlib.closing(Server(g,side,o.unity,bundle,c,command=cmd)) as s:
                    s.expect(r"Done \(",160)
                    result["paperVersionReply"]=s.send("version WorldGuard","WorldGuard version",30)
                raw=(o.out/"paper.log").read_text("utf8")
                assert "Enabling WorldGuard v7.0.19" in raw and "Error occurred while enabling WorldGuard" not in raw
            else:
                with (o.out/"unity.log").open("wb") as log:
                    proc=subprocess.run(["java","-Xmx2g","-XX:ActiveProcessorCount=4","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-jar",str(o.unity),"--server-jar",str(bundle),"--runtime-dir",str(c),"nogui"],cwd=g,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,timeout=160,creationflags=subprocess.CREATE_NO_WINDOW)
                raw=(o.out/"unity.log").read_text("utf8")
                assert "Unity plugin API does not implement Server.getHelpMap" in raw and "Standalone ready" not in raw
                result["unityFirstGap"]="WorldEdit startup: Server.getHelpMap"
    result["logs"]={n:digest(o.out/n) for n in ["paper.log","unity.log"]}
    (o.out/"result.json").write_text(json.dumps(result,indent=2),"utf8")
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ["authors","unity","paper","eula","out"]:p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    run(p.parse_args())
