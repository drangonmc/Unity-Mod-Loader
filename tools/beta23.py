"""Bind the complete official Fabric API and two-client gameplay receipts to the release."""
import json
from beta20 import require, sha256

def evidence(root, directory, jar):
    version = "0.2.3-Beta"
    spec = root/"standalone/beta-gameplay.json"
    api = json.loads(spec.read_bytes())["fabricApi"]
    name = api["url"].rsplit("/",1)[1]
    vendor = root/"standalone/vendor"/name
    import hashlib
    require(hashlib.sha512(vendor.read_bytes()).hexdigest()==api["sha512"], "Official Fabric API original")
    sample = root/"standalone/fabric-play-sample.json"
    vision = json.loads(sample.read_bytes())["sha512"]
    full = directory/"full-fabric-api.json"
    r = json.loads(full.read_bytes())
    require(r["loader"]==sha256(jar) and r["fabricApi"]==api["sha512"] and r["vision"]==vision, "Full API proof identity")
    pack=json.loads((root/"standalone/performance-pack.json").read_bytes())["files"]
    originals={m["filename"]:m["sha512"] for m in pack}
    require(r["mixed"]["server"]==originals and r["mixed"]["A"]==r["mixed"]["B"],"Full official API plus all mixed originals")
    require(len(r["runs"])==2 and r["datagen"]=={"owner":"qa23_full"}, "Full API restart and generated provider output")
    stamps = (root/"standalone/src/main/resources/META-INF/unity/fabric-api-modules.sha256").read_text().splitlines()
    wanted={row.split()[1]:row.split()[3] for row in stamps}
    require(len(wanted)==43 and sum(s=="client" for s in wanted.values())==7, "Official 26.2 module distribution")
    uuids=[]
    for run in r["runs"]:
        server=run["serverModules"]; clients=run["clientModules"]
        require(set(server)=={k for k,v in wanted.items() if v!="client"} and sum(server.values())==438,"Server API class sources")
        require(len(clients)==2 and clients[0]==clients[1] and set(clients[0])==set(wanted) and sum(clients[0].values())==700,"Client API class sources")
        require(run["mixinTargets"]==[220,375,375],"Original Minecraft Mixin targets")
        require(all(p["gpu"] and p["remote"] and not p["visible"] for p in run["status"]), "Rendered hidden clients")
        uuids.append([p["uuid"] for p in run["status"]])
        require("QA23 FULL API COMMAND" in run["command"] and "QA23_DATA_FUNCTION" in run["resource"],"Commands and reload")
    require(len(set(uuids[0]))==2 and uuids[0]==uuids[1],"Returning real clients")
    light=json.loads((directory/"candidate65.json").read_bytes())
    for batch in [light["fabricPlay"],light["restartFabricPlay"],*[v["play"] for v in r["runs"]]]:
        require([p["uuid"] for p in batch]==uuids[0],"Play identities")
        for p in batch:
            require(p["echo"].startswith(p["uuid"]+"/") and p["echo"] in p["serverEcho"] and
                    "translated overlay 88" in p["asset"] and "QA23_SERVER_enable_" in p["enable"] and
                    "QA23_SERVER_clear_" in p["clear"], "Raw VisionToggle and two-way Play")
    require("PASS independent registry and watcher acquisition during watch and poll callbacks" in (directory/"lifecycle.log").read_text("utf8",errors="replace"),"Watcher cross-thread callbacks")
    outputs=[full,sample,vendor,root/"standalone/tests/full_fabric_api.py",root/"tools/beta23.py",
             root/"standalone/BETA-0.2.3.md",root/"Install-Unity-Fabric-API.cmd"]
    for name,h in r["logs"].items():
        path=directory/name
        require(path.parent==directory and sha256(path)==h,"Full API log "+name)
        outputs.append(path)
    return outputs
