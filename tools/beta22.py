"""Verify V0.2.2 fixes against the same executable and raw author archive as the gameplay run."""
import json
from beta20 import require,sha256
def evidence(root, directory, jar):
    proof=json.loads((directory/"candidate65.json").read_bytes())
    sample=root/"standalone/universal-data-sample.json"
    original=json.loads(sample.read_bytes())
    require(proof["loader"]==sha256(jar) and proof["samples"][original["filename"]]==original["sha512"],"Original universal data archive")
    require(proof["dataArchive"]==original["filename"] and "recipe=craftable_trident:trident result=minecraft:trident count=1" in proof["archiveRecipe"],"Authored 26.2 recipe overlay")
    require("java=25 resource=java25" in proof["multiForge"] and "escaped=true multiline=true" in proof["multiNeo"],"Multi-mod owners and runtime JAR view")
    require(len(proof["clientFallback"])==2 and all("vanillaBreak=true vanillaHarvest=1" in l for l in proof["clientFallback"]),"Real LocalPlayer fallback")
    trace=(directory/"candidate65.log").read_text("utf8")
    require(trace.count("QA22 CONFIG END ConfigA")==1 and trace.index("QA22 CONFIG END ConfigA")<trace.index("ConfigA joined the game"),"Abort and reconnect ordering")
    require(trace.count("Login capabilities play=256 configuration=true extended=true")==3,"All 256 options during abort and both joins")
    require((directory/"candidate65-restart.log").read_text("utf8").count("Login capabilities play=256 configuration=true extended=true")==2,"All 256 on restart")
    log=(directory/"lifecycle.log").read_text("utf8",errors="replace")
    for marker in ["atomic configuration close versus handshake","watcher replacement, normalized paths","bounded Play and configuration encoders",
                   "MRV solver resolved 24 binary choices in 26 states","archive close exception preservation","identity-based removal"]:
        require("PASS "+marker in log,"Missing regression "+marker)
    return [root/".clang-format",root/"tools/beta22.py",sample,root/"standalone/tests/ReviewChecks.java",root/"standalone/BETA-0.2.2.md"]
