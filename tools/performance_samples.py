"""Materialize the locked original performance pack in a benchmark directory."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
from urllib.request import Request,urlopen
import zipfile
from unity import ROOT,CACHE

def matches(path,row):
    if not path.is_file() or path.stat().st_size!=row["size"]:return False
    with path.open("rb") as stream:return hashlib.file_digest(stream,"sha512").hexdigest()==row["sha512"]

def read_url(url,size):
    with urlopen(Request(url,headers={"User-Agent":"Unity local mod compatibility benchmark/0.2"}),timeout=90) as response:
        return response.read(size+1)

def run(dest,sources,offline,side="server"):
    lock=json.loads((ROOT/"standalone/performance-pack.json").read_bytes())
    dest.mkdir(parents=True,exist_ok=True)
    server_only={m["filename"] for m in json.loads((ROOT/"standalone/forge-samples.json").read_bytes())}
    rows=[m for m in lock["files"] if side=="server" or m["filename"] not in server_only]
    names={x["filename"] for x in rows}
    if len(rows)!=len(names) or any(Path(n).name!=n or not n.endswith(".jar") for n in names):raise ValueError("Invalid pack lock")
    extras={p.name for p in dest.glob("*.jar")}-names
    if extras:raise ValueError(f"Benchmark directory contains unrelated JARs: {sorted(extras)}")
    umbrella=None
    for row in rows:
        name=row["filename"];final=dest/name
        if matches(final,row):continue
        if final.exists():raise ValueError("Existing JAR differs: "+str(final))
        source=next((p/name for p in sources if matches(p/name,row)),None)
        if source:blob=source.read_bytes()
        elif offline:raise FileNotFoundError(name)
        elif "member" in row:
            if umbrella is None:
                author=lock["fabricApi"];raw=read_url(author["url"],author["size"])
                if len(raw)!=author["size"] or hashlib.sha512(raw).hexdigest()!=author["sha512"]:raise ValueError("Fabric API digest differs")
                umbrella=io.BytesIO(raw)
            with zipfile.ZipFile(umbrella) as z:blob=z.read(row["member"])
        else:blob=read_url(row["url"],row["size"])
        if len(blob)!=row["size"] or hashlib.sha512(blob).hexdigest()!=row["sha512"]:raise ValueError("Digest differs: "+name)
        with tempfile.TemporaryDirectory(dir=dest) as tmp:
            stage=Path(tmp)/name;stage.write_bytes(blob)
            try:os.link(stage,final)
            except FileExistsError:
                if not matches(final,row):raise
    return len(rows)
if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--dest",type=Path,default=None)
    p.add_argument("--source",action="append",type=Path,default=[CACHE/"samples"]);p.add_argument("--offline",action="store_true")
    p.add_argument("--side",choices=("server","client"),default="server")
    o=p.parse_args()
    directory=o.dest or CACHE/("performance-samples" if o.side=="server" else "performance-client-samples")
    print(f"Verified {run(directory,o.source,o.offline,o.side)} original JARs in {directory}")
