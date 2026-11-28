"""Validate the matching machine, plugin and Paper receipts before packaging the rebuilt 0.2.9."""
import json
from pathlib import Path

def validate(root,options,digest):
    pins=json.loads((root/"standalone/tests/reference/review29/originals.json").read_bytes())
    engine=digest(options.dist/("unity-loader-"+getattr(options,"version","0.2.9-Beta")+".jar"))
    expected={r["filename"]:r["sha512"] for r in pins["plugins"]}
    reports={}
    for host in ("unity","paper"):
        folder=options.runs/host
        result=json.loads((folder/"result.json").read_bytes())
        assert result["plugins"]==expected and result["runs"][0]==result["runs"][1],host
        assert result.get("loader",result.get("client"))==engine
        assert len(result["runs"])==2
        for file,h in result["logs"].items():
            assert Path(file).name==file and digest(folder/file)==h,(host,file)
        for phase,action in (("first","write"),("restart","read")):
            log=(folder/(phase+".log")).read_text("utf8",errors="replace")
            for who,v in (("VolumeA",291),("VolumeB",292)):
                assert f"UNITY29 {action} {who} permission=LuckPerms economy=EssentialsX Economy balance={v}.0 pdc={v}" in log
            assert "UNITY29_ACTUAL_FAILURE" not in log and "Could not pass event" not in log
        reports[host]=result
    u=reports["unity"];paper=reports["paper"]
    assert paper["paper"]=="0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
    assert all(r["essentialsHome"]["xyz"]==paper["runs"][i][-1]["xyz"] for i,r in enumerate(u["runs"]))
    assert u["probe"]==paper["probe"] and u["clientWitness"]==digest(options.dist/"unity-client-witness.jar")
    for phase in u["runs"]:
        assert phase["26"]==phase["36"]
        v=phase["36"];assert (v["fluid"],v["energy"],v["sourceCount"],v["chestCount"])==(7250,12345,1,11)
        assert "facing=north" in v["facing"] and len(v["clientViews"])==2
        for client in v["clientViews"]:
            assert client["energy"]==12345 and client["count"]==7 and "machine_serial" in client["component"]
    for name,files in u["authors"].items():
        assert len(files) >= (82 if name=="server" else 80)
        for row in pins["machines"]:assert files[row["filename"]]==row["sha512"]
    assert u["worldEditGuard"]=={r["slug"]:r["sha512"] for r in json.loads(
        (root/"standalone/tests/reference/protection-originals.json").read_bytes()) if r["slug"] in ("worldedit","worldguard")}
    review=json.loads(options.contract_result.read_bytes())
    assert review["loader"]==engine and review["passed"]
    return reports
