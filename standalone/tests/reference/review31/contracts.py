import argparse,hashlib,json,os,subprocess,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ("loader","cache","out"):p.add_argument("--"+k,type=lambda s:Path(s).resolve(),required=True)
    o=p.parse_args()
    subprocess.run([sys.executable,HERE.parent/"review30/contracts.py","--loader",o.loader,"--cache",o.cache,"--out",o.out],check=True)
    log=o.out/"contracts.log"
    cp=[o.loader,o.cache/"versions/26.2/server-26.2.jar",*sorted((o.cache/"libraries").rglob("*.jar"))]
    with log.open("ab") as stream:subprocess.run(["java","-Xverify:all","-cp",os.pathsep.join(map(str,cp)),HERE/"configs.java"],stdout=stream,stderr=subprocess.STDOUT,check=True)
    result=o.out/"result.json";data=json.loads(result.read_bytes())
    assert "PASS TOML list, enum and exact numeric conversion" in log.read_text("utf8",errors="replace")
    data.update(configTypes=True,log=hashlib.sha256(log.read_bytes()).hexdigest());result.write_text(json.dumps(data,indent=2),"utf8")
