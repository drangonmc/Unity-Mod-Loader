"""Require the same Paper plugin bytes and observable outcomes on both running hosts."""
import hashlib
import json
from pathlib import Path

def validate(root,options,digest):
    runtime=digest(options.dist/"unity-loader-0.3.1-Beta.jar")
    client=digest(options.dist/"unity-client-witness.jar")
    reports={}
    pins=json.loads((root/"standalone/tests/reference/review31/originals.json").read_bytes())
    reference=root/"standalone/tests/reference"
    pins+=json.loads((reference/"review29/originals.json").read_bytes())["plugins"]
    pins+=[r for r in json.loads((reference/"protection-originals.json").read_bytes()) if r["slug"] in ("worldedit","worldguard")]
    for host in ("unity","paper"):
        folder=options.runs/host
        report=json.loads((folder/"result.json").read_bytes())
        assert report["engine"]==runtime and report["clientWitness"]==client and report["host"]==host
        assert report["paper"]=="0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
        assert report["plugins"]=={r["filename"]:r["sha512"] for r in pins}
        assert len(report["states"])==2 and report["states"][0]==report["states"][1]
        for clients in report["clientSessions"].values():
            assert {p["name"] for p in clients}=={"VolumeA","VolumeB"} and len({p["pid"] for p in clients})==2
            assert all(p["remote"] and not p["visible"] for p in clients)
        for n,h in report["logs"].items():assert Path(n).name==n and digest(folder/n)==h
        log=(folder/"first.log").read_text("utf8",errors="replace")
        for t in (*report["chatOrder"],"U31 marks 0","U31 executed-u31permit 1","U31 tp-cause COMMAND",
                  "U31 tp-cause PLUGIN","U31 plugin-teleport false","U31 region-tp true","U31 move-cancel true"):
            assert t in log,(host,t)
        for phrase in ("Views$UnsupportedCall:","Could not pass event","failed handling ","Failed to pass event","Player chat delivery","U31 executed-u31forbid","U31 uncancelled-u31cancelchat"):
            assert phrase not in log,(host,phrase)
        restarted=(folder/"restart.log").read_text("utf8",errors="replace")
        for phrase in ("Views$UnsupportedCall:","Could not pass event","failed handling ","Player chat delivery"):
            assert phrase not in restarted,(host,"restart",phrase)
        incoming=[l for l in (folder/"firstVolumeB-client.log").read_text("utf8",errors="replace").splitlines() if "[CHAT]" in l]
        assert all(any(t in l for l in incoming) for t in ("u31filter ###","u31barrierone","u31barriertwo"))
        assert not any(t in l for t in ("u31cancelchat","u31hidden","orangecactus") for l in incoming)
        disk=json.loads((folder/"offline.json").read_bytes())
        for n,pos in (("VolumeA",[64.5,80,20.5]),("VolumeB",[66.5,80,20.5])):
            state=report["states"][0];assert disk[n]["position"]==state[n]==pos
            assert disk[n]["bag"]=={k:v.split("@",1)[0] for k,v in state["bags"][n].items()}
        assert disk["VolumeA"]["bag"]["3"]=="minecraft:emerald*9" and disk["VolumeB"]["bag"]["2"]=="minecraft:bread*7"
        reports[host]=report
    u,p=reports["unity"],reports["paper"]
    assert u["plugins"]==p["plugins"] and u["witness"]==p["witness"] and u["chatOrder"]==p["chatOrder"]
    assert u["states"]==p["states"]
    assert hashlib.sha256(json.dumps(u["matrix"],sort_keys=True,separators=(",",":")).encode()).hexdigest()=="8241e560bf5a4336d620f15f4486c1a627b2e2e21db888bedc9fd26f4a25f99f"
    assert u["review"]==dict(lethal=["pre","post","death","drops"],zeroPost=False,forgeCancelPost=False,playerSource=11,playerReplacedWith=3,shieldDurability=2)
    contracts=json.loads(options.contract_result.read_bytes())
    assert contracts["loader"]==runtime and contracts["passed"] and contracts["menuSessions"]==2 and contracts["configTypes"]
    assert digest(options.contract_result.parent/"contracts.log")==contracts["log"]
    regression=json.loads((options.runs/"previous/result.json").read_bytes())
    assert regression["loader"]==runtime and regression["clientWitness"]==client and regression["runs"][0]==regression["runs"][1]
    return reports
