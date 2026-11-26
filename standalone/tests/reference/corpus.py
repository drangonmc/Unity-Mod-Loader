"""Join a frozen ranked download sample to audited gaps. Never load classes from the sample JARs."""
import argparse, collections, hashlib, json, os, subprocess, sys, tempfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from acceptance import ROOT,VERSION,digest

def metrics(rows):
    all_symbols=collections.defaultdict(list)
    for row in rows:
        for symbol in row["need"]:all_symbols[symbol].append(row)
    clean=[r for r in rows if not r["need"]]
    def stats(symbol,affected):
        sole=[r for r in affected if len(r["need"])==1]
        return {"symbol":symbol,"references":len(affected),"sole":len(sole),
                "soleProjectDownloads":sum(r["downloads"] for r in sole),"soleProjects":[r["slug"] for r in sole]}
    priority=sorted((stats(k,v) for k,v in all_symbols.items()),key=lambda x:(-x["soleProjectDownloads"],-x["references"],x["symbol"]))
    return {"n":len(rows),"staticClean":len(clean),"sampleProjectDownloads":sum(r["downloads"] for r in rows),
            "staticCleanProjectDownloads":sum(r["downloads"] for r in clean),"distinctGapKeys":len(priority),"marginals":priority}

def main(o):
    listing=json.loads(o.listing.read_bytes());analysis=json.loads(o.analysis.read_bytes())
    assert listing["minecraft"]=="26.2"
    report={"sourceDate":listing["date"],"listingSHA256":digest(o.listing),"reviewAnalysisSHA256":digest(o.analysis),
            "auditedUnitySHA256":digest(o.release),"groups":{}}
    entries={}
    for name in ("fabric","forge","neoforge","plugin"):
        group=analysis["plugins"] if name=="plugin" else analysis["groups"][name]
        metadata=sorted((r for r in listing["rows"] if r["group"]==name and r["rank"]<=50),key=lambda r:r["rank"])
        assert [r["rank"] for r in metadata]==list(range(1,51))
        assert len({r["project"] for r in metadata})==50
        gaps={r["file"]:r for r in group["rows"]}
        rows=[]
        for m in metadata:
            file=f"{m['rank']:03}-{m['filename']}"
            jar=o.archives/name/file
            assert digest(jar,"sha512")==m["sha512"] and jar.stat().st_size==m["size"],file
            g=gaps[file];assert g["downloads"]==m["downloads"]
            need=g["need"]
            if isinstance(need,dict):need={area+" | "+sym for area,syms in need.items() for sym in syms}
            row={"project":m["project"],"slug":m["slug"],"downloads":m["downloads"],"rank":m["rank"],
                 "file":file,"versionId":m["version"],"url":m["url"],"bytes":m["size"],"sha512":m["sha512"],"need":sorted(need)}
            rows.append(row)
        entries[name]=rows
        report["groups"][name]=metrics(rows)
        assert report["groups"][name]["staticClean"]==group["clean"]
    cache=ROOT/"cache"/VERSION
    cp=[o.release,cache/"versions/26.2/server-26.2.jar",*sorted((cache/"libraries").rglob("*.jar"))]
    with tempfile.TemporaryDirectory(dir=ROOT/".work") as tmp:
        path=Path(tmp)/"refs.json"
        subprocess.run(["java","-Xmx2g","-cp",os.pathsep.join(map(str,cp)),ROOT/"standalone/tests/CorpusPreflight.java",
                        o.archives/"plugin",path,ROOT/"standalone/vendor/bukkit"],check=True)
        refs=json.loads(path.read_bytes())
        assert set(refs)=={r["file"] for r in entries["plugin"]}
        names=collections.Counter();all_uses=0
        for r in entries["plugin"]:
            symbols=refs[r["file"]]["pluginMemberReferences"]
            names.update(symbols);all_uses+=len(symbols)
            r["pluginMemberReferences"]=symbols
            r["sdkGaps"]=refs[r["file"]]["sdkGaps"]
        report["bukkitScan"]={"files":len(refs),"distinctMemberDescriptors":len(names),"fileMemberPairs":all_uses,
                              "mostReferenced":names.most_common(30),"scope":"direct classfile member references; no reflection or dynamic dispatch inference"}
    report["bukkitScan"]["sdkGapFiles"]=sum(bool(r["sdkGaps"]) for r in entries["plugin"])
    report["bukkitScan"]["sdkGapFileMemberPairs"]=dict(collections.Counter(kind for r in entries["plugin"] for kind in r["sdkGaps"].values()))
    # Keep popular boundary cases in the panel; ease-of-implementation alone biases the result.
    audit={r["file"]:r for r in analysis["plugins"]["rows"]}
    panel=[];picked=set()
    def eligible(r):
        detail=audit[r["file"]]
        return not detail["descriptor"] and not detail["nms"]
    def select(options,n,why):
        for r in [x for x in options if x["project"] not in picked][:n]:
            panel.append({"project":r["project"],"slug":r["slug"],"rank":r["rank"],"gapKeys":len(r["need"]),"reason":why})
            picked.add(r["project"])
    simple=[r for r in entries["plugin"] if eligible(r)]
    select(sorted(simple,key=lambda r:(len(r["need"]),r["rank"])),16,"smaller listed gap sets")
    select(simple,4,"higher downloads")
    select([r for r in entries["plugin"] if not eligible(r)],4,"loader or NMS boundary")
    select(entries["plugin"],24-len(panel),"ranked reserve")
    assert len(panel)==24
    report["nextPluginPanel"]=panel
    report["holdoutFromReview"]=analysis["cross"]
    o.out.mkdir(parents=True,exist_ok=True)
    (o.out/"matrix.json").write_text(json.dumps(entries,ensure_ascii=False,separators=(",",":")),"utf8")
    report["matrixSHA256"]=digest(o.out/"matrix.json")
    (o.out/"ranking.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),"utf8")
    print({k:(v["staticClean"],v["n"]) for k,v in report["groups"].items()},report["bukkitScan"]["distinctMemberDescriptors"])
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ["listing","analysis","archives","release","out"]:p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    main(p.parse_args())
