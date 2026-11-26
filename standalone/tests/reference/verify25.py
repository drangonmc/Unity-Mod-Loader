"""Cross-check exact binaries and game logs before assigning a 0.2.5 compatibility result."""
import argparse,hashlib,io,json,re,subprocess,sys,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from acceptance import ROOT,digest

def split_snbt(text):
    """Split only top-level SNBT entries. Preserve namespaced component keys and quoted delimiters."""
    parts=[];depth=0;quote=None;escaped=False;start=0
    for i,char in enumerate(text):
        if quote:
            if escaped:escaped=False
            elif char==chr(92):escaped=True
            elif char==quote:quote=None
        elif char in ('"',"'"):quote=char
        elif char in "[{":depth+=1
        elif char in "]}":depth-=1
        elif char=="," and depth==0:parts.append(text[start:i].strip());start=i+1
    assert not depth and quote is None
    if text[start:].strip():parts.append(text[start:].strip())
    return parts

def server_bag(log,name):
    plain=re.sub(r"\x1b\[[0-9;]*m","",log)
    values=re.findall(re.escape(name)+r" has the following entity data: (\[.*\])",plain)
    assert values,name
    bag={};components={}
    for record in split_snbt(values[-1][1:-1]):
        fields=dict(x.split(": ",1) for x in split_snbt(record[1:-1]))
        index=fields["Slot"].removesuffix("b")
        patch=fields.get("components","{}")
        damage=re.search(r'"minecraft:damage": (\d+)',patch)
        bag[index]=fields["id"].strip('"')+"*"+fields["count"]+"@"+(damage[1] if damage else "0")
        components[index]=patch
    return bag,components

def verify(o):
    ev=o.evidence;v="0.2.5-Beta";jar=o.dist/("unity-loader-"+v+".jar");runtime=digest(jar)
    def read(rel):return json.loads((ev/rel).read_bytes())
    def logs(dir,report):
        for name,h in report["logs"].items():
            path=ev/dir/name
            if path.parent!=ev/dir or digest(path)!=h:raise ValueError(path)
    def trace(rows,phase,key):return [v for ph,k,v in rows if ph==phase and k==key]
    files={f.name:digest(f) for f in o.dist.glob("*.jar")}
    assert len(files)==3 and files=={f.name:digest(f) for f in o.rebuild.glob("*.jar")}
    with zipfile.ZipFile(jar) as outer:
        mf=outer.read("META-INF/MANIFEST.MF").decode().splitlines()
        assert "Main-Class: mc.unity.bootstrap.Main" in mf and "Implementation-Version: "+v in mf
        with zipfile.ZipFile(io.BytesIO(outer.read("META-INF/unity/bukkit/unity-bukkit.jar"))) as host:
            assert hashlib.sha256(host.read("META-INF/unity/worldedit-7.4.6.jar")).hexdigest()==digest(o.provider)
    authors={r["slug"]:r["sha512"] for r in json.loads(Path(__file__).with_name("protection-originals.json").read_bytes()) if r["slug"] in ("worldguard","worldedit")}
    references=[];pairs={};fixture=None
    for group in ("core","regions","region-edges"):
        ap=group+"/paper";bp=group+"/unity"
        a=read(ap+"/result.json");b=read(bp+"/result.json")
        assert a["runtime"]==digest(o.paper) and b["runtime"]==runtime
        assert a["client"]==b["client"]==runtime and a["witness"]==b["witness"]==files["unity-client-witness.jar"]
        assert a["plugin"]==b["plugin"] and (fixture is None or fixture==b["plugin"])
        fixture=b["plugin"]
        assert a["authors"]==b["authors"]==(authors if group!="core" else {})
        assert not a["mixed"] and {k:len(values) for k,values in b["mixed"].items()}=={"server":77,"VolumeA":75,"VolumeB":75}
        assert len(a["runs"])==2 and a["runs"]==b["runs"]
        assert all(b["runs"][0][k]==b["runs"][1][k] for k in ("players","health","bags","blocks"))
        for key,doc in [(ap,a),(bp,b)]:
            logs(key,doc)
            components=[]
            for i,boot in enumerate(["first","restart"]):
                text=(ev/key/(boot+".log")).read_text("utf8",errors="replace")
                readback={}
                for player in ("VolumeA","VolumeB"):
                    server,patch=server_bag(text,player)
                    assert server==doc["runs"][i]["bags"][player],(key,boot,player)
                    readback[player]=patch
                components.append(readback)
            assert components[0]==components[1],(key,"saved item components")
            if group!="core":
                for boot in ["first","restart"]:
                    log=(ev/key/(boot+".log")).read_text("utf8",errors="replace")
                    for name in ["WorldEdit","WorldGuard"]:
                        assert ("Enabled "+name+" " if key==bp else "Enabling "+name+" v") in log,key
                    if key==bp:assert "Using mc.unity.adapter.worldedit.UnityWorldEdit as the Bukkit adapter" in log
                    assert not any(k in log for k in ["failed handling ","Could not dispatch event:","Could not pass event","UnsupportedCall:"]),key
        tx=(ev/bp/"events.tsv").read_bytes();assert (ev/ap/"events.tsv").read_bytes()==tx,group
        rows=[l.split("\t",2) for l in tx.decode().splitlines()]
        assert len(trace(rows,"login","join"))==4
        if group=="core":
            for fluid in ("water","lava"):
                for action in ("pour","take"):
                    for decision in ("deny","ok"):
                        phase=f"{fluid}-{action}-dispense-{decision}"
                        assert len(trace(rows,phase,"dispense.end"))==1 and trace(rows,phase,"dispense.end")[0].endswith(str(decision=="deny").lower())
            assert trace(rows,"pot-bucket-deny","cauldron.low")==[]
            assert trace(rows,"pot-level-deny","cauldron.end")==["BUCKET_EMPTY 0 3 true"]
            assert trace(rows,"alter-level","cauldron.end")==["BUCKET_EMPTY 0 2 false"]
            assert trace(rows,"respawn-false","write")==["4"]
            assert trace(rows,"pierce-deny","hit")==["VolumeA 2 PLAYER true","VolumeA 2 COW false"]
            assert trace(rows,"pierce-ok","damage")==["VolumeA PLAYER false","VolumeA COW false"]
        elif group=="region-edges":
            assert trace(rows,"inside-pierce","damage")==["VolumeA PLAYER true"]
            assert trace(rows,"outside-pierce","damage")==["VolumeA PLAYER false"]
            assert trace(rows,"inside-slab","bucket.end")[-1].endswith("true")
            assert trace(rows,"outside-slab","bucket.end")[-1].endswith("false")
            assert trace(rows,"outside-pot","cauldron.end")[-1]=="BUCKET_EMPTY 0 3 false"
            assert trace(rows,"dispenser-boundary","dispense.end") and a["runs"][0]["dispenserDenied"]==a["runs"][1]["dispenserDenied"]
        else:
            for side,denied in [("in","true"),("out","false")]:
                for operation in ("bucket","fill"):
                    assert trace(rows,f"region-{side}-{operation}","bucket.end")[0].endswith(denied)
                assert trace(rows,f"region-{side}-arrow","damage")==["VolumeA PLAYER "+denied]
            assert trace(rows,"region-border-arrow","damage")==["VolumeA PLAYER true"]
        pairs[group]={"rows":len(rows),"trace":hashlib.sha256(tx).hexdigest(),"saved":b["runs"][1]}
        references.extend([ap+"/result.json",bp+"/result.json"])
    lifecycle=read("lifecycle/lifecycle.json")
    assert lifecycle["exit"]==0 and lifecycle["loaderSHA256"]==runtime and lifecycle["logSHA256"]==digest(ev/"lifecycle/lifecycle.log")
    references.append("lifecycle/lifecycle.json")
    for side in ["server","client"]:
        a=read("anchors/"+side+".json");assert a["unity"]==runtime and set(a["counts"])=={"ok"}
        references.append("anchors/"+side+".json")
    for tag in ["saves","ctrl-c"]:
        t=read(tag+"/saves.json");assert t["runtime"]==runtime
        assert all(x["goldBlock"] and x["other"]==14 and x["lastGood"]==7 and x["nextListener"] for x in t["results"].values())
        logs(tag,t);references.append(tag+"/saves.json")
    timing=read("timing/comparison.json");assert not timing["differences"] and timing["binarySHA256"]["unity"]==runtime
    assert len(timing["observations"]["paper"]["stronger.callbacks"])==2;logs("timing",timing);references.append("timing/comparison.json")
    mixed=ev/"mixed/mixed-reference.json";inventory=ev/"inventory/inventory24.json"
    previous=ev/"previous-protection/unity/result.json"
    old=ROOT/"evidence/protection-26.2/accepted/paper/result.json"
    historical=json.loads(old.read_bytes());repeat=json.loads(previous.read_bytes())
    assert repeat["runtime"]==runtime and repeat["runs"]==historical["runs"]
    assert (previous.parent/"events.tsv").read_bytes()==(old.parent/"events.tsv").read_bytes()
    subprocess.run([sys.executable,Path(__file__).resolve().parents[1]/"bukkit_coverage.py","--runtime",jar,"--mixed",mixed,
        "--inventory",inventory,"--protection",previous,"--additional",ev/"core/unity/result.json","--additional",ev/"regions/unity/result.json","--additional",ev/"region-edges/unity/result.json","--out",ev/"coverage"],check=True)
    coverage=read("coverage/bukkit-coverage.json")
    references+=["coverage/bukkit-coverage.json","inventory/inventory24.json","mixed/mixed-reference.json","previous-protection/unity/result.json"]
    summary={"minecraft":"26.2","version":v,"bootstrap":"mc.unity.bootstrap.Main","loader":runtime,"paper":digest(o.paper),
             "observer":fixture,"provider":digest(o.provider),"authors":authors,"rebuild":files,"pairs":pairs,"coverage":coverage["gameOrigins"],
             "receipts":{n:digest(ev/n) for n in references}}
    (ev/"verified.json").write_text(json.dumps(summary,indent=2),"utf8")
    print("PASS",runtime,{k:v["rows"] for k,v in pairs.items()},summary["coverage"])
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ("evidence","dist","rebuild","provider","paper"):p.add_argument("--"+k,type=lambda s:Path(s).resolve(),required=True)
    verify(p.parse_args())
