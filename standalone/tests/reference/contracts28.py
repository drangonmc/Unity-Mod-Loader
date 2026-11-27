import argparse, json, os, re, shutil, subprocess, sys, tempfile
from pathlib import Path
import beta25 as qa
from review28.probes import build

def main(o):
    o.project=qa.ROOT
    o.out.mkdir(parents=True,exist_ok=True)
    loader=o.dist/"unity-loader-0.3.0-Beta.jar"
    cp=[loader,o.cache/"versions/26.2/server-26.2.jar",*sorted((o.cache/"libraries").rglob("*.jar"))]
    with (o.out/"contracts.log").open("wb") as log:
        subprocess.run(["java","-cp",os.pathsep.join(map(str,cp)),Path(__file__).with_name("review28")/"Contracts.java",
                        qa.ROOT/"src/plugin/resources/META-INF/unity/worldedit-7.4.6.jar",o.worldedit,o.alternate],stdout=log,stderr=subprocess.STDOUT,check=True)
    with tempfile.TemporaryDirectory(dir=qa.ROOT/".work") as tmp:
        root=Path(tmp);mods=root/"mods";mods.mkdir()
        shutil.copy2(o.eula,root/"eula.txt")
        jars=build(o,root);shutil.copy2(jars["unity28registry"],mods)
        with (o.out/"strict.log").open("wb") as log:
            p=subprocess.run(["java","-Dmc.unity.abi=strict","-jar",loader,"--server-jar",o.cache/"mojang-server-26.2.jar","--runtime-dir",o.cache,"nogui"],
                         cwd=root,stdout=log,stderr=subprocess.STDOUT,timeout=90)
        out=(o.out/"strict.log").read_text("utf8",errors="replace")
        assert p.returncode and "Unsupported platform type: net/neoforged/testoptional/OptionalWidget" in out and "Standalone ready" not in out,out[-550:]
        loop=root/"local";loop.mkdir()
        (loop/"mods").mkdir();shutil.copy2(jars["unity28registry"],loop/"mods")
        shutil.copy2(o.eula,loop/"eula.txt")
        (loop/"server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\ngenerate-structures=false\n")
        qa.acceptance.EVIDENCE=o.out
        cmd=["java","-Xmx1g","-XX:ActiveProcessorCount=4","-Dunity.test.onlyNeo=true","-Dstdout.encoding=UTF-8","-jar",str(loader),"--server-jar",str(o.cache/"mojang-server-26.2.jar"),"--runtime-dir",str(o.cache),"nogui"]
        server=qa.Server(loop,"lightweight",loader,o.cache/"mojang-server-26.2.jar",o.cache,cmd)
        try:server.expect("Standalone ready",120)
        finally:server.close()
        light=(o.out/"lightweight.log").read_text("utf8",errors="replace")
        assert "UNITY28_PLATFORM_IDS_OK" in light and "UNITY28_REGISTRIES_OK" in light
    result={"loader":qa.digest(loader),"rootRegistries":int(re.search(r"UNITY28_ROOT_ALL (\d+)",light)[1]),"strictRejected":True,"worldEdit":[qa.digest(f,"sha512") for f in (o.worldedit,o.alternate)],
            "logs":{n:qa.digest(o.out/n) for n in ("contracts.log","strict.log","lightweight.log")}}
    (o.out/"result.json").write_text(json.dumps(result,indent=2),"utf8")
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ("dist","out","cache","sdk","worldedit","alternate","eula"):
        p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    main(p.parse_args())
