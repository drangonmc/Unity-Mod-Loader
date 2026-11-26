"""Check the shipped built-in transformation chain on an arbitrary MC server/client archive, without booting it."""
import argparse,collections,json,os,subprocess,sys,tempfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from acceptance import ROOT,VERSION,digest

def main(o):
    cache=ROOT/"cache"/VERSION
    libs=sorted((o.libraries_dir or cache/"libraries").rglob("*.jar"))
    if o.side=="client" and not o.libraries_dir:
        libs+=sorted(p for p in (cache/"client-home/libraries").rglob("*.jar") if "/mc/unity/" not in p.as_posix())
    with tempfile.TemporaryDirectory(dir=ROOT/".work") as tmp:
        classes=Path(tmp)/"classes";classes.mkdir()
        cp=os.pathsep.join(map(str,[classes,o.unity,o.game,*o.library,*libs]))
        # A source argument file avoids the Windows command-line length limit for the client SDK.
        def invoke(exe,args):
            af=Path(tmp)/"args.txt"
            af.write_text("\n".join('"'+str(s).replace(chr(92),"/")+'"' for s in args),encoding="mbcs" if os.name=="nt" and exe=="java" else "utf8")
            subprocess.run([exe,"@"+str(af)],check=True)
        invoke("javac",["--release","25","-proc:none","-cp",cp,"-d",classes,Path(__file__).with_name("AnchorAudit.java")])
        mainclass="mc.unity.bootstrap.AnchorAudit"
        invoke("java",["-cp",cp,mainclass,"prepare",o.unity,classes])
        result=Path(tmp)/"steps.json"
        invoke("java",["-Xmx2g","-cp",cp,mainclass,"scan",o.side,o.game,result])
        rows=json.loads(result.read_bytes());counts=dict(collections.Counter(r["status"] for r in rows))
        o.out.parent.mkdir(parents=True,exist_ok=True)
        o.out.write_text(json.dumps({"side":o.side,"game":digest(o.game),"unity":digest(o.unity),"counts":counts,"steps":rows},indent=2),"utf8")
        print(o.side,counts)
        if counts.get("failed"):raise SystemExit(2)
if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("side",choices=["client","server"])
    for k in ("unity","game","out"):p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    p.add_argument("--libraries-dir",type=lambda s:Path(s).resolve(),help="Use target-version libraries in place of the 26.2 cache")
    p.add_argument("--library",action="append",type=lambda s:Path(s).resolve(),default=[])
    main(p.parse_args())
