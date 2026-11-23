"""Check phase transitions and persistence against the actual candidate and per-process logs."""
import hashlib,json,re
from beta20 import require,sha256

def evidence(root,directory,jar):
    r=json.loads((directory/"candidate65.json").read_bytes())
    solo=json.loads((directory/"integrated.json").read_bytes())
    digest=sha256(jar)
    require(r["loader"]==solo["loader"]==digest,"Phase evidence binary")
    require("registry mismatch" in r["phaseMismatch"] and "originalNBT=4" in r["quarantine"],"Mismatched channel or attachment recovery")
    require("enumerations=1" in r["tanks"] and "resized=1 refreshed=1" in r["tanks"],"Filtered and resized fluid storage")
    require(set(r["configurationPhases"])=={"first","restart"},"Missing configuration round")
    for stage,sessions in r["configurationPhases"].items():
        require(set(sessions)=={"ConfigA","ConfigB"},"Two phase clients")
        for api in ("NEO","FABRIC"):
            require(len({s[api] for s in sessions.values()})==2,"Per-connection challenges")
        prefix="candidate65"+("-restart" if stage=="restart" else "")
        text=(directory/(prefix+".log")).read_text("utf8")
        for player,values in sessions.items():
            joined=re.search(r"\b"+player+r" joined the game",text)
            require(joined is not None,"Missing join "+player)
            for api,n in values.items():
                ack=re.search("QA21 "+api+" ACK "+str(n)+r" input=Netty[^\n]*player="+player+r"\b",text)
                require(ack is not None and text.index("QA21 "+api+" OFFER "+str(n))<ack.start()<joined.start(),"Task acknowledgement before play")
    require(solo["configuration"]=={"sessions":3,"wirePackets":6,"serverConfigPackets":0,"acks":{"NEO":3,"FABRIC":3}},"Integrated transport isolation")
    require(solo["phaseFixtures"]=={"qa_server_config.jar":r["probe"],"qa_forge_config.jar":r["forgeProbe"],"qa_fabric_ecology.jar":r["fabricProbe"]},"Same configuration probes on both transports")
    require("PASS configuration codecs, frame boundaries, vanilla fallback and nested/dollar generic interface reflection" in (directory/"lifecycle.log").read_text("utf8",errors="replace"),"Java 25 class signature and protocol checks")
    require("PASS config receive/disconnect ordering and stale-connection cleanup" in (directory/"lifecycle.log").read_text("utf8",errors="replace"),"Cross-thread config cleanup")
    require("PASS corrupt attachment keys, failed and throwing mod codecs, retained NBT and explicit replacement" in (directory/"lifecycle.log").read_text("utf8",errors="replace"),"Damaged attachment preservation")
    return [root/"standalone/tests/ConfigurationChecks.java",root/"standalone/BETA-0.2.1.md",root/"tools/beta21.py"]
