"""Gate the 26.2 release on the author hashes, game process logs, and save readback."""
import json
from pathlib import Path
import re
from validate_beta29 import validate as mixed_baseline

def validate(root,options,digest):
    reports=mixed_baseline(root,options,digest)
    assert reports["unity"]["pdcWire"]==reports["paper"]["pdcWire"]
    assert len(set(reports["unity"]["pdcWire"].values()))==1
    for clients in reports["unity"]["clientSessions"].values():
        assert len({row["pid"] for row in clients})==2
        assert {row["player"] for row in clients}=={"VolumeA","VolumeB"}
        assert all(row["gpu"] and row["remote"] and not row["visible"] for row in clients)
    test=root/"standalone/tests/reference/review30"
    pins=json.loads((test/"originals.json").read_bytes())
    assert {p["project"] for p in pins}=={"no-berry-damage","vinescutter","villager-bonus-drops"}
    unity=reports["unity"]
    assert set(unity["authors"])=={"server","VolumeA","VolumeB"}
    for side,archives in unity["authors"].items():
        assert len(archives)>=(85 if side=="server" else 83)
        assert all(archives.get(p["filename"])==p["sha512"] for p in pins)
    review=json.loads(options.contract_result.read_bytes())
    assert review["menuSessions"]==2 and digest(options.contract_result.parent/"contracts.log")==review["log"]
    first=(options.runs/"unity/first.log").read_text("utf8",errors="replace")
    observed=re.findall(r"UNITY30 ([^\r\n]+)",first)
    def ordered(*values):
        positions=[observed.index(v) for v in values]
        assert positions==sorted(positions),values
    ordered("break-high-46 false","break-low-46 true","bukkit-break-46 true")
    assert not any(o.startswith("break-normal-46 ") for o in observed)
    ordered("break-high-47 false","break-normal-47 false","break-low-47 false","bukkit-break-47 false")
    ordered("place-old-49 true","place-low-49 true","bukkit-place-49 true")
    ordered("place-old-50 true","place-low-50 false","bukkit-place-50 false")
    ordered("interact-deny true","attack-deny true","incoming-sweetBerryBush true","bukkit-damage-deny true","pre 4.0","post 2.0","death-deny true")
    assert any(re.fullmatch(r"drops-cancel [1-9]\d*",v) for v in observed)
    assert any(re.fullmatch(r"drops-replace [1-9]\d*",v) for v in observed)
    for marker in ("U30_ALIVE","U30_NO_DROPS","U30_DROP_COUNT","U30_ONLY_EMERALDS","U30_AUTHOR_FURNACE","UNITY30 restored-and-pdc"):
        assert marker in first,marker
    for phase in unity["runs"]:
        events=phase["neoEvents"]
        assert events["savedCow"]==[4,4] and events["emptyDrops"]==[{},{}]
        assert events["emeraldDrops"]==[{"minecraft:emerald":5}]*2 and events["armorerFurnaces"]==[1,1]
        assert events["healthB"]==[15]*3 and events["healthA"]==[17]*3
        assert events["bags"]["VolumeA"]["1"].startswith("minecraft:bricks*5")
        for x,b in {"46":"stone","47":"air","49":"air","50":"bricks","52":"lever"}.items():
            blocks=events["blocks"][x]
            assert len(blocks)==2 and all("minecraft:"+b in v for v in blocks)
    disk=json.loads((options.runs/"unity/offline.json").read_bytes())
    assert disk["blocks"]=={k:"minecraft:"+v for k,v in {"46":"stone","47":"air","49":"air","50":"bricks","52":"lever"}.items()}
    for name,player in disk["players"].items():
        assert player["health"]==events["health"+name[-1]][0]
        assert player["bag"]=={i:value.split("@",1)[0] for i,value in events["bags"][name].items()}
    return reports
