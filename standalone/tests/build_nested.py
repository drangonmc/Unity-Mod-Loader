"""Build reproducible walkable examples against upstream Fabric/NeoForge, without Unity on javac's class path."""
from pathlib import Path
import json,os,subprocess,tempfile,zipfile,io,sys,hashlib
from acceptance import ROOT,VERSION
SRC=ROOT/"standalone/tests/nested"

def archive(files):
    out=io.BytesIO()
    with zipfile.ZipFile(out,"w") as z:
        for n,data in sorted(files.items()):z.writestr(zipfile.ZipInfo(n),data,compress_type=zipfile.ZIP_DEFLATED)
    return out.getvalue()

def jarjar(entries):
    return json.dumps({"jars":[{"identifier":{"group":"qa","artifact":name},"version":{"range":constraint,"artifactVersion":version},"path":"META-INF/jarjar/"+path,"isObfuscated":False} for name,constraint,version,path in entries]})

def build(ref,out):
    references = {}
    for group in ("platform", "interop"):
        lock = ROOT / "standalone/tests" / group / "references.json"
        references.update(json.loads(lock.read_bytes()))
    for name, spec in references.items():
        with (ref / (name + ".jar")).open("rb") as source:
            if hashlib.file_digest(source, "sha256").hexdigest() != spec["sha256"]:
                raise ValueError("Upstream compile reference changed: " + name)
    cache=ROOT/"cache"/VERSION;out.mkdir(parents=True,exist_ok=True)
    cp=[cache/"versions/26.2/server-26.2.jar",*sorted((cache/"libraries").rglob("*.jar"))]
    with tempfile.TemporaryDirectory(dir=out) as work:
        base=Path(work);index=0
        def classes(src,reference=()):
            nonlocal index
            index+=1;dest=base/str(index);dest.mkdir();source=dest/(src[0]+".java");source.write_text(src[1],encoding="utf8")
            subprocess.run(["javac","-proc:none","-g:none","--release","25","-J-Duser.language=en","-encoding","UTF-8","-classpath",os.pathsep.join(map(str,[*reference,*cp])),"-d",str(dest),str(source)],check=True)
            return dest,{f.relative_to(dest).as_posix():f.read_bytes() for f in dest.rglob("*.class")}
        def component(name,access,number):
            pkg,cls,method,field=("qa.inner","Secret","answer","value") if name=="Secret" else ("qa.lib","Version","secret","marker")
            return classes((cls,f"package {pkg}; public final class {cls}{{ {access} static int {field}=Integer.valueOf({number}); {access} static int {method}(){{return {field};}} }}"))
        fref,_=component("Secret","public",41);nref,_=component("Version","public",13)
        _,inner=component("Secret","private final",41)
        inner["fabric.mod.json"]=json.dumps({"schemaVersion":1,"id":"qa_inner","version":"1.0.0","name":"Nested library"})
        innerjar=archive(inner)
        _,outer=classes(("FabricParent",(SRC/"FabricParent.java").read_text("utf8")),[fref,*[ref/(n+".jar") for n in ("fabric-loader","fabric-command-api-v2","fabric-api-base")]])
        outer["fabric.mod.json"]=json.dumps({"schemaVersion":1,"id":"qa_fabric_content","version":"1.0.0","entrypoints":{"main":["qa.fabriccontent.FabricParent"]},"depends":{"minecraft":"26.2","qa_inner":"~1.0","fabric-lifecycle-events-v1":">=4.1","fabric-entity-events-v1":">=5"},"jars":[{"file":"META-INF/jars/inner.jar"}],"accessWidener":"qa.aw"})
        outer["qa.aw"]="accessWidener v2 named\naccessible method qa/inner/Secret answer ()I\naccessible field qa/inner/Secret value I\nmutable field qa/inner/Secret value I\n"
        outer["META-INF/jars/inner.jar"]=innerjar
        _,nlow=component("Version","private final",10);_,nhigh=component("Version","private final",13)
        lo,hi=archive(nlow),archive(nhigh)
        _,mid=classes(("Audit",(SRC/"Audit.java").read_text("utf8")),[ref/(n+".jar") for n in ("forge","bus","javafmllanguage","fmlcore","fmlloader")])
        mid.update({"META-INF/mods.toml":'modLoader="javafml"\nloaderVersion="[65,)"\n[[mods]]\nmodId="qa_stage_audit"\nversion="1.0"\n',"META-INF/jarjar/metadata.json":jarjar([("helper","[1.2,2)","1.3.0","helper.jar")]),"META-INF/jarjar/helper.jar":hi})
        middle=archive(mid)
        _,neo=classes(("NeoParent",(SRC/"NeoParent.java").read_text("utf8")),[nref,*[ref/(n+".jar") for n in ("neo","neo-bus","neo-loader")]])
        neo["META-INF/neoforge.mods.toml"]='modLoader="javafml"\nloaderVersion="[11,)"\nlicense="MIT"\n[[mods]]\nmodId="qa_neo_content"\nversion="1.0.0"\n[[accessTransformers]]\nfile="META-INF/qa.cfg"\n'
        neo["META-INF/qa.cfg"]="public qa.lib.Version secret()I\npublic-f qa.lib.Version marker\n"
        neo["META-INF/jarjar/metadata.json"]=jarjar([("helper","[1,2)","1.0.0","helper.jar"),("middle","[1,2)","1.0","middle.jar")])
        neo["META-INF/jarjar/helper.jar"]=lo;neo["META-INF/jarjar/middle.jar"]=middle
        for id in ("pass","fail","commit","counter"):
            neo[f"assets/qa_neo_content/items/{id}.json"]=json.dumps({"model":{"type":"minecraft:model","model":"minecraft:item/diamond"}})
        neo["assets/qa_neo_content/blockstates/counter.json"]=json.dumps({"variants":{"":{"model":"minecraft:block/emerald_block"}}})
        for name,files in (("qa_fabric_content",outer),("qa_neo_content",neo)):(out/(name+".jar")).write_bytes(archive(files))
        info={k:hashlib.sha256(v).hexdigest() for k,v in (("fabric-inner",innerjar),("selected-helper",hi),("rejected-helper",lo),("middle",middle))}
        (out/"nested-lock.json").write_text(json.dumps(info,indent=2)+"\n")
if __name__=="__main__":build(Path(sys.argv[1]).resolve(),Path(sys.argv[2]).resolve())
