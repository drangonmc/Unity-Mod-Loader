"""Index Bukkit registration and game-origin coverage from the pinned SDK and a successful real run."""
from pathlib import Path
import argparse
import io
import zipfile
import csv
import hashlib
import json
import re
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[2]
VERSION="0.2.4-Beta"
EV=ROOT/"evidence"/VERSION
def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def routes_for(jar):
    with zipfile.ZipFile(jar) as outer,zipfile.ZipFile(io.BytesIO(outer.read("META-INF/unity/bukkit/unity-bukkit.jar"))) as adapter:
        route_file="mc/unity/plugins/game-events.tsv"
        if route_file in adapter.namelist():
            return dict(line.split("\t",1) for line in adapter.read(route_file).decode("utf8").splitlines())
    # The audited early preview predated the embedded manifest. Read its original shipped table.
    archive=jar.parent/"Unity-0.2.4-Development-Preview-MC26.2.zip"
    with zipfile.ZipFile(archive) as old:
        assert hashlib.sha256(old.read("versions/"+VERSION+"/"+jar.name)).hexdigest()==sha(jar)
        table=csv.DictReader(io.StringIO(old.read("evidence/"+VERSION+"/bukkit-event-coverage.tsv").decode("utf8")),delimiter="\t")
        return {r["event"].removeprefix("org.bukkit.event."):r["contract"] for r in table if r["gameOrigin"]!="not-wired"}

def origins(report,jar,fields,server_logs):
    doc=json.loads(report.read_bytes())
    assert doc[fields]==sha(jar) and len(doc["runs"])==2
    result=set()
    for name,h in doc["logs"].items():
        path=report.parent/name
        assert path.parent==report.parent and sha(path)==h
        if name in server_logs:
            result.update(re.findall(r"Unity Bukkit game event org\.bukkit\.event\.([\w.]+)",path.read_text("utf8")))
    assert server_logs<=doc["logs"].keys()
    return result

def main(options):
    jar=options.runtime
    out=options.out
    routes=routes_for(jar)
    proof=json.loads(options.mixed.read_bytes())
    assert all("QA24 ALL HANDLERLISTS 263" in r["all"] for r in proof["runs"])
    prefix=options.mixed.stem
    native=origins(options.mixed,jar,"loader",{prefix+"-first.log",prefix+"-restart.log"})
    native.update(origins(options.inventory,jar,"runtime",{"bukkit-transaction-first.log","bukkit-transaction-restart.log"}))
    if options.protection:
        native.update(origins(options.protection,jar,"runtime",{"first.log","restart.log"}))
    for extra in options.additional:
        native.update(origins(extra,jar,"runtime",{"first.log","restart.log"}))
    out.mkdir(parents=True,exist_ok=True)
    api=next((ROOT/"standalone/vendor/bukkit").glob("paper-api-*.jar"))
    with tempfile.TemporaryDirectory() as tmp:
        inventory_file=Path(tmp)/"classes.tsv"
        subprocess.run(["java",ROOT/"standalone/tests/BukkitEventInventory.java",api,inventory_file],check=True)
        entries=[line.split("\t") for line in inventory_file.read_text().splitlines()]
    assert len(entries)==263 and native<=routes.keys(),native-routes.keys()
    counts={}
    with (out/"bukkit-event-coverage.tsv").open("w",encoding="utf8",newline="") as stream:
        writer=csv.writer(stream,delimiter="\t",lineterminator="\n")
        writer.writerow(["event","handlerListOwner","cancellable","registration","gameOrigin","contract"])
        for cls,owner,cancellable in entries:
            key=cls.removeprefix("org.bukkit.event.")
            status="observed" if key in native else "wired-unobserved" if key in routes else "not-wired"
            counts[status]=counts.get(status,0)+1
            writer.writerow([cls,owner,cancellable,"verified",status,routes.get(key,"")])
    print(counts)
    (out/"bukkit-coverage.json").write_text(json.dumps({"loader":sha(jar),"paperApi":sha(api),"registration":len(entries),"gameOrigins":counts,
        "tsvSHA256":sha(out/"bukkit-event-coverage.tsv"),"runSHA256":sha(options.mixed),"transactionsSHA256":sha(options.inventory),**({"additionalSHA256":[sha(path) for path in options.additional]} if options.additional else {}),**({"protectionSHA256":sha(options.protection)} if options.protection else {})},indent=2))
if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--runtime",type=Path,default=ROOT/"versions"/VERSION/f"unity-loader-{VERSION}.jar")
    parser.add_argument("--mixed",type=Path,default=EV/"bukkit.json")
    parser.add_argument("--inventory",type=Path,default=EV/"inventory24.json")
    parser.add_argument("--additional",action="append",type=Path,default=[])
    parser.add_argument("--protection",type=Path)
    parser.add_argument("--out",type=Path,default=EV)
    main(parser.parse_args())
