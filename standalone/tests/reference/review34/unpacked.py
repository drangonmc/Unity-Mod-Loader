"""Exercise the exact Windows launch/install scripts from the public distribution, with an empty plugins folder."""
import argparse,hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]))
import acceptance
def verify(o):
    dest=o.out;dest.mkdir(parents=True);delivery=dest/"extracted";delivery.mkdir()
    with zipfile.ZipFile(o.archive) as z:
        z.extractall(delivery)
    engine,=delivery.glob("*.jar");assert len(list(delivery.rglob("*.jar")))==1
    instance=delivery/"standalone-instance";instance.mkdir();shutil.copy2(o.eula,instance/"eula.txt")
    (instance/"server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n")
    acceptance.EVIDENCE=dest;cmd=["cmd.exe","/d","/c","Start-Unity.cmd","--server-jar",str(o.bundle)]
    for first in (True,False):
        s=acceptance.Server(delivery,"fresh" if first else "restart",engine,o.bundle,delivery/"cache",cmd)
        try:
            s.expect("Standalone ready; native \\[world-marker\\]",150)
            s.send("unitymark count","Unity marks saved: "+("0" if first else "1"))
            if first:
                s.send("forceload add 0 0")
                from content import observed
                observed(s,"loaded 1 84 1","U34_RELEASE_READY",45)
                s.send("unitymark place 1 84 1","Unity marker placed on game tick")
            else:s.send("execute if block 1 84 1 amethyst_block run say U34_DISK_OK",r"\[Server\] U34_DISK_OK")
        finally:s.close()
    assert not list((delivery/"plugins").glob("*.jar")) and not list((instance/"unity-mods").glob("*.jar"))
    launcher=dest/"launcher test"
    subprocess.run(["cmd.exe","/d","/c","Install-Unity-Client.cmd",str(launcher)],cwd=delivery,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=True)
    installed,=launcher.rglob(engine.name);assert acceptance.digest(installed)==acceptance.digest(engine)
    profile,=(launcher/"versions").rglob("*.json");assert json.loads(profile.read_bytes())["inheritsFrom"]=="26.2"
    assert not (delivery/"standalone-client/unity-mods/unity-client-witness.jar").exists()
    receipt=dict(archiveSHA256=acceptance.digest(o.archive),engineSHA256=acceptance.digest(engine),singleJar=True,firstBoot=True,restartCount=1,clientProfile=profile.stem)
    (dest/"result.json").write_text(json.dumps(receipt,indent=2),"utf8")
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ("archive","out","bundle","eula"):p.add_argument("--"+k,type=lambda v:Path(v).resolve(),required=True)
    verify(p.parse_args())
