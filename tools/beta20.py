"""Release evidence for the standalone 26.2 mixed-mod Beta."""
import csv
import hashlib
import json
import math
from pathlib import Path

def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream,"sha256").hexdigest()

def require(condition, message):
    if not condition: raise ValueError(message)

def evidence(root, directory, jar):
    digest=sha256(jar)
    spec=root/"standalone/performance-pack.json"
    files=json.loads(spec.read_bytes())["files"]
    authors=[m for m in files if m["role"]=="gameplay"]
    require(50<=len(authors)<=100 and len({m["project"] for m in authors})==len(authors)
            and all(m["classes"]>0 for m in authors) and {m["platform"] for m in authors}=={"forge","fabric","neoforge"},"Author gameplay corpus")
    expected={m["filename"]:m["sha512"] for m in files}
    require(len(expected)==len(files),"Duplicate corpus archive")
    qa=directory/"candidate65.json"
    r=json.loads(qa.read_bytes())
    data_pack=json.loads((root/"standalone/universal-data-sample.json").read_bytes())
    require(r["loader"]==digest and r["samples"]=={**expected,data_pack["filename"]:data_pack["sha512"]},"Candidate gameplay must use the locked release binary and pack")
    require(len({p["uuid"] for p in r["status"]})==2 and all(p["gpu"] and p["remote"] and not p["visible"] for p in r["status"]),"Two remote rendered clients")
    for field,match in {"initial":"load 23","updated":"reload 71","forgeInitial":"load 31","forgeUpdated":"reload 59","modelBake":"custom layer baked"}.items():
        require(len(r[field])==2 and all(match in s for s in r[field]),field)
    for field,match in {"biomes":"spawnWeight=17 feature=true resistance=700 barter=true markers=","resources":"energy=23 fluidMb=750 droplets=60750",
                        "transfer":"simulated=7 aborted=7 committed=5 invalidated=true","attachmentWrite":"stored tile=42 chunk=43","attachmentRead":"restored tile=42 chunk=43",
                        "loot":"Found 3 matching","prismarine_crystals":"Found 1 matching","echo_shard":"Found 1 matching"}.items():
        require(match in r[field],field)
    require(int(r["biomes"].split("markers=")[-1])>0 and r["restartBarrel"]==5,"World generation and persisted container")
    require(r["savedBags"]==[6,9] and [s["uuid"] for s in r["status"]]==[s["uuid"] for s in r["reconnected"]],"Persisted inventories and player attachments")
    diagnostic=directory/"mod-diagnostic.json";message=directory/"mod-diagnostic.txt"
    negative=json.loads(diagnostic.read_bytes())
    require(negative["loader"]==digest and negative["exit"]!=0 and negative["report"]==sha256(message),"Original mod conflict diagnostic")
    callbacks=directory/"callback-costs.json";counts=json.loads(callbacks.read_bytes())
    require(counts["loader"]==digest and set(counts["batches"])=={"0","1","8","32"},"CPU dispatcher sample identity")
    for group in counts["batches"].values():
        require(set(group)=={"direct","core","forge"} and all(len(v)==20 for v in group.values()),"Dispatcher samples")
        for a,b,c in zip(group["direct"],group["core"],group["forge"]):
            require(a["checksum"]==b["checksum"]==c["checksum"] and min(v["elapsedNs"] for v in (a,b,c))>0,"Dispatcher checksum")
    dedicated=directory/"dedicated-sdk.json";sdk=json.loads(dedicated.read_bytes())
    require(sdk["files"]=={p.name:sha256(p) for p in (jar,jar.parent/"unity-world-marker.jar")} and sdk["javaRelease"]==25,"Dedicated SDK source rebuild")
    sources=[qa,spec,diagnostic,message,callbacks,dedicated]
    for file,h in r["logs"].items():
        path=directory/file
        require(path.parent==directory and sha256(path)==h,"Changed gameplay log "+file);sources.append(path)
    trials=[]
    for mode in ("vanilla","bare","mixed"):
        path=directory/("release65-"+mode+".json")
        result=json.loads(path.read_bytes());trials.append(result)
        require(result["server"]==mode and result["loader"]==(None if mode=="vanilla" else digest),"Performance binary "+mode)
        require(result["sampleJars"]==(expected if mode=="mixed" else {}),"Performance mod set "+mode)
        require(len({p["uuid"] for p in result["clients"]})==2 and all(p["gpu"] and p["remote"] and not p["visible"] for p in result["clients"]),"Performance clients "+mode)
        require(result["walkDistances"] and all(len(pair)==2 and min(pair)>=5 for pair in result["walkDistances"]),"Walking load "+mode)
        raw=directory/("release65-"+mode+"-ticks.csv")
        require(result["rawSHA256"]==sha256(raw),"Tick samples "+mode)
        with raw.open() as stream: rows=list(csv.DictReader(stream))
        ms=sorted(int(row["tickNanoseconds"])/1e6 for row in rows)
        require(len(ms)==result["samples"]==100*result["windows"] and len(ms)>=1000 and ms[0]>0,"Sample count "+mode)
        for name,fraction in [("p50",.5),("p95",.95),("p99",.99),("max",1.)]:
            require(ms[math.ceil(fraction*len(ms))-1]==result["mspt"][name],"Percentile "+name)
        require(result["peakWorkingSetBytes"]>0 and result["startupSeconds"]>0,"Memory and startup "+mode)
        sources.extend([path,raw])
    require(len({r["seedLevelDat"] for r in trials})==len({r["serverBundle"] for r in trials})==1,"Matched save and MC bundle")
    return sources
