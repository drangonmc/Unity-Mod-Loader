"""Observe the host QA first-emission log under concurrent, in-process fixture delivery."""
from pathlib import Path
import argparse, json, os, shutil, subprocess, tempfile, zipfile
from acceptance import ROOT, EVIDENCE, DIST, VERSION, Server, digest

def run(eula):
    cache=ROOT/"cache"/VERSION
    jar=DIST/f"unity-loader-{VERSION}.jar"
    if "eula=true" not in eula.read_text(): raise ValueError("Local EULA required")
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="bukkit-workers-") as temp:
        game=Path(temp);classes=game/"classes";classes.mkdir();plugins=game/"plugins";plugins.mkdir()
        cp=[*(ROOT/"standalone/vendor/bukkit").glob("*.jar"),*(cache/"libraries").rglob("*.jar")]
        subprocess.run(["javac","-proc:none","-g:none","--release","25","-cp",os.pathsep.join(map(str,cp)),"-d",classes,ROOT/"standalone/tests/bukkit/HostAuditProbe.java"],check=True)
        with zipfile.ZipFile(plugins/"host-audit.jar","w") as z:
            for f in classes.rglob("*.class"):z.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix()),f.read_bytes())
            z.writestr(zipfile.ZipInfo("plugin.yml"),"name: HostAuditProbe\nversion: 1\napi-version: '26.2'\nmain: qa.audit.HostAuditProbe\n")
        native=game/"unity-mods";native.mkdir();mods=game/"mods";mods.mkdir()
        sample=next((cache/"performance-samples").glob("stackedblocks-*.jar"))
        locked=next(m for m in json.loads((ROOT/"standalone/performance-pack.json").read_bytes())["files"] if m["filename"]==sample.name)
        assert digest(sample,"sha512")==locked["sha512"]
        shutil.copy2(sample,mods)
        wire=game/"wire";wire.mkdir()
        subprocess.run(["javac","-proc:none","-g:none","--release","25","-encoding","UTF-8","-cp",os.pathsep.join(map(str,[jar,cache/"versions/26.2/server-26.2.jar",*cp])),"-d",wire,ROOT/"standalone/tests/bukkit/FailedDiagnosticSend.java"],check=True)
        with zipfile.ZipFile(native/"failed-send.jar","w") as z:
            for f in wire.rglob("*.class"):z.writestr(zipfile.ZipInfo(f.relative_to(wire).as_posix()),f.read_bytes())
            z.writestr(zipfile.ZipInfo('META-INF/unity.properties'),"api=1\nenvironment=server\nid=review-send\nentrypoint=qa.auditwire.FailedDiagnosticSend\n")
        shutil.copy2(eula,game/"eula.txt")
        (game/"server.properties").write_text("server-port=0\nserver-ip=127.0.0.1\nonline-mode=false\nlevel-seed=24\n")
        s=Server(game,"host-worker-audit",jar,cache/"mojang-server-26.2.jar",cache)
        try: s.expect("Standalone ready",120)
        finally: s.close()
    src=EVIDENCE/"host-worker-audit.log";log=src.read_text("utf8",errors="replace")
    totals={n:log.count("Unity Bukkit game event qa.audit.HostAuditProbe$"+n+"\n") for n in ("Signal","Other")}
    assert totals=={"Signal":1,"Other":1} and "QA24 WORKER DISPATCH callbacks=256" in log and "QA24 FAILED SEND CLEANED EXACTLY ONCE" in log,totals
    (EVIDENCE/"host-worker-audit.json").write_text(json.dumps({"loader":digest(jar),"callbacks":256,"failedDiagnosticSendCleanedOnce":True,"firstEmissions":totals,"log":digest(src)},indent=2))
    print("PASS 256 async deliveries, one QA emission per event class")
if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--eula",required=True,type=Path);run(p.parse_args().eula)
