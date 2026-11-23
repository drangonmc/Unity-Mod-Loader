"""Summarize exact candidate evidence without promoting the release."""
from pathlib import Path
import json
import re
from acceptance import ROOT, DIST, EVIDENCE, VERSION, digest

def verify():
    jar = DIST / f"unity-loader-{VERSION}.jar"
    loader_hash = digest(jar)
    full=EVIDENCE/"beta-mixed-gameplay.json"
    if full.exists() and json.loads(full.read_bytes()).get("loader")==loader_hash:
        import sys
        sys.path.insert(0,str(ROOT/"tools"))
        import release
        release.gameplay_evidence()
        release.world_evidence()
        state=json.loads((ROOT/"versions/state.json").read_bytes())["active"]
        promoted=state["version"]==VERSION
        if promoted:
            cold=json.loads((EVIDENCE/"unpacked.json").read_bytes())
            assert cold["archiveSHA256"]==digest(release.ARCHIVE)==state["files"][-1][2]
        data=json.loads(full.read_bytes())
        phases=json.loads((EVIDENCE/"puzzles-57-phases.json").read_bytes())
        summary={"version":VERSION,"minecraft":"26.2","loaderSHA256":loader_hash,"active":state["version"],"promoted":promoted,
                 "authorMods":21,"twoGraphicalClients":True,"worldAndPlayerRestart":True,"windowsVisible":data["runs"][0]["windowVisible"],
                 "phaseCounts":phases["counts"],"gameplay":full.name,"trials":{k:v for k,v in data["runs"][0]["speed"].items()}}
        integrated=json.loads((EVIDENCE/"integrated.json").read_bytes())
        wire=json.loads((EVIDENCE/"registry-wire.json").read_bytes())
        summary.update(singleplayerWorlds=[v["world"] for v in integrated["visits"]],
            singleplayerValues=[v["extensions"]["server"]["worldValue"] for v in integrated["visits"]],
            remoteRegistryValue=wire["client"]["worldValue"],singleplayerProof="integrated.json",registryProof="registry-wire.json")
        if promoted: summary.update(archiveSHA256=cold["archiveSHA256"],sourceRebuild=cold["byteIdenticalRebuild"],adapterRebuild=cold["adapterRebuildSHA256"],clientApiRebuild=cold["clientApiRebuildSHA256"],worldFixtureRebuild=cold["worldFixtureRebuild"])
        (EVIDENCE/"release-status.json").write_text(json.dumps(summary,indent=2,ensure_ascii=False)+"\n","utf8")
        return
    baseline = []
    for name in ("forge-samples","forge-content","forge-containers","ecosystem","creative-content"):
        baseline.extend(json.loads((ROOT/"standalone"/(name+".json")).read_bytes()))
    spec = json.loads((ROOT/"standalone/beta-gameplay.json").read_bytes())
    subsets = []
    for name in ("ore", "ore-foundation"):
        report = json.loads((EVIDENCE/(name+"-gameplay.json")).read_bytes())
        assert report["version"] == VERSION and report["loader"] == loader_hash
        samples = baseline + [x for x in spec["originals"] if x["id"]=="fjZyYXGJ"] + spec["fabricModules"]
        assert report["originals"] == {x["filename"]:x["sha512"] for x in samples}
        first,last = report["runs"]
        assert first["bags"] == last["bags"] and first["pinned"] == ["SEARCH","INVENTORY"]
        assert [x[0] for x in first["players"]] == [x[0] for x in last["players"]]
        for phase,run in zip(("first","restart"),(first,last)):
            assert len({p[1] for p in run["players"] if p[2]})==2
            logs = [EVIDENCE/(name+"-"+phase+suffix) for suffix in (".log","-A-client.log","-B-client.log")]
            assert run["logs"] == {p.name:digest(p) for p in logs}
            server=logs[0].read_text("utf8")
            assert all("[Server] WORLD_"+str(x)+"_"+str(y) in server for x in (39,40,41) for y in (201,202,203))
            assert "[Server] WORLD_43_201" in server and 'minecraft:damage": 9' in run["bags"]["UnityMinerA"]
            if name=="ore-foundation":
                assert run["clientData"]==[83,83] and run["serverData"].endswith("83 CONFIG 23")
        assert digest(EVIDENCE/(name+"-world.png"))==first["screenshot"]
        subsets.append({"test":name,"players":[p[0] for p in first["players"]],"report":name+"-gameplay.json"})
    lifecycle=json.loads((EVIDENCE/"lifecycle.json").read_bytes())
    assert lifecycle["loaderSHA256"]==loader_hash and lifecycle["logSHA256"]==digest(EVIDENCE/"lifecycle.log") and lifecycle["exit"]==0
    mixed=EVIDENCE/"beta-mixed-first.log"
    failure=json.loads((EVIDENCE/"beta-mixed-failure.json").read_bytes())
    assert failure["exit"]!=0 and failure["loader"]==loader_hash and failure["logSHA256"]==digest(mixed)
    assert failure["originals"]=={x["filename"]:x["sha512"] for x in baseline+spec["originals"]+spec["fabricModules"]}
    log=mixed.read_text("utf8")
    errors=re.findall(r"^(.+\.jar) \((\d+) distinct first errors across classes\)$",log,re.M)
    assert errors and "Standalone ready" not in log
    active=json.loads((ROOT/"versions/state.json").read_bytes())["active"]
    assert active["version"]=="0.1.1-Alpha"
    for _,name,expected in active["files"]:
        assert digest(ROOT/"versions"/active["version"]/name)==expected
    assert (ROOT/"cache"/active["version"]/"mojang-server-26.2.jar").is_file()
    rebuild=json.loads((EVIDENCE/"candidate-rebuild.json").read_bytes())
    assert rebuild["byteIdenticalRebuild"]=={p.name:digest(p) for p in DIST.glob("*.jar")}
    result={"candidate":VERSION,"minecraft":"26.2","loaderSHA256":loader_hash,"promoted":False,"active":active["version"],
            "completeAuthorSet":len(baseline)+2,"testedAuthorSet":len(baseline)+1,"passed":subsets,
            "rebuild":"candidate-rebuild.json","reviewRegressionLog":"lifecycle.log","completeSetLog":mixed.name,"completeSetLogSHA256":digest(mixed),
            "preflightDistinctFirstErrors":dict((name,int(n)) for name,n in errors),"previousReleaseVerified":True}
    path=EVIDENCE/"candidate-status.json"
    path.write_text(json.dumps(result,indent=2)+"\n",encoding="utf8")
if __name__=="__main__":verify()
