"""Compare two local loader binaries using the same game, same JAR, and alternating JVM processes."""
import argparse, hashlib, json, re, shutil, statistics, subprocess, tempfile
from pathlib import Path
from acceptance import ROOT,DIST,EVIDENCE,VERSION,digest,clean_env

def run(old,cache,sample):
    jars={"previous":old.resolve(),"candidate":(DIST/f"unity-loader-{VERSION}.jar").resolve()}
    report={"candidateSHA256":digest(jars["candidate"]),"previousSHA256":digest(jars["previous"]),"sourceModSHA512":digest(sample,"sha512"),"resource":"laserbladetools.mixins.json","readsPerBatch":500,"warmupBatchesPerJVM":2,"batches":{name:[] for name in jars}}
    with tempfile.TemporaryDirectory(dir=ROOT/".work") as temp:
        game=Path(temp);(game/"mods").mkdir();src=Path(shutil.copy2(sample,game/"mods"))
        for name in ("previous","candidate","candidate","previous"):
            exe=jars[name]
            p=subprocess.run(["java","-cp",str(exe),str(ROOT/"standalone/tests/ArchiveReads.java"),str(exe),str(cache.resolve()),str(game),str(src)],cwd=game,env=clean_env(),stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=True)
            traces=p.stdout.decode("utf8",errors="replace")
            elapsed=[int(x) for x in re.findall(r"UNITY_READ_NS (\d+)",traces)]
            if len(elapsed)!=7:raise ValueError(traces[-1500:])
            checksum=re.search(r"UNITY_RESOURCE ([0-9a-f]{64})",traces)[1]
            if report.setdefault("resourceSHA256",checksum)!=checksum:raise ValueError("Input resource differs")
            report["batches"][name].extend(elapsed)
    report["medianMicrosecondsPerRead"]={n:statistics.median(b)/500/1000 for n,b in report["batches"].items()}
    out=EVIDENCE/"resource-reads.json";out.write_text(json.dumps(report,indent=2))
    print(report["medianMicrosecondsPerRead"])
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for name in ("previous","cache","sample"):p.add_argument("--"+name,type=Path,required=True)
    a=p.parse_args();run(a.previous,a.cache,a.sample)
