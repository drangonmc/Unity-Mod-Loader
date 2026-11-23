"""Compile four cross-platform acceptance mods without the Unity runtime on javac's class path."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,tempfile,urllib.request,zipfile
from acceptance import ROOT,VERSION
SOURCES=ROOT/"standalone/tests/interop"

def build(reference,out,output_rules=False):
    reference.mkdir(parents=True,exist_ok=True);out.mkdir(parents=True,exist_ok=True)
    for name,ref in json.loads((SOURCES/"references.json").read_bytes()).items():
        dest=reference/(name+".jar")
        if not dest.exists():
            with tempfile.NamedTemporaryFile(dir=reference,delete=False) as temp:q=Path(temp.name)
            try:
                with urllib.request.urlopen(ref["url"],timeout=90) as r:q.write_bytes(r.read())
                if hashlib.sha256(q.read_bytes()).hexdigest()!=ref["sha256"]:raise ValueError(name)
                os.replace(q,dest)
            finally:q.unlink(missing_ok=True)
        if hashlib.sha256(dest.read_bytes()).hexdigest()!=ref["sha256"]:raise ValueError(dest)
    cache=ROOT/"cache"/VERSION
    cp=[cache/"versions/26.2/server-26.2.jar",*sorted((cache/"libraries").rglob("*.jar")),*sorted((ROOT/"standalone/lib").glob("*.jar"))]
    # Different ecosystems are deliberately compiled in separate javac invocations.
    refs={"forge":["forge","bus","javafmllanguage","fmlcore","fmlloader","fabric-loader"],"neo":["neo","neo-bus","neo-loader"],"fabric":["fabric-loader"]}
    def metadata(id,extra=""):
        return 'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="'+id+'"\nversion="1.2.0"\n'+extra
    def config(pkg,classes):
        return json.dumps({"required":True,"minVersion":"0.8","package":pkg,"compatibilityLevel":"JAVA_21","mixins":classes,"injectors":{"defaultRequire":1}})
    cases=[
      ("zz_plainlib","forge",["Plain"],{"META-INF/mods.toml":metadata("zz_plainlib")}),
      ("zz_neo_before","neo",["Before","RangeMixin"],{"META-INF/neoforge.mods.toml":metadata("zz_neo_before",'[[mixins]]\nconfig="qa-before.json"\n[[dependencies.zz_neo_before]]\nmodId="aa_bridge"\ntype="required"\nversionRange="[1,2)"\nordering="BEFORE"\nside="BOTH"\n'),"qa-before.json":config("qa.before.mixin",["RangeMixin"])}),
      ("aa_bridge","fabric",["Bridge","ModMixin","RendererMixin"],{"fabric.mod.json":json.dumps({"schemaVersion":1,"id":"aa_bridge","version":"1.2.0","entrypoints":{"main":[{"adapter":"default","value":"qa.bridge.Bridge"}],"client":["qa.bridge.Bridge$Client"],"server":["qa.bridge.Bridge$Server"]},"depends":{"minecraft":["26.1","26.2"],"zz_plainlib":">=1.2 <2","fabricloader":"^0.19.0"},"mixins":["qa-bridge.json",{"config":"qa-render.json","environment":"client"}]}),"qa-bridge.json":config("qa.bridge.mixin",["ModMixin"]),"qa-render.json":config("qa.bridge.client",["RendererMixin"])}),
      ("aa_events","forge",["ForgeEvents"],{"META-INF/mods.toml":metadata("aa_events",'[[dependencies.aa_events]]\nmodId="aa_bridge"\nmandatory=true\nversionRange="[1.2,2)"\nside="BOTH"\n')})]
    if output_rules:
        cases=[("unity_cancel_fixture","forge",["CancelRules"],{"META-INF/mods.toml":metadata("unity_cancel_fixture")})]
    generated=[]
    for id,kind,names,meta in cases:
        with tempfile.TemporaryDirectory(dir=out) as tmp:
            classes=Path(tmp)
            args=["javac","-J-Duser.language=en","--release","25","-proc:none","-cp",os.pathsep.join(map(str,[*[reference/(n+".jar") for n in refs[kind]],*generated,*cp])),"-d",str(classes),*[str((ROOT/"standalone/tests/gameplay" if output_rules else SOURCES)/(n+".java")) for n in names]]
            subprocess.run(args,check=True)
            jar=out/(id+".jar")
            with zipfile.ZipFile(jar,"w") as z:
                for f in sorted(classes.rglob("*.class")):z.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix()),f.read_bytes())
                for n,data in meta.items():z.writestr(zipfile.ZipInfo(n),data)
            generated.append(jar)
if __name__=="__main__":build(Path(sys.argv[1]).resolve(),Path(sys.argv[2]).resolve(),"--rules" in sys.argv[3:])
