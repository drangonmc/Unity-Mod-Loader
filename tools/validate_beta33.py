"""Bind real Paper/Unity runs, original JARs and closed-save reads to the distributable."""
from pathlib import Path
import json
import hashlib
import uuid

def read(path):
    return json.loads(path.read_bytes())

def validate(root,o,digest):
    baseline=read(root/"standalone/tests/reference/review33/baseline.json")
    canonical=lambda obj:hashlib.sha256(json.dumps(obj,sort_keys=True,separators=(",",":"),ensure_ascii=True).encode()).hexdigest()
    additions={p["slug"]:p for p in read(root/"standalone/tests/reference/review33/originals.json")}
    plugins={}
    for slug in ("cmilib","residence"):
        p=additions[slug];plugins[p["filename"]]=p["sha512"]
    quarry=additions["quite-quarry"]
    engine=digest(o.dist/"unity-loader-0.3.3-Beta.jar")
    observer=digest(o.dist/"unity-client-witness.jar")
    reports={}
    for host in ("paper","unity"):
        d=o.runs/host;report=reports[host]=read(d/"result.json")
        assert report["host"]==host and report["machineOnly"] is False and report["engine"]==engine and report["clientWitness"]==observer
        originals=dict(report["plugins"])
        for name,sha in plugins.items():assert originals.pop(name)==sha
        assert canonical(originals)==baseline["pluginsSHA256"] and report["paper"]=="0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
        phases=["first","restart"]
        assert [r["phase"] for r in report["runs"]]==phases
        assert set(report["logs"])=={name+".log" for phase in phases for name in (phase,phase+"VolumeA-client",phase+"VolumeB-client")}
        for path,h in report["logs"].items():assert digest(d/path)==h,path
        for r in report["runs"]:
            assert len(r["clients"])==len({p["pid"] for p in r["clients"]})==2 and all(p["visible"] is False for p in r["clients"])
            assert {p["name"] for p in r["clients"]}=={"VolumeA","VolumeB"} and 0<r["stopSeconds"]<180
            for n,item in (("VolumeA","minecraft:gold_ingot*9"),("VolumeB","minecraft:iron_ingot*7")):
                assert read(d/"players.json")[n]["bag"]["4"]==r["bags"][n]["4"].split("@")[0]==item
            log=(d/(r["phase"]+".log")).read_text("utf8",errors="replace")
            for marker in ("U33 RULES END","U33 CHEST VolumeA 67 DENY","U33 CHEST VolumeB 67 ALLOW","U33 CHEST VolumeB 70 DENY"):
                assert marker in log,(host,r["phase"],marker)
            if r["phase"]=="first":assert "U33 PORTAL cancelled NETHER_PORTAL" in log and "U33_PORTAL_STAY" in log
            for failure in ("Views$UnsupportedCall:","U33 region setup or query failed","FAILED INITIALIZATION!","NoClassDefFoundError:","Exception in thread","failed handling "):
                assert failure not in log,(host,r["phase"],failure)
        assert report["runs"][0]["rules"]==report["runs"][1]["rules"] and len(report["runs"][0]["rules"])==12
        if host=="unity":
            previous={side:dict(files) for side,files in report["matrix"].items()}
            for files in previous.values():assert files.pop(quarry["filename"])==quarry["sha512"]
            assert canonical(previous)==baseline["matrixSHA256"]
            closed=read(d/"world.json")
            assert report["runs"][1]["offlineOwnerDenied"] and closed["worldUUID"]==report["runs"][1]["worldId"] and "VolumeA" in closed["quarryOwner"]
            assert uuid.UUID(closed["worldUUID"]).version==4
            for name in ("VolumeA","VolumeB"):assert name in closed["drone"+name]
            restart=(d/"restart.log").read_text("utf8",errors="replace")
            offline=restart.index("[Server] U33_OWNER_OFFLINE")
            assert "U33 POLICY EXTRACT 66, 80, 39 d72a93df-334e-34e6-81f8-67ddcdeb22db WorldGuard=false" in restart[offline:]
            first=(d/"first.log").read_text("utf8",errors="replace")
            assert first.count("U33 DRONE TYPE logisticsdrones:stone_drone worldedit=logisticsdrones:stone_drone cancelled=false")==2
            for op,pos,wg,res in (("MINE","65, 80, 25",True,True),("MINE","66, 80, 25",False,True),
                                  ("MINE","67, 80, 25",True,False),("MINE","68, 80, 25",True,False),("MINE","69, 80, 25",True,True),
                                  ("INSERT","67, 80, 35",True,False),("EXTRACT","66, 80, 39",False,True),("EXTRACT","67, 80, 35",True,False)):
                assert any(f"U33 POLICY {op} {pos} d72a93df-334e-34e6-81f8-67ddcdeb22db" in l and
                           f"WorldGuard={str(wg).lower()} Residence={str(res).lower()}" in l for l in first.splitlines()),(op,pos)
    assert reports["paper"]["runs"][0]["rules"]==reports["unity"]["runs"][0]["rules"]
    contract=read(o.contract_result)
    assert contract["loader"]==engine and contract["passed"] and contract["configTypes"] and contract["menuSessions"]==2
    assert digest(o.contract_result.parent/"contracts.log")==contract["log"]
    aux=o.runs/"other-owner";proof=read(aux/"result.json")
    assert proof["engine"]==engine and proof["sourceReport"]==digest(o.runs/"unity/result.json")
    assert [r["phase"] for r in proof["runs"]]==["replace","restart"]
    for row in proof["runs"]:assert len({p["pid"] for p in row["players"]})==2 and all(not p["visible"] for p in row["players"])
    for n,h in proof["logs"].items():assert Path(n).name==n and digest(aux/n)==h
    disk=read(aux/"world.json")
    assert disk["uuid"]=="0b731fd8-6b40-3e28-b59c-a5d6dcaf3d3d" and disk["energy"]==0 and disk["drops"]=={"minecraft:gold_block":1,"minecraft:emerald_block":1,"minecraft:copper_block":1}
    log=(aux/"replace.log").read_text("utf8",errors="replace")
    for x,wg in [(66,True),(67,True),(68,False),(69,True)]:
        assert f"U33 POLICY MINE {x}, 80, 25 {disk['uuid']} WorldGuard={str(wg).lower()} Residence=true" in log
    original=reports["unity"]["matrix"]["server"]
    drone,=[n for n in original if n.lower().startswith("logisticsdrones ")]
    names=lambda fragment:next(n for n in reports["unity"]["plugins"] if fragment in n.lower())
    table={"minecraft":"26.2","release":"0.3.3-Beta","loaderSHA256":engine,
           "policies":{n:reports["unity"]["plugins"][n] for n in (names("worldguard"),names("residence"),names("worldedit"),names("cmilib"))},
           "machines":[{"id":"quietquarry:quarry_core","jar":quarry["filename"],"sha512":quarry["sha512"],
                        "operations":["owner placement","break before drops and energy","protected targets retained","saved inventory and UUID"]},
                       {"id":"logisticsdrones:stone_drone","jar":drone,"sha512":original[drone],
                        "operations":["original AI extraction and delivery","source and target refusal","offline-owner rejection","saved native and Unity UUIDs"]}],
           "hosts":["Paper reference","Unity mixed"],"sessionsPerHost":2,"clientsPerSession":2}
    (o.runs/"machine-compatibility.json").write_text(json.dumps(table,indent=2),"utf8")
    return reports
