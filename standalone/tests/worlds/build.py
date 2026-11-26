"""Build the two authored QA mods against published Forge/Fabric APIs and the 26.2 client."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,tempfile,zipfile
ROOT=Path(__file__).resolve().parents[3]
def build(refs,fabric,cache,out,home=None):
    home=home or cache/"client-home"
    out.mkdir(parents=True,exist_ok=True)
    sdk=json.loads((ROOT/"standalone/tests/interop/references.json").read_bytes())
    api=[]
    for n in ("forge","bus","fmlloader","fmlcore","javafmllanguage","fabric-loader","neo","neo-bus","neo-loader"):
        p=refs/(n+".jar");assert hashlib.sha256(p.read_bytes()).hexdigest()==sdk[n]["sha256"];api.append(p)
    dist=refs/"dist.jar"
    assert hashlib.sha256(dist.read_bytes()).hexdigest()==json.loads(Path(__file__).with_name("dist-reference.json").read_bytes())["sha256"]
    api.append(dist)
    for name in ("fabric-api-base","fabric-rendering-v1","fabric-model-loading-api-v1","fabric-events-interaction-v0","fabric-registry-sync-v0","fabric-lifecycle-events-v1"):
        f=next(fabric.glob(name+"-*.jar"))
        lock=json.loads(Path(__file__).with_name("fabric-references.json").read_bytes())
        if hashlib.sha256(f.read_bytes()).hexdigest()!=lock["modules"][f.name]:raise ValueError(f)
        api.append(f)
    client=home/"versions/26.2/26.2.jar"
    lib=ROOT/"standalone/lib"
    client_api,=(lib/row.split("  ",1)[1] for row in (lib/"SHA256SUMS").read_text().splitlines() if "  unity-client-api-" in row)
    cp=api+[client_api,client,*sorted((cache/"libraries").rglob("*.jar")),*sorted(p for p in (home/"libraries").rglob("*.jar") if "/mc/unity/" not in p.as_posix())]
    with tempfile.TemporaryDirectory(dir=out) as local:
        tmp=Path(local);classes=tmp/"classes";classes.mkdir()
        subprocess.run(["javac","--release","25","-g:none","-proc:none","-encoding","UTF-8","-cp",os.pathsep.join(map(str,cp)),"-d",classes,*sorted(Path(__file__).parent.glob("*.java")),ROOT/"src/main/java/mc/unity/api/UnityMod.java",ROOT/"standalone/src/main/java/mc/unity/api/TickingMod.java"],check=True)
        content={};fabricmod={};neomod={};native={"META-INF/unity.properties":"api=1\nenvironment=both\nid=worldprobe-native\nentrypoint=qa.worlds.nativecheck.NativeWorld\n"}
        for f in classes.rglob("*.class"):
            if f.relative_to(classes).as_posix().startswith("mc/unity/"):continue
            (native if f.name.startswith("NativeWorld") else content if f.name.startswith("ContentMod") else neomod if f.name.startswith("NeoMod") else fabricmod)[f.relative_to(classes).as_posix()]=f.read_bytes()
        content["META-INF/mods.toml"]='modLoader="javafml"\nloaderVersion="[65,)"\nlicense="MIT"\n[[mods]]\nmodId="worldprobe"\nversion="1.0"\n'
        fabricmod["fabric.mod.json"]=json.dumps({"schemaVersion":1,"id":"worldprobe_fabric","version":"1.0","environment":"*","entrypoints":{"main":["qa.worlds.FabricMod"],"client":["qa.worlds.Visuals"]},"depends":{"worldprobe":"*","fabric-registry-sync-v0":"*","fabric-events-interaction-v0":"*","fabric-lifecycle-events-v1":"*","fabric-rendering-v1":"*","fabric-model-loading-api-v1":"*"}})
        for key,tex in (("glass_panel","blue_stained_glass"),("grate","iron_bars"),("bell_stand","polished_andesite")):
            content[f"assets/worldprobe/blockstates/{key}.json"]=json.dumps({"variants":{"":{"model":"worldprobe:block/"+key}}})
            content[f"assets/worldprobe/models/block/{key}.json"]=json.dumps({"parent":"minecraft:block/cube_all","textures":{"all":{"sprite":"minecraft:block/"+tex,"force_translucent":key!="bell_stand"}}})
            content[f"assets/worldprobe/items/{key}.json"]=json.dumps({"model":{"type":"minecraft:model","model":"worldprobe:block/"+key}})
        # A low slab and an open frame leave the block entity renderer's bell mesh visible.
        def cuboid(start,end,texture):
            return {"from":start,"to":end,"faces":{face:{"texture":texture} for face in ("north","south","west","east","up","down")}}
        content["assets/worldprobe/models/block/bell_stand.json"]=json.dumps({
            "textures":{"base":"minecraft:block/polished_andesite","beam":"minecraft:block/stripped_oak_log","particle":"#base"},
            "elements":[cuboid([0,0,0],[16,2,16],"#base"),cuboid([1,2,7],[3,14,9],"#beam"),
                        cuboid([13,2,7],[15,14,9],"#beam"),cuboid([1,14,6],[15,16,10],"#beam")]})
        content["assets/worldprobe/sounds.json"]=json.dumps({"chime":{"sounds":[{"name":"minecraft:block.note_block.bell","type":"event"}]}})
        content["data/worldprobe/recipe/convert.json"]=json.dumps({"type":"worldprobe:convert","score":31})
        for reg,value in (("tuning",41),("server_rules",9)):content[f"data/worldprobe/worldprobe/{reg}/value.json"]=str(value)
        neomod["META-INF/neoforge.mods.toml"]='modLoader="javafml"\nloaderVersion="*"\nlicense="MIT"\n[[mods]]\nmodId="worldprobe_neo"\nversion="1.0"\n'
        neomod["data/worldprobe_neo/worldprobe_neo/labels/sample.json"]='"sapphire"'
        (out/"native").mkdir(exist_ok=True)
        for name,rows in (("native/worldprobe-native",native),("worldprobe",content),("worldprobe_fabric",fabricmod),("worldprobe_neo",neomod)):
            with zipfile.ZipFile(out/(name+".jar"),"w") as z:
                for path,data in sorted(rows.items()):z.writestr(zipfile.ZipInfo(path),data,compress_type=zipfile.ZIP_DEFLATED)
if __name__=="__main__":build(*(Path(x).resolve() for x in sys.argv[1:]))
