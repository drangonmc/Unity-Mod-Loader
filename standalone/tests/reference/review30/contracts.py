import argparse
import json
import os
import sys
import subprocess
import tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/"review29"))
import check

def main(o):
    check.main(o)
    log=o.out/"contracts.log"
    with tempfile.TemporaryDirectory(dir=o.out) as tmp,log.open("ab") as stream:
        subprocess.run(["javac","--release","25","-proc:none","-cp",o.loader,"-d",tmp,HERE/"MenuSessions.java"],check=True)
        subprocess.run(["java","-Xverify:all","-cp",os.pathsep.join([tmp,str(o.loader)]),"mc.unity.bootstrap.MenuSessions",o.loader,o.cache],
                       cwd=tmp,stdout=stream,stderr=subprocess.STDOUT,check=True)
    report=o.out/"result.json"
    data=json.loads(report.read_bytes());data.update(menuSessions=2,log=check.hashfile(log))
    report.write_text(json.dumps(data,indent=2),"utf8")
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for name in ("loader","cache","out"):p.add_argument("--"+name,type=lambda s:Path(s).resolve(),required=True)
    main(p.parse_args())
