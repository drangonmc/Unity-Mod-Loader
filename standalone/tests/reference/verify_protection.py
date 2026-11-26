"""Require current, matched Paper/Unity evidence before deriving the compatibility table."""
import argparse,hashlib,json,subprocess,sys,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from acceptance import VERSION,digest

def verify(o):
    base=o.out
    def read(name):return json.loads((base/name).read_bytes())
    def logs(path,record):
        for name,expected in record["logs"].items():
            file=base/path/name
            assert file.parent==base/path and digest(file)==expected,file
    binary=o.dist/f"unity-loader-{VERSION}.jar";runtime=digest(binary)
    with zipfile.ZipFile(binary) as z:
        manifest=z.read("META-INF/MANIFEST.MF").decode("utf8").splitlines()
        assert "Main-Class: mc.unity.bootstrap.Main" in manifest
    a=read("accepted/paper/result.json");b=read("accepted/unity/result.json")
    assert a["runtime"]==digest(o.paper) and b["runtime"]==runtime
    assert a["client"]==b["client"]==runtime and a["witness"]==b["witness"]==digest(o.dist/"unity-client-witness.jar")
    assert a["plugin"]==b["plugin"] and a["authors"]==b["authors"] and not a["mixed"]
    assert {k:len(v) for k,v in b["mixed"].items()}=={"server":77,"ShieldA":75,"ShieldB":75}
    assert len(a["runs"])==len(b["runs"])==2
    assert a["runs"]==b["runs"],"Different Paper/Unity events or state"
    for name,doc in [("paper",a),("unity",b)]:logs("accepted/"+name,doc)
    trace=(base/"accepted/unity/events.tsv").read_bytes()
    assert trace==(base/"accepted/paper/events.tsv").read_bytes()
    assert trace.count(b"skippy.tracked\ttrue")==1 and trace.count(b"letmedespawn.players\t2")==2
    cases=b["runs"][0];saved=b["runs"][1]
    assert all(saved[k]==cases[k] for k in ("health","actorHealth","bags","blocks","joins"))
    assert [saved["health"],saved["actorHealth"]]==[[13.0]*3,[15.0]*3]
    for k in ("empty-plugin","lava-empty-plugin","fill-deny","lava-fill-deny"):
        rows=cases["events"][k]
        assert [v.split()[-1] for step,v in rows if step in ("low","monitor")]==["false","true"]
    for k in ("empty-ok","lava-empty-ok","fill-ok"):
        assert all(v.endswith("false") for step,v in cases["events"][k] if step in ("low","monitor"))
    items=[]
    for phase,arrows,wear,blood in [("shoot-deny",11,0,20),("launch-deny",10,0,20),("hit-deny",9,1,20),("damage-deny",8,2,20),("damage-ok",7,3,16)]:
        result=cases["shots"][phase];bag=result["bags"]["ShieldA"]
        assert result["health"]==[blood]*3 and bag["2"]==f"minecraft:bow*1@{wear}" and bag["3"]==f"minecraft:arrow*{arrows}@0"
        items.append({"phase":phase,"arrows":arrows,"bowWear":wear,"targetHealth":blood})
    timing=read("timing-final/comparison.json")
    assert timing["binarySHA256"]["unity"]==runtime and not timing["differences"] and len(timing["observations"]["paper"])==12
    logs("timing-final",timing)
    stronger=read("stronger/comparison.json")
    assert stronger["binarySHA256"]==timing["binarySHA256"] and not stronger["differences"]
    assert len(stronger["observations"]["paper"]["stronger.callbacks"])==2
    assert stronger["observations"]["unity"]["stronger.health"]==[[9.0]]
    logs("stronger",stronger)
    wg=read("worldguard-final/result.json")
    assert wg["loader"]==runtime and wg["paper"]==a["runtime"] and "Server.getHelpMap" in wg["unityFirstGap"]
    logs("worldguard-final",wg)
    save=read("saves/saves.json");assert save["runtime"]==runtime;logs("saves",save)
    assert all(v["goldBlock"] and v["lastGood"]==7 and v["other"]==14 and v["nextListener"] for v in save["results"].values())
    hashes={p.name:digest(p) for p in o.dist.glob("*.jar")}
    assert len(hashes)==3 and hashes=={p.name:digest(p) for p in o.rebuild.glob("*.jar")}
    for side in ["server","client"]:
        doc=read("anchors/"+side+".json");assert doc["unity"]==runtime and set(doc["counts"])=={"ok"}
    mixed=base/"regression/mixed-reference.json";inventory=base/"inventory/inventory24.json"
    subprocess.run([sys.executable,Path(__file__).resolve().parents[1]/"bukkit_coverage.py","--runtime",binary,
                    "--mixed",mixed,"--inventory",inventory,"--protection",base/"accepted/unity/result.json","--out",base/"coverage"],check=True)
    coverage=read("coverage/bukkit-coverage.json")
    receipts=["accepted/paper/result.json","accepted/unity/result.json","worldguard-final/result.json","timing-final/comparison.json","stronger/comparison.json",
              "regression/mixed-reference.json","inventory/inventory24.json","saves/saves.json","coverage/bukkit-coverage.json",
              "anchors/server.json","anchors/client.json"]
    summary={"mc":"26.2","version":VERSION,"entrypoint":"mc.unity.bootstrap.Main","runtime":runtime,"byteIdentical":hashes,"samePlugin":a["plugin"],
             "traceSHA256":hashlib.sha256(trace).hexdigest(),"traceRows":len(trace.splitlines()),"timingObservations":12,
             "originals":b["authors"],"worldguard":wg["unityFirstGap"],"shots":items,"saved":saved,"coverage":coverage["gameOrigins"],
             "receipts":{n:digest(base/n) for n in receipts}}
    (base/"verified.json").write_text(json.dumps(summary,indent=2),"utf8")
    print("Verified",runtime,"events",summary["coverage"])
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ("out","dist","rebuild","paper"):p.add_argument("--"+k,type=lambda s:Path(s).resolve(),required=True)
    verify(p.parse_args())
