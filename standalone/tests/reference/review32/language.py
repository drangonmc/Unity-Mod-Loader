import json,os,subprocess,zipfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
def compile_probe(compiler,fabric,loader,work,output):
    with zipfile.ZipFile(fabric) as jar:
        name=next(n for n in jar.namelist() if "/kotlin-stdlib-2" in n)
        sdk=work/Path(name).name;sdk.write_bytes(jar.read(name))
    subprocess.run(["java","-cp",str(compiler/"*"),"org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-no-reflect","-no-stdlib","-jvm-target","21","-classpath",os.pathsep.join(map(str,[sdk,loader])),"-d",output,HERE/"KotlinProbe.kt"],check=True)
    with zipfile.ZipFile(output,"a") as jar:
        jar.writestr("fabric.mod.json",json.dumps({"schemaVersion":1,"id":"unity32kotlin","version":"1",
            "depends":{"fabric-language-kotlin":"*"},"entrypoints":{k:[{"adapter":"kotlin","value":"qa.unity32.kotlin."+n}]
            for k,n in [("preLaunch","Early"),("main","Main")]}}))

def compile_kff(compiler,archive,sdk,work,output):
    cp=[sdk/n for n in ("neo.jar","neo-bus.jar","neo-loader.jar","dist.jar")]
    with zipfile.ZipFile(archive) as kff:
        for entry in kff.namelist():
            if entry.startswith("META-INF/jarjar/") and entry.endswith(".jar"):
                path=work/Path(entry).name;path.write_bytes(kff.read(entry));cp.append(path)
    subprocess.run(["java","-cp",str(compiler/"*"),"org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-no-reflect","-no-stdlib","-jvm-target","21","-classpath",os.pathsep.join(map(str,cp)),"-d",output,HERE/"KffProbe.kt"],check=True)
    with zipfile.ZipFile(output,"a") as jar:
        jar.writestr("META-INF/neoforge.mods.toml",'modLoader="kotlinforforge"\nloaderVersion="[6,7)"\nlicense="MIT"\n[[mods]]\nmodId="unity32kff"\nversion="1"\n')
