"""Create the public single-JAR server/client distribution after runtime and upgrade verification."""
import argparse,hashlib,json,os,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
VERSION="0.3.4-Beta"
def digest(p):
    with p.open("rb") as f:return hashlib.file_digest(f,"sha256").hexdigest()
def build(engine,evidence,out):
    verified=json.loads((evidence/"result.json").read_bytes())
    if verified["loader"]!=digest(engine) or len(verified["runs"])!=2 or verified["runs"][0]!=verified["runs"][1]:
        raise ValueError("The fresh and upgrade receipts must match this loader")
    with zipfile.ZipFile(engine) as z:
        assert "mc/unity/runtime/WorldMarker.class" in z.namelist()
        assert not any(p.startswith("mc/unity/qa/") for p in z.namelist())
    files={"plugins/":b"","README.md":(ROOT/"README.md").read_bytes(),"LICENSE":(ROOT/"LICENSE").read_bytes(),"NOTICE.md":(ROOT/"NOTICE.md").read_bytes()}
    for n in ("Start-Unity.cmd","Install-Unity-Client.cmd"):files[n]=(ROOT/n).read_bytes()
    files[engine.name]=engine.read_bytes()
    out.parent.mkdir(parents=True,exist_ok=True);temp=out.with_suffix(".zip.tmp")
    with zipfile.ZipFile(temp,"w") as z:
        for name,data in sorted(files.items()):
            entry=zipfile.ZipInfo(name);entry.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(entry,data)
    with zipfile.ZipFile(temp) as z:
        assert z.testzip() is None and [n for n in z.namelist() if n.endswith(".jar")]==[engine.name]
        for n,data in files.items():assert z.read(n)==data
    os.replace(temp,out)
    return digest(out)
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for name in ("engine","evidence","out"):p.add_argument("--"+name,type=Path,required=True)
    opts=p.parse_args();print(build(opts.engine,opts.evidence,opts.out))
