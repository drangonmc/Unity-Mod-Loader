"""Compile the optional adapter against the exact WorldEdit release without modifying that release."""
import hashlib,json,os,subprocess,sys,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
def build(we,game,libs,out):
    spec=next(r for r in json.loads((ROOT/"standalone/tests/reference/protection-originals.json").read_bytes()) if r["slug"]=="worldedit")
    assert hashlib.sha512(we.read_bytes()).hexdigest()==spec["sha512"]
    cp=[we,game,*(ROOT/"standalone/vendor/bukkit").glob("*.jar"),*sorted(libs.rglob("*.jar"))]
    with tempfile.TemporaryDirectory(dir=ROOT/".work") as tmp:
        d=Path(tmp)
        subprocess.run(["javac","-proc:none","-g:none","--release","25","-cp",os.pathsep.join(map(str,cp)),"-d",d,ROOT/"src/plugin/java/mc/unity/plugins/NativeWorldView.java",*Path(__file__).with_name("src").rglob("*.java")],check=True)
        out.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(out,"w") as z:
            for f in sorted((d/"mc/unity/adapter/worldedit").rglob("*.class")):z.writestr(zipfile.ZipInfo(f.relative_to(d).as_posix()),f.read_bytes())
if __name__=="__main__":build(*(Path(x).resolve() for x in sys.argv[1:]))
