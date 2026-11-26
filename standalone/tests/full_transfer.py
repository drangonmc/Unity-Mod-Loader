"""Focused official Fabric transfer module check using the original SDK-compiled Forge and Fabric test mods."""
import argparse,contextlib,json,shutil,sys,tempfile,zipfile,os,subprocess
from pathlib import Path
from acceptance import ROOT,VERSION,DIST,EVIDENCE,Server,digest
from content import observed
from server_configs import biome_resources

def run(o):
    cache=ROOT/"cache"/VERSION; jar=next(DIST.glob("unity-loader*.jar")); parent=Path(o.fixtures)
    assert "eula=true" in o.eula.read_text()
    with tempfile.TemporaryDirectory(dir=ROOT/".work",prefix="full-transfer-") as tmp:
        game=Path(tmp);mods=game/"mods";mods.mkdir()
        api=next((ROOT/"standalone/vendor").glob("fabric-api-*.jar"));shutil.copy2(api,mods)
        inputs={api.name:digest(api)}
        for n in ("qa_server_config.jar","qa_forge_config.jar","qa_fabric_ecology.jar"):
            shutil.copy2(parent/n,mods);inputs[n]=digest(parent/n)
        sdk=game/"sdk";sdk.mkdir()
        with zipfile.ZipFile(api) as z:
            for n in z.namelist():
                if n.startswith("META-INF/jars/") and n.endswith(".jar"):(sdk/Path(n).name).write_bytes(z.read(n))
        original=json.loads((ROOT/"standalone/tests/interop/references.json").read_bytes())
        refs=[o.refs/(name+".jar") for name in ("neo","fabric-loader","neo-bus","neo-loader")]
        assert all(digest(p)==original[p.stem]["sha256"] for p in refs)
        cp=[*refs,*list(sdk.glob("*.jar")),*list(parent.glob("*.jar")),cache/"versions/26.2/server-26.2.jar",*list((cache/"libraries").rglob("*.jar"))]
        classes=game/"classes";classes.mkdir()
        subprocess.run(["javac","-proc:none","--release","25","-d",classes,"-cp",os.pathsep.join(map(str,cp)),ROOT/"standalone/tests/bukkit/InteropStorageProbe.java"],check=True)
        with zipfile.ZipFile(mods/"qa24-stores.jar","w") as z:
            for cls in classes.rglob("*.class"):z.write(cls,cls.relative_to(classes).as_posix())
            z.writestr("fabric.mod.json",json.dumps(dict(schemaVersion=1,id="qa24_stores",version="1",entrypoints={"main":["qa.fabric.InteropStorageProbe"]})))
        pack=game/"world/datapacks/qa";pack.mkdir(parents=True)
        (pack/"pack.mcmeta").write_text('{"pack":{"description":"QA","min_format":107,"max_format":107}}')
        for n,v in biome_resources().items():
            file=pack/"data/qa_fabric_ecology"/(n+".json");file.parent.mkdir(parents=True,exist_ok=True);file.write_text(json.dumps(v))
        shutil.copyfile(o.eula,game/"eula.txt");(game/"server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nspawn-protection=0\nlevel-seed=262024\n")
        with contextlib.closing(Server(game,"full-transfer",jar,cache/"mojang-server-26.2.jar",cache)) as s:
            s.expect("Standalone ready",100)
            s.send("forceload add 7 8");observed(s,"loaded 7 201 8","TRANSFER_CHUNK",30)
            s.send("setblock 7 201 8 minecraft:barrel","Changed")
            s.send("setblock 8 201 8 minecraft:bell","Changed")
            ecology=s.send("qa_ecology",r"QA_ECOLOGY spawnWeight=17 feature=true .*markers=[1-9]",90)
            transfer=s.send("qa_transfer","QA_TRANSFER simulated=7 aborted=7 committed=5 invalidated=true",20)
            write=s.send("qa24_attach_write","QA24 STORES WRITE");s.send("save-all flush","Saved the game")
        with contextlib.closing(Server(game,"full-transfer-restart",jar,cache/"mojang-server-26.2.jar",cache)) as second:
            second.expect("Standalone ready",100);observed(second,"loaded 7 201 8","TRANSFER_CHUNK",30)
            read=second.send("qa24_attach_read","QA24 STORES READ")
        log=EVIDENCE/"full-transfer.log";text=log.read_text("utf8",errors="replace")
        assert "QA_RESOURCES energy=23 fluidMb=750 droplets=60750" in text
        (EVIDENCE/"full-transfer.json").write_text(json.dumps({"runtime":digest(jar),"inputs":inputs,"item":transfer,"biome":ecology,"storage":[write,read],"restartLog":digest(EVIDENCE/"full-transfer-restart.log"),"fluid":{"millibuckets":750,"droplets":60750},"log":digest(log)},indent=2))
        print("PASS original Fabric Transfer -> Unity / Forge items and fluids")
if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--refs",type=Path,required=True);p.add_argument("--fixtures",type=Path,required=True);p.add_argument("--eula",type=Path,required=True);run(p.parse_args())
