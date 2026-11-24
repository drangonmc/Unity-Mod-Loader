"""Compile in a temporary directory against the original published APIs."""
from pathlib import Path
import hashlib, json, os, subprocess, sys, tempfile, zipfile
ROOT=Path(__file__).resolve().parents[3]
refs,raw,out=map(lambda p:Path(p).resolve(),sys.argv[1:])
out.mkdir(parents=True,exist_ok=True)
lock=json.loads((ROOT/"standalone/tests/platform/references.json").read_bytes())
for k in ("neo","neo-loader","neo-bus"):
    assert hashlib.sha256((refs/(k+".jar")).read_bytes()).hexdigest()==lock[k]["sha256"]
spec=json.loads((ROOT/"standalone/beta-gameplay.json").read_bytes())
br=next(r for r in spec["originals"] if r["id"]=="VNgfrtBA")
parent=raw/br["filename"]
assert hashlib.sha512(parent.read_bytes()).hexdigest()==br["sha512"]
with zipfile.ZipFile(parent) as z:
    name="multiloaderdataextensions-neoforge-26.2.0.jar"
    data=z.read("META-INF/jarjar/"+name);assert hashlib.sha256(data).hexdigest()=="ffbadbdc7b3c55462edb935f2493bcabf06f690a5c1f09b3b5a216171349350d"
    (out/name).write_bytes(data)
runtime=ROOT/"cache/0.2.3-Beta"
cp=[out/name,*(refs/(k+".jar") for k in ("neo","neo-loader","neo-bus")),runtime/"versions/26.2/server-26.2.jar",*sorted((runtime/"libraries").rglob("*.jar")),*sorted(refs.glob("nightconfig*.jar"))]
with tempfile.TemporaryDirectory(dir=out) as tmp:
    subprocess.run(["javac","-proc:none","--release","25","-g:none","-classpath",os.pathsep.join(map(str,cp)),"-d",tmp,str(Path(__file__).with_name("NestedDataProbe.java"))],check=True)
    files={p.relative_to(tmp).as_posix():p.read_bytes() for p in Path(tmp).rglob("*.class")}
    files["META-INF/neoforge.mods.toml"]='modLoader="javafml"\nloaderVersion="*"\n[[mods]]\nmodId="qa_registry_data"\nversion="1.0"\n[[dependencies.qa_registry_data]]\nmodId="multiloaderdataextensions"\ntype="REQUIRED"\n'
    files["data/qa_registry_data/data_maps/block/scores.json"]='{"values":{"minecraft:stone":41}}'
    with zipfile.ZipFile(out/"qa_registry_data.jar","w") as z:
        for name,data in sorted(files.items()):z.writestr(zipfile.ZipInfo(name),data,compress_type=zipfile.ZIP_DEFLATED)
