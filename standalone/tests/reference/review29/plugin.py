import os,subprocess,zipfile,sys,tempfile
from pathlib import Path
def build(root,vault,cache,dest):
    with tempfile.TemporaryDirectory(dir=root/".work") as tmp:
        cls=Path(tmp)
        cp=[*sorted((root/"standalone/vendor/bukkit").glob("*.jar")),vault,*sorted((cache/"libraries").rglob("*.jar"))]
        subprocess.run(["javac","--release","25","-proc:none","-g:none","-cp",os.pathsep.join(map(str,cp)),"-d",cls,
                        Path(__file__).with_name("PluginRoundTrip.java")],check=True)
        with zipfile.ZipFile(dest,"w") as z:
            for f in sorted(cls.rglob("*.class")):z.writestr(zipfile.ZipInfo(f.relative_to(cls).as_posix()),f.read_bytes())
            z.writestr(zipfile.ZipInfo("plugin.yml"),'name: Unity29Probe\nmain: qa.PluginRoundTrip\nversion: 1\napi-version: "26.2"\ndepend: [LuckPerms, Vault, Essentials]\nlibraries: ["org.apache.commons:commons-text:1.12.0"]\ncommands:\n  u29: {}\npermissions:\n  unity29.confirm:\n    default: false\n  unity29.temporary:\n    default: false\n')
if __name__=="__main__":build(*(Path(x).resolve() for x in sys.argv[1:]))
