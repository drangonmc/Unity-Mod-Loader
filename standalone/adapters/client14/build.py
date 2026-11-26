"""Compile the client API slice from source against the installed vanilla 26.2 SDK."""
import os, subprocess, sys, tempfile, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
def build(core,home,dest):
    sys.path.insert(0,str(ROOT/"tools"))
    from unity import CACHE
    src=Path(__file__).with_name("src")
    files=sorted(src.rglob("*.java"))
    owned={p.relative_to(src).with_suffix("").as_posix() for p in files}
    with tempfile.TemporaryDirectory(dir=ROOT/".work") as temp:
        temp=Path(temp); api=temp/"unity-api.jar"; classes=temp/"classes";classes.mkdir()
        with zipfile.ZipFile(core) as original,zipfile.ZipFile(api,"w") as view:
            for n in original.namelist():
                if n.endswith(".class") and n[:-6].split("$",1)[0] not in owned:view.writestr(n,original.read(n))
        cp=[api,home/"versions/26.2/26.2.jar",*sorted((CACHE/"libraries").rglob("*.jar"))]
        # All signatures for the client implementation come from the real 26.2 SDK.
        subprocess.run(["javac","-proc:none","--release","25","-encoding","UTF-8","-g:none","-cp",os.pathsep.join(map(str,cp)),"-d",str(classes),*files],check=True)
        with zipfile.ZipFile(dest,"w") as out:
            for c in sorted(classes.rglob("*.class")):out.writestr(zipfile.ZipInfo(c.relative_to(classes).as_posix()),c.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
if __name__=="__main__":build(*(Path(x).resolve() for x in sys.argv[1:]))
