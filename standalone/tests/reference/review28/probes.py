import os,subprocess,tempfile,zipfile
from pathlib import Path
SRC=Path(__file__).parent

def build(o,root):
    cache=o.cache
    sdk=o.sdk
    common=[cache/"versions/26.2/server-26.2.jar",*sorted((cache/"libraries").rglob("*.jar"))]
    built={}
    for key,files,libs,descriptor,data in (
        ("unity28registry",["RegistryProbe","OptionalWidget"],["neo.jar","neo-loader.jar","neo-bus.jar","dist.jar"],"META-INF/neoforge.mods.toml",None),
        ("unity28forge",["ForgeBreak"],["forge.jar","bus.jar","fmlcore.jar","fmlloader.jar","javafmllanguage.jar"],"META-INF/mods.toml",None),
        ("Unity28Bukkit",["BukkitProbe"],[],"plugin.yml",'name: Unity28Bukkit\nversion: 1\napi-version: "26.2"\nmain: qa.review28.BukkitProbe\n')):
        classes=root/(key+"-classes");classes.mkdir()
        cp=[*(sdk/n for n in libs),*common] if libs else [*(o.project/"standalone/vendor/bukkit").glob("*.jar"),*common]
        subprocess.run(["javac","-proc:none","--release","25","-encoding","UTF-8","-g:none","-cp",os.pathsep.join(map(str,cp)),"-d",classes,
            *(SRC/(n+".java") for n in files)],check=True)
        path=root/(key+".jar")
        with zipfile.ZipFile(path,"w") as z:
            for f in sorted((classes/"qa").rglob("*.class")): z.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix()),f.read_bytes())
            z.writestr(zipfile.ZipInfo(descriptor),data or ('modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="'+key+'"\nversion="1"\n'))
        built[key]=path
    classes=root/"native-classes";classes.mkdir()
    cp=[o.dist/"unity-loader-0.3.0-Beta.jar",*common]
    subprocess.run(["javac","-proc:none","--release","25","-g:none","-cp",os.pathsep.join(map(str,cp)),
                    "-d",classes,SRC/"DropProbe.java"],check=True)
    path=root/"unity28drop.jar"
    with zipfile.ZipFile(path,"w") as archive:
        for f in sorted(classes.rglob("*.class")):
            archive.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix()),f.read_bytes())
        archive.writestr(zipfile.ZipInfo("META-INF/unity.properties"),"api=1\nid=unity28drop\nentrypoint=qa.review28.DropProbe\n")
    built["unity28drop"]=path
    return built
