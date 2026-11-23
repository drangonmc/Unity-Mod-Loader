"""Compile two test mods against upstream APIs. Only the two resulting mod JARs enter game JVMs."""
from pathlib import Path
import json,os,subprocess,tempfile,zipfile,hashlib,urllib.request
from acceptance import ROOT,VERSION

def build(reference,output):
    reference.mkdir(parents=True,exist_ok=True)
    for name,row in json.loads((ROOT/"standalone/tests/platform/references.json").read_bytes()).items():
        dest=reference/(name+".jar")
        if not dest.exists():
            with tempfile.NamedTemporaryFile(dir=reference,delete=False) as tmp:
                candidate=Path(tmp.name)
            try:
                with urllib.request.urlopen(row["url"],timeout=90) as resp:candidate.write_bytes(resp.read())
                if hashlib.sha256(candidate.read_bytes()).hexdigest()!=row["sha256"]:raise ValueError(name)
                os.replace(candidate,dest)
            finally:candidate.unlink(missing_ok=True)
        if hashlib.sha256(dest.read_bytes()).hexdigest()!=row["sha256"]:raise ValueError("Reference hash mismatch: "+name)
    runtime=ROOT/"cache"/VERSION
    game=[runtime/"versions/26.2/server-26.2.jar",*sorted((runtime/"libraries").rglob("*.jar"))]
    output.mkdir(parents=True,exist_ok=True)
    for main,id,fabric,names in (("FabricToken","unity_fabric_sample",True,("fabric-loader","fabric-api-base","fabric-command-api-v2")),("NeoToken","unity_neo_sample",False,("neo","neo-bus","neo-loader"))):
        libs=[reference/(name+".jar") for name in names]
        if not all(p.is_file() for p in libs):raise FileNotFoundError(libs)
        with tempfile.TemporaryDirectory(dir=output) as t:
            temp=Path(t)
            args=["javac","-J-Duser.language=en","--release","25","-proc:none","-cp",os.pathsep.join(map(str,[*libs,*game])),"-d",str(temp),str(ROOT/"standalone/tests/platform"/(main+".java"))]
            subprocess.run(args,check=True)
            meta=json.dumps({"schemaVersion":1,"id":id,"version":"1.0.0-test.1","name":"Unity Fabric acceptance sample","environment":"*","entrypoints":{"main":["sample."+main]},"depends":{"minecraft":"26.2","fabricloader":">=0.19.0","fabric-command-api-v2":"*"}}) if fabric else 'modLoader="javafml"\nloaderVersion="[11,)"\nlicense="MIT"\n[[mods]]\nmodId="unity_neo_sample"\nversion="1.0.0+neo"\n[[dependencies.unity_neo_sample]]\nmodId="neoforge"\ntype="required"\nversionRange="[26.2,26.3)"\nside="BOTH"\n'
            with zipfile.ZipFile(output/(id+".jar"),"w",zipfile.ZIP_DEFLATED) as z:
                for file in sorted(temp.rglob("*.class")):z.writestr(zipfile.ZipInfo(file.relative_to(temp).as_posix()),file.read_bytes())
                model={"model":{"type":"minecraft:model","model":"minecraft:item/"+("amethyst_shard" if fabric else "emerald")}}
                z.writestr(zipfile.ZipInfo(f"assets/{id}/items/token.json"),json.dumps(model))
                z.writestr(zipfile.ZipInfo("fabric.mod.json" if fabric else "META-INF/neoforge.mods.toml"),meta)
if __name__=="__main__":
    import sys
    build(Path(sys.argv[1]).resolve(),Path(sys.argv[2]).resolve())
