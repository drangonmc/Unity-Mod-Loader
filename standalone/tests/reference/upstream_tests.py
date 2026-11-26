"""Run unchanged upstream test sources with their official frameworks, then probe discovery on Unity."""
import argparse, hashlib, json, os, shutil, subprocess, sys, tempfile, zipfile, re
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent))
from acceptance import ROOT, VERSION, digest, clean_env
FABRIC_COMMIT="e14617e8d44c8eacf6c24e63dc6a694c283bf9f7"
NEO_COMMIT="b6c967adb1ed258f36639204cdea70800d198c0f"
def prepare(kind,inputs,tm,game):
    cache=ROOT/"cache"/VERSION
    sdk=cache/"versions/26.2/server-26.2.jar"
    cp=[sdk,*sorted((cache/"libraries").rglob("*.jar"))]
    classes=tm/"classes";classes.mkdir()
    files=[];resources={};sourceHashes={}
    testpaths=(["fabric-gametest-api-v1/src/testmod/java/net/fabricmc/fabric/test/gametest/ExampleFabricTestSuite.java"] if kind=="fabric" else
               ["tests/src/main/java/net/neoforged/neoforge/debug/CustomHelperTests.java",
                "tests/src/main/java/net/neoforged/neoforge/debug/crafting/CraftingRemainderTests.java"])
    commit=FABRIC_COMMIT if kind=="fabric" else NEO_COMMIT
    with zipfile.ZipFile(inputs/(kind+"-upstream.zip")) as z:
        root=z.namelist()[0].split("/")[0]
        if not root.endswith(commit): raise ValueError("Wrong upstream commit "+root)
        for path in testpaths:
            source=z.read(root+"/"+path); file=tm/Path(path).name;file.write_bytes(source);files.append(file)
            sourceHashes[path]=hashlib.sha256(source).hexdigest()
        if kind=="fabric":
            for n in z.namelist():
                prefix=root+"/fabric-gametest-api-v1/src/testmod/resources/"
                if n.startswith(prefix+"data/") and not n.endswith("/"):
                    resources[n.removeprefix(prefix)]=z.read(n)
    mods=game/"mods";mods.mkdir()
    if kind=="fabric":
        cp.append(inputs/"fabric-gametest.jar")
        shutil.copy2(inputs/"fabric-gametest.jar",mods)
        shutil.copy2(next((ROOT/"standalone/vendor").glob("fabric-api-*.jar")),mods)
        resources["fabric.mod.json"]=json.dumps(dict(schemaVersion=1,id="fabric-gametest-api-v1-testmod",version="1",
            entrypoints={"fabric-gametest":["net.fabricmc.fabric.test.gametest.ExampleFabricTestSuite"]})).encode()
    else:
        refs=ROOT/".work/v14/refs"
        cp.extend([inputs/"neo-framework.jar",*[refs/n for n in ["neo.jar","neo-loader.jar","neo-bus.jar","dist.jar"]]])
        files.append(HERE/"ReferenceNeoTests.java")
        resources["META-INF/neoforge.mods.toml"]=b'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="LGPL-2.1-only"\n[[mods]]\nmodId="reference_neo_tests"\nversion="1"\ndisplayName="Upstream GameTest reference"\n'
        shutil.copy2(inputs/"neo-framework.jar",mods)
    subprocess.run(["javac","-proc:none","--release","25","-g:none","-cp",os.pathsep.join(map(str,cp)),"-d",classes,*files],check=True)
    with zipfile.ZipFile(mods/"upstream-tests.jar","w") as z:
        for f in sorted(classes.rglob("*.class")): z.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix()),f.read_bytes())
        for n,b in sorted(resources.items()):z.writestr(zipfile.ZipInfo(n),b)
    return sourceHashes

def run(kind,o):
    from inputs import check
    check(o.inputs, ["fabric-server.jar","fabric-upstream.zip","fabric-gametest.jar"] if kind=="fabric" else
          ["neo-framework.jar","neo-upstream.zip","mdk-pinned.zip"])
    cache=ROOT/"cache"/VERSION;inputs=o.inputs
    out=o.out/kind;out.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="upstream-"+kind+"-") as tmp:
        tm=Path(tmp);official=tm/"official";official.mkdir()
        source=prepare(kind,inputs,tm,official)
        unity=tm/"unity";unity.mkdir();shutil.copytree(official/"mods",unity/"mods")
        for game in [official,unity]:
            shutil.copy2(o.eula,game/"eula.txt")
            (game/"server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\n")
        std=["java","-Xmx2g","-XX:ActiveProcessorCount=4","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8"]
        report={"upstreamCommit":FABRIC_COMMIT if kind=="fabric" else NEO_COMMIT,"sourceSHA256":source,
                "inputs":{f.name:digest(f) for f in (official/"mods").glob("*.jar")},"unitySHA256":digest(o.unity)}
        opts=[]
        if kind=="fabric":
            shutil.copy2(cache/"mojang-server-26.2.jar",official/"server.jar")
            opts=["-Dfabric-api.gametest","-Dfabric-api.gametest.report-file="+str(out/"official.xml")]
            cmd=[*std,*opts,"-jar",str(inputs/"fabric-server.jar"),"nogui"]
        else:
            # NeoForge tests are registered in the official ModDev environment.
            cmd=None
            if o.only!="unity":
                from neo_dev import main as run_mdk
                run_mdk(argparse.Namespace(inputs=inputs,out=out))
                compiled=inputs/"mdk-gametest/build/classes/java/main"
                with zipfile.ZipFile(unity/"mods/upstream-tests.jar","w") as jar:
                    for f in sorted(compiled.rglob("*.class")):
                        jar.writestr(zipfile.ZipInfo(f.relative_to(compiled).as_posix()),f.read_bytes())
                    n="META-INF/neoforge.mods.toml"
                    jar.writestr(zipfile.ZipInfo(n),(inputs/"mdk-gametest/src/main/resources"/n).read_bytes())
                report["inputs"]["upstream-tests.jar"]=digest(unity/"mods/upstream-tests.jar")
                proof=json.loads((out/"reference.json").read_bytes())
                report["official"]={"exit":0,"cases":proof["tests"],"xmlSHA256":proof["xml"],"logSHA256":proof["log"],
                                    "format":"official ModDev exploded mod; same class files packed for Unity"}
                (out/"result.json").write_text(json.dumps(report,indent=2),"utf8")
        for tag,game,args in [("official",official,cmd),("unity",unity,[*std,*opts,"-jar",str(o.unity),"--server-jar",str(cache/"mojang-server-26.2.jar"),"--runtime-dir",str(cache),"nogui"])]:
            if (o.only and tag!=o.only) or (tag=="official" and cmd is None):continue
            log=out/(tag+".log")
            xml=out/(tag+".xml")
            if xml.exists():xml.unlink()
            timed_out=False
            if tag=="unity" and kind=="fabric":args[args.index(opts[-1])]="-Dfabric-api.gametest.report-file="+str(out/"unity.xml")
            with log.open("wb") as f:
                proc=subprocess.Popen(args,cwd=game,env=clean_env(),stdin=subprocess.PIPE,stdout=f,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
                try:
                    import time
                    deadline=time.monotonic()+300
                    while proc.poll() is None:
                        if tag=="unity" and "Standalone ready" in log.read_text("utf8",errors="replace"):
                            proc.communicate(b"stop\n",timeout=120);break
                        if time.monotonic()>deadline:
                            timed_out=True;break
                        time.sleep(.4)
                finally:
                    if proc.poll() is None:proc.kill();proc.wait()
            text=log.read_text("utf8",errors="replace")
            err=game/"logs/unity-mod-error.txt"
            if err.exists():shutil.copy2(err,out/"unity-preflight.txt")
            import xml.etree.ElementTree as ET
            cases=[] if not xml.is_file() else [{"name":n.get("name"),"failed":any(k.tag in ("error","failure","skipped") for k in n)} for n in ET.parse(xml).iter("testcase")]
            report[tag]={"exit":proc.returncode,"timedOut":timed_out,"cases":cases,"logSHA256":digest(log),"xmlSHA256":digest(out/(tag+".xml")) if (out/(tag+".xml")).exists() else None,
                         "summary":[l[:420] for l in text.splitlines() if re.search(r"tests? (passed|failed)|tests? were run|test[s]? (complete|successful)|Unsupported platform|reserved mod class",l,re.I)][:12]}
            (out/"result.json").write_text(json.dumps(report,indent=2),"utf8")
            print(kind,tag,report[tag],flush=True)
            if tag=="official":
                names={c["name"] for c in cases if not c["failed"]}
                required={"fabric-gametest-api-v1-testmod:example_fabric_test_suite_no_structure",
                          "fabric-gametest-api-v1-testmod:example_fabric_test_suite_diamond"}
                if timed_out or proc.returncode or not required<=names or any(c["failed"] for c in cases):
                    raise ValueError("Official GameTest baseline incomplete")
            if tag=="unity" and not cases and not err.is_file():
                raise ValueError("No Unity test results or preflight diagnostic")

if __name__=="__main__":
    ap=argparse.ArgumentParser();ap.add_argument("kind",choices=["neo","fabric"])
    for k in ["inputs","unity","eula","out"]:ap.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    ap.add_argument("--only",choices=["official","unity"])
    o=ap.parse_args()
    assert "eula=true" in o.eula.read_text()
    run(o.kind,o)
