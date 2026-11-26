"""Collect review regressions from the same candidate and verified game logs."""
from pathlib import Path
import hashlib,json,re
from acceptance import ROOT,VERSION,DIST,EVIDENCE,digest

def main():
    jar=DIST/f"unity-loader-{VERSION}.jar";stamp=digest(jar)
    def read(name):
        return json.loads((EVIDENCE/name).read_bytes())
    unit=read("review24.json");audit=read("host-worker-audit.json");mixed=read("bukkit.json")
    assert unit["runtime"]==audit["loader"]==mixed["loader"]==stamp
    assert audit["callbacks"]==256 and list(audit["firstEmissions"].values())==[1,1]
    assert audit["failedDiagnosticSendCleanedOnce"] is True
    assert audit["log"]==digest(EVIDENCE/"host-worker-audit.log")
    assert any("host startup rollback" in s for s in unit["checks"])
    results=[]
    for phase,run in zip(["first","restart"],mixed["runs"],strict=True):
        def log(name):
            path=EVIDENCE/name
            assert digest(path)==mixed["logs"][name]
            return path.read_text("utf8")
        server=log("bukkit-"+phase+".log")
        for marker in ["QA24 TRANSITIVE CLASS IDENTITY PASS","QA24 DISTINCT LOGIN IDS, NO-REPLY TIMEOUT","QA24 FORGE GUARD DEDICATED_SERVER","QA24 NEO GUARD DEDICATED_SERVER",
                       "QA24 ZERO DELAY SAME HEARTBEAT","QA24 NEXT TICK","QA24 ASYNC REPEAT OVERLAP","QA24 FAILURE PUBLICATION RETRY PASS"]:
            assert server.count(marker)==1,(phase,marker)
        assert re.search(r"(?:SEVERE|严重): Task \d+ of UnityEventsProbe\r?\njava\.lang\.AssertionError: QA24 intentional task assertion",server)
        assert not any("QA24 "+side+" CLIENT BRANCH" in server for side in ["FORGE","NEO"])
        for side in ("A","B"):
            client=log("bukkit-"+phase+"-"+side+"-client.log")
            for marker in ["QA24 FORGE CLIENT BRANCH Minecraft","QA24 NEO CLIENT BRANCH Minecraft","QA24 FAILURE PUBLICATION RETRY PASS","[ECS-READY]"]:
                assert client.count(marker)==1,(phase,side,marker)
        elapsed=int(re.search(r"(\d+)ms$",run["silentDiagnostic"])[1])
        assert 4900<=elapsed<=12000 and len(run["neoLifecycle"])==5 and run["clientOnlyForgeSkipped"]
        assert run["elytraSwap"]=={"chest":"minecraft:iron_chestplate","hotbar5":"minecraft:elytra"}
        results.append({"phase":phase,"timeoutMs":elapsed,"neoLifecycle":run["neoLifecycle"],"equipment":run["elytraSwap"]})
    assert "客户端缺少 classicgamerule" in mixed["runs"][-1]["clientRejection"]
    data={"runtime":stamp,"bukkitReport":digest(EVIDENCE/"bukkit.json"),"sourceChecks":unit["checks"],"asyncAudit":audit["firstEmissions"],"asyncCallbacks":audit["callbacks"],"failedDiagnosticSendCleanedOnce":audit["failedDiagnosticSendCleanedOnce"],"sessions":results}
    (EVIDENCE/"review-fixes.json").write_text(json.dumps(data,indent=2),"utf8")
    print("PASS review regressions on",stamp)
if __name__=="__main__":main()
