"""Bind each baseline result to the actual artifact and its fresh, local evidence files."""
import argparse,json,sys,xml.etree.ElementTree as ET,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from acceptance import ROOT,DIST,VERSION,digest
def main(o):
    out=o.out; release=DIST/f"unity-loader-{VERSION}.jar";runtime=o.candidate/release.name
    current=digest(runtime)
    def get(path):return json.loads((out/path).read_bytes())
    def logs(path,items):
        for name,h in items.items():
            f=out/path/name
            assert f.parent==out/path and digest(f)==h,f
    a=get("released/comparison.json");b=get("candidate/comparison.json")
    assert a["binarySHA256"]["unity"]==digest(release) and b["binarySHA256"]["unity"]==current
    for k in ("paper","sdk","fixture","clientLoader","clientWitness"):
        assert a["binarySHA256"][k]==b["binarySHA256"][k]
    assert a["observations"]["paper"]==b["observations"]["paper"]
    logs("released",a["logs"]);logs("candidate",b["logs"])
    window=get("hurt-window/comparison.json");logs("hurt-window",window["logs"])
    assert window["binarySHA256"]==b["binarySHA256"]
    for host in ("paper","unity"):assert window["observations"][host]["window.health"]==[[14.0]]
    for folder in ["saves","windows-control"]:
        result=get(folder+"/saves.json");assert result["runtime"]==current
        logs(folder,result["logs"])
        for row in result["results"].values():assert row==dict(lastGood=7,other=14,goldBlock=True,nextListener=True)
    order=get("order/order.json");assert order["runtime"]==current and order["oldContextResolvesCurrentPlayer"];logs("order",order["logs"])
    policy=get("capability/capability.json");assert policy["runtime"]==current
    logs("capability",{k+".log":r["log"] for k,r in policy["outcomes"].items()})
    mix=get("mixed/mixed-reference.json")
    assert mix["loader"]==current and len(mix["runs"])==2
    logs("mixed",mix["logs"])
    platforms={}
    for k in ("fabric","neo"):
        folder="platforms/"+k
        result=get(folder+"/result.json")
        assert result["unitySHA256"]==current and result["official"]["exit"]==0
        logs(folder,{"official.log":result["official"]["logSHA256"],"official.xml":result["official"]["xmlSHA256"],"unity.log":result["unity"]["logSHA256"]})
        cases=list(ET.parse(out/folder/"official.xml").iter("testcase"))
        ids=[c.get("name","") for c in cases if c.get("name")!="minecraft:always_pass"]
        assert len(ids)==2 and not any(len(c) for c in cases)
        parity=result["unity"]
        if parity["exit"]!=0:
            diagnostic=(out/folder/"unity-preflight.txt").read_text("utf8")
            assert "Duplicate or reserved mod class" in diagnostic and not parity.get("timedOut")
        else:
            unity_xml=out/folder/"unity.xml"
            assert digest(unity_xml)==parity["xmlSHA256"]
            actual=list(ET.parse(unity_xml).iter("testcase"))
            assert set(ids)<={case.get("name") for case in actual} and not any(len(case) for case in actual)
        platforms[k]={"officialCases":ids,"unityExit":parity["exit"],"unityCases":parity["cases"]}
    corpus=get("corpus/ranking.json")
    assert corpus["auditedUnitySHA256"]==digest(release) and corpus["matrixSHA256"]==digest(out/"corpus/matrix.json")
    assert len(corpus["nextPluginPanel"])==24
    anchor={}
    for file in sorted((out/"anchors").glob("*.json")):
        doc=json.loads(file.read_bytes())
        if "steps" in doc:
            assert doc["unity"]==current
            anchor[file.stem]=doc["counts"]
    assert get("anchors/upgrade-server-26.3.json")["game"]==get("anchors/upgrade-input.json")["game"]
    adapters={}
    for key,filename in {"client":"unity-client-api-0.2.4.jar","puzzles":"unity-puzzles-platform-26.2.5.jar","transfer":"unity-transfer-adapter-0.2.3.jar","biome":"unity-biome-adapter-0.2.3.jar"}.items():
        h=digest(ROOT/"standalone/lib"/filename);assert digest(o.inputs/("verify-"+key+".jar"))==h
        adapters[filename]=h
    builds={f.name:digest(f) for f in o.candidate.glob("*.jar")}
    assert len(builds)==3 and builds=={f.name:digest(f) for f in o.rebuild.glob("*.jar")}
    package=DIST/"Unity-0.2.4-Development-Preview-MC26.2.zip"
    assert digest(package)=="ee190b4acbd6b7299883cfc2c699d2959a4acca3b749625348eca89562a01402"
    with zipfile.ZipFile(package) as z:
        import hashlib
        assert hashlib.sha256(z.read("versions/"+VERSION+"/"+release.name)).hexdigest()==digest(release)
        for f,h in adapters.items():assert hashlib.sha256(z.read("standalone/lib/"+f)).hexdigest()==h
    paths=["released/comparison.json","candidate/comparison.json","hurt-window/comparison.json","saves/saves.json","windows-control/saves.json","order/order.json","capability/capability.json","mixed/mixed-reference.json","platforms/fabric/result.json","platforms/neo/result.json","corpus/ranking.json"]
    paths.extend("anchors/"+p.name for p in (out/"anchors").glob("*.json"))
    index={"mc":"26.2","publishedZIP":digest(package),"releasedLoader":digest(release),"candidate":current,
           "cleanRebuild":builds,"shippedAdaptersRebuilt":adapters,"originalDifferences":list(a["differences"]),
           "candidateDifferences":list(b["differences"]),"supplementalDifferences":list(window["differences"]),
           "platforms":platforms,"anchors":anchor,"receipts":{p:digest(out/p) for p in sorted(paths)}}
    (out/"index.json").write_text(json.dumps(index,indent=2),"utf8")
    print("PASS linked reference evidence for",current)
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for name in ("out","inputs","candidate","rebuild"):p.add_argument("--"+name,type=lambda s:Path(s).resolve(),required=True)
    main(p.parse_args())
