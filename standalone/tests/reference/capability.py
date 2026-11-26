"""Exercise registration diagnostics and adapter capability failures on a synthetic cancellable event."""
import argparse,contextlib,json,os,shutil,subprocess,sys,tempfile,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import acceptance
from acceptance import ROOT,VERSION,Server,digest
def run(o):
    o.out.mkdir(parents=True,exist_ok=True);acceptance.EVIDENCE=o.out
    c=ROOT/"cache"/VERSION;bundle=c/"mojang-server-26.2.jar";assert "eula=true" in o.eula.read_text()
    result={}
    with tempfile.TemporaryDirectory(dir=ROOT/".work") as tmp:
        t=Path(tmp);classes=t/"classes";classes.mkdir()
        cp=[*(ROOT/"standalone/vendor/bukkit").glob("*.jar"),*(c/"libraries").rglob("*.jar")]
        subprocess.run(["javac","-proc:none","-g:none","-cp",os.pathsep.join(map(str,cp)),"-d",classes,Path(__file__).with_name("CapabilityProbe.java")],check=True)
        for mode in ["warn","strict"]:
            g=t/mode;(g/"plugins").mkdir(parents=True)
            with zipfile.ZipFile(g/"plugins/capability.jar","w") as z:
                for f in classes.rglob("*.class"):z.write(f,f.relative_to(classes))
                z.writestr("plugin.yml",'name: CapabilityProbe\nversion: 1\napi-version: "26.2"\nmain: qa.reference.CapabilityProbe\n')
            (g/"server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\n")
            shutil.copy2(o.eula,g/"eula.txt")
            cmd=["java","-Xmx1g","-XX:ActiveProcessorCount=4","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8",
                 "-Dmc.unity.strictBukkitEvents="+str(mode=="strict").lower(),"-jar",str(o.unity),"--server-jar",str(bundle),"--runtime-dir",str(c),"nogui"]
            with contextlib.closing(Server(g,mode,o.unity,bundle,c,command=cmd)) as s:
                s.expect("REFERENCE synthetic status cancelled",120);s.expect("Standalone ready",45)
            log=o.out/(mode+".log");text=log.read_text("utf8")
            warnings=text.count("Unity 尚未验证此事件的游戏触发路径 org.bukkit.event.block.NotePlayEvent")
            rejected=text.count("REFERENCE registration rejected")
            assert (warnings,rejected)==((1,0) if mode=="warn" else (0,2)),(warnings,rejected)
            result[mode]={"warnings":warnings,"rejected":rejected,"cancelled":True,"log":digest(log)}
    (o.out/"capability.json").write_text(json.dumps({"runtime":digest(o.unity),"outcomes":result},indent=2))
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ("unity","eula","out"):p.add_argument("--"+k,type=lambda s:Path(s).resolve(),required=True)
    run(p.parse_args())
