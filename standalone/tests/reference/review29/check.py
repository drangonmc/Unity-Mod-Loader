import argparse,json,hashlib,os,subprocess
from pathlib import Path
def hashfile(f):return hashlib.sha256(f.read_bytes()).hexdigest()
def main(o):
    o.out.mkdir(parents=True,exist_ok=True)
    cp=[o.loader,o.cache/"versions/26.2/server-26.2.jar",*sorted((o.cache/"libraries").rglob("*.jar"))]
    log=o.out/"contracts.log"
    with log.open("wb") as stream:
        subprocess.run(["java","-Xverify:all","-cp",os.pathsep.join(map(str,cp)),Path(__file__).with_name("Contracts.java")],stdout=stream,stderr=subprocess.STDOUT,check=True)
    assert "PASS XP preservation" in log.read_text("utf8",errors="replace")
    (o.out/"result.json").write_text(json.dumps({"passed":True,"loader":hashfile(o.loader),"log":hashfile(log)},indent=2),"utf8")
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ("loader","cache","out"):p.add_argument("--"+k,type=lambda s:Path(s).resolve(),required=True)
    main(p.parse_args())
