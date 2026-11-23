"""Build Unity's optional service providers against the exact published Puzzles Lib binary."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, tempfile, zipfile
root=Path(__file__).resolve().parents[3]
v="0.2.2-Beta"
def build(refs, home, samples, loader, dest):
    lock=json.loads((root/"standalone/beta-gameplay.json").read_bytes())
    spec=next(row for row in lock["originals"] if row["id"]=="XkWV0WSp")
    puzzles=samples/spec["filename"]
    if hashlib.sha512(puzzles.read_bytes()).hexdigest()!=spec["sha512"]:raise ValueError("Puzzles Lib binary")
    official=json.loads((root/"standalone/tests/platform/references.json").read_bytes())
    apis=[refs/(k+".jar") for k in ("neo","neo-bus","neo-loader")]
    for p in apis:
        if hashlib.sha256(p.read_bytes()).hexdigest()!=official[p.stem]["sha256"]: raise ValueError(p)
    dependencies=[puzzles,*apis,home/"versions/26.2/26.2.jar",*sorted((root/"cache"/v/"libraries").rglob("*.jar")),*sorted((home/"libraries").rglob("*.jar"))]
    with tempfile.TemporaryDirectory(dir=root/".work") as tmp:
        src=Path(__file__).with_name("src")
        sources=sorted(src.rglob("*.java"))
        # Exclude the distributed adapter from the compiler input. Only the rest of Unity is its SDK.
        owned={p.relative_to(src).with_suffix("").as_posix() for p in sources}
        compile_api=Path(tmp)/"unity-api.jar"
        with zipfile.ZipFile(loader) as original, zipfile.ZipFile(compile_api,"w") as view:
            for name in original.namelist():
                if name.endswith(".class") and name[:-6].split("$",1)[0] not in owned:
                    view.writestr(name,original.read(name))
        cp=[compile_api,*dependencies]
        subprocess.run(["javac","-proc:none","-g:none","--release","25","-encoding","UTF-8","-cp",os.pathsep.join(map(str,cp)),"-d",tmp,*sources],check=True)
        with zipfile.ZipFile(dest,"w") as z:
            for p in sorted(Path(tmp).rglob("*.class")):z.writestr(zipfile.ZipInfo(p.relative_to(tmp).as_posix()),p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for name in ("refs","home","samples","loader","dest"):p.add_argument(name,type=Path)
    build(**vars(p.parse_args()))
