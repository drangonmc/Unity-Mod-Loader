"""Rebuild the data-driven biome binding against the publisher's unmodified 26.2 interfaces."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,tempfile,zipfile
root=Path(__file__).resolve().parents[3]
core,cache,out=map(lambda p:Path(p).resolve(),sys.argv[1:])
refs=[]
for row in json.loads((root/"standalone/beta-gameplay.json").read_bytes())["fabricModules"]:
    if row["id"]!="fabric-biome-api-v1":continue
    path=cache/"samples"/row["filename"]
    if hashlib.sha512(path.read_bytes()).hexdigest()!=row["sha512"]:raise ValueError(path)
    refs.append(path)
with tempfile.TemporaryDirectory(dir=root/".work") as tmp:
    classes=Path(tmp)/"classes";classes.mkdir()
    cp=[*refs,core,cache/"versions/26.2/server-26.2.jar",*sorted((cache/"libraries").rglob("*.jar"))]
    subprocess.run(["javac","--release","25","-g:none","-encoding","UTF-8","-proc:none","-cp",os.pathsep.join(map(str,cp)),"-d",classes,*sorted(Path(__file__).with_name("src").rglob("*.java"))],check=True)
    with zipfile.ZipFile(out,"w") as z:
        for file in sorted(classes.rglob("*.class")):z.writestr(zipfile.ZipInfo(file.relative_to(classes).as_posix()),file.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
