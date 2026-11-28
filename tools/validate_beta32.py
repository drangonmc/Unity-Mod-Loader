"""Validate the exact shipped engine against the mixed server, Paper control and disk proof."""
from pathlib import Path
import hashlib,json

def validate(root,o,digest):
    ref=root/"standalone/tests/reference"
    read=lambda p:json.loads(p.read_bytes())
    pins=read(ref/"review32/originals.json")+read(ref/"review31/originals.json")+read(ref/"review29/originals.json")["plugins"]
    pins+=[r for r in read(ref/"protection-originals.json") if r["slug"] in ("worldedit","worldguard")]
    plugins={r["filename"]:r["sha512"] for r in pins}
    engine=digest(o.dist/"unity-loader-0.3.2-Beta.jar");client=digest(o.dist/"unity-client-witness.jar")
    reports={};durations={}
    for host in ("unity","paper"):
        d=o.runs/host;v=read(d/"result.json");reports[host]=v
        assert v["engine"]==engine and v["clientWitness"]==client and v["host"]==host and v["plugins"]==plugins
        assert v["paper"]=="0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
        count=4 if host=="unity" else 2
        assert len(v["runs"])==len(v["stops"])==len(v["sessions"])==count and all(state==v["runs"][0] for state in v["runs"])
        assert all(s["exitCode"]==0 and 0<s["seconds"]<180 for s in v["stops"]);durations[host]=v["stops"]
        for n,h in v["logs"].items():assert n==Path(n).name and digest(d/n)==h
        for phase,clients in v["sessions"].items():
            assert len({p["pid"] for p in clients})==2 and {p["name"] for p in clients}=={"VolumeA","VolumeB"} and all(not p["visible"] for p in clients)
            log=(d/(phase+".log")).read_text("utf8",errors="replace")
            for bad in ("Views$UnsupportedCall:","failed handling ","Could not pass event","NoSuchMethodError:","NoClassDefFoundError:","Error occurred while enabling","Encountered an unexpected exception"):
                assert bad not in log,(host,phase,bad)
            if host=="unity":
                for proof in ("U32 Kotlin preLaunch","U32 Kotlin object main","U32 KFF object main","U32 KFF lifecycle","U32MACHINE Fabric and NeoForge share WorldGuard ownership checks",
                              "U32SYNC server check 3201 3202 null","[Unity] Standalone stopped"):
                    assert proof in log,(phase,proof)
                for who in ("VolumeA","VolumeB"):
                    clientlog=(d/(phase+who+"-client.log")).read_text("utf8",errors="replace")
                    for proof in ("U32 Kotlin preLaunch","U32 Kotlin object main","U32 KFF object main","U32 KFF lifecycle"):
                        assert proof in clientlog,(phase,who,proof)
        log=(d/"first.log").read_text("utf8",errors="replace")
        for who,kept,qty in (("VolumeA","true",0),("VolumeB","false",7)):
            events=[f"U32 death-low {who} {qty}",f"U32 death-high {who} {kept}",f"U32 death-monitor {who} keep={kept} emeralds=0",
                    f"U32 respawn-low {who} DEATH",f"U32 respawn-monitor {who} 62 20"]
            idx=[log.index(t) for t in events];assert idx==sorted(idx),events
            chat=(d/("first"+who+"-client.log")).read_text("utf8",errors="replace");assert "You died in the Overworld" in chat
        first=log.index("U32 open-low VolumeB false");denied=log.index("U32 open-monitor VolumeB true",first)
        assert "U32 open-allowed VolumeB" not in log[first:denied]
        for t in ("U32 open-allowed VolumeB","U32 open-monitor VolumeB false","U32 close VolumeB PLAYER","U32 close VolumeA PLAYER"):
            denied=log.index(t,denied)
        for t in ("U32_REPLACED_DROP","NO_gold_ingot","NO_emerald","U32_CHEST_13","U32_CHEST_6","U32 first-step 62 20","U32 console-removed true",
                  "U32 cow denied CUSTOM","U32 block-blast denied","U32 entity-blast filtered","U32 world-meta ok"):
            assert t in log,(host,t)
        for cause in ("CONSUMABLE_EFFECT","ENDER_PEARL","NETHER_PORTAL","END_PORTAL","END_GATEWAY","SPECTATE"):assert "U32 cause "+cause in log,(host,cause)
        state=v["runs"][0];disk=read(d/"players.json");world=read(d/"world.json")
        assert state["bags"]=={"VolumeA":{"3":"minecraft:gold_ingot*9@0"},"VolumeB":{"4":"minecraft:iron_ingot*7@0"}}
        assert state["chest"]==6 and world["chest"]=={"0":"minecraft:iron_ingot*6"} and world["state"]=="minecraft:chest"
        for who in ("VolumeA","VolumeB"):
            assert disk[who]["position"]==state["positions"][who]
            assert disk[who]["bag"]=={k:v.split("@",1)[0] for k,v in state["bags"][who].items()}
        if host=="unity":
            assert world["playerAttachment"]==3201 and world["chunkAttachment"]==3202
            for proof in ("U32ABS 3.0 0.0 3.0","U32PARRY unarmed 3","U32_MACHINE_PLACED","U32SYNC server write 3201 3202 3211","U32SYNC server remove 3201 3202 null"):
                assert proof in log,proof
    u,p=reports["unity"],reports["paper"];assert u["runs"][0]==p["runs"][0] and u["witness"]==p["witness"]
    languages=[read(ref/"review32"/name) for name in ("fabric-kotlin.json","kotlin-for-forge.json")]
    matrix={k:dict(a) for k,a in u["matrix"].items()}
    for side,files in matrix.items():
        for spec in languages:assert files.pop(spec["filename"])==spec["sha512"]
    assert hashlib.sha256(json.dumps(matrix,sort_keys=True,separators=(",",":")).encode()).hexdigest()=="8241e560bf5a4336d620f15f4486c1a627b2e2e21db888bedc9fd26f4a25f99f"
    contract=read(o.contract_result);assert contract["loader"]==engine and contract["passed"] and contract["configTypes"] and contract["menuSessions"]==2
    assert digest(o.contract_result.parent/"contracts.log")==contract["log"]
    previous=read(o.runs/"prior-regression.json");assert previous["engine"]==engine and previous["states"][0]==previous["states"][1]
    earlier=read(o.runs/"exit-regression.json")
    assert earlier["timeoutSeconds"]==180 and earlier["after"]["engine"]==engine and earlier["after"]["releasedInFullRun"] and earlier["after"]["releasedInAffectedRegression"]
    (o.runs/"shutdowns.json").write_text(json.dumps(dict(engine=engine,limitSeconds=180,runs=durations),indent=2),"utf8")
    (o.runs/"language.json").write_text(json.dumps(dict(engine=engine,originals=languages,entrypoints=["preLaunch","main","NeoForge @Mod","FMLCommonSetupEvent"]),indent=2),"utf8")
    (o.runs/"game-event-coverage.tsv").write_text("event\tunity\tpaper\n"+"".join(n+"\tobserved\tobserved\n" for n in
        ("PlayerDeathEvent","PlayerRespawnEvent","InventoryOpenEvent","InventoryCloseEvent","EntityExplodeEvent","BlockExplodeEvent","CreatureSpawnEvent")),"utf8")
    return reports
