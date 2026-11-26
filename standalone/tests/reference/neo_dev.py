"""Launch the upstream NeoForge tests with the official 26.2 ModDevGradle development environment."""
import argparse,json,os,shutil,sys,subprocess,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from acceptance import digest
def main(o):
    w=o.inputs/"mdk-gametest";w.mkdir(exist_ok=True)
    with zipfile.ZipFile(o.inputs/"mdk-pinned.zip") as z:
        root=z.namelist()[0]
        for n in ("gradlew.bat","gradle/wrapper/gradle-wrapper.jar","gradle/wrapper/gradle-wrapper.properties","settings.gradle"):
            dest=w/n;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(root+n))
    build=w/"build.gradle"
    build.write_text("""plugins {
    id 'java'
    id 'net.neoforged.moddev' version '2.0.148'
}
java.toolchain.languageVersion = JavaLanguageVersion.of(25)
neoForge {
    version = '26.2.0.93'
    runs {
        gameTestServer {
            type = 'gameTestServer'
            jvmArgument '-XX:ActiveProcessorCount=4'
            programArguments.addAll '--tests', 'reference_neo_tests:*', '--report', 'reference-results.xml'
        }
    }
    mods {
        reference_neo_tests { sourceSet sourceSets.main }
    }
}
dependencies { implementation 'net.neoforged:testframework:26.2.0.93' }
""","utf8")
    src=w/"src/main/java";src.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(o.inputs/"neo-upstream.zip") as z:
        for n in z.namelist():
            if n.endswith(("/debug/CustomHelperTests.java","/debug/crafting/CraftingRemainderTests.java")):
                f=src/n.split("/src/main/java/")[-1];f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(z.read(n))
    shutil.copy2(Path(__file__).with_name("ReferenceNeoTests.java"),src/"ReferenceNeoTests.java")
    mods=w/"src/main/resources/META-INF";mods.mkdir(parents=True,exist_ok=True)
    (mods/"neoforge.mods.toml").write_text('modLoader="javafml"\nloaderVersion="[1,)"\nlicense="LGPL-2.1-only"\n[[mods]]\nmodId="reference_neo_tests"\nversion="1"\ndisplayName="Upstream GameTest reference"\n')
    o.out.mkdir(parents=True,exist_ok=True)
    env={**os.environ,"GRADLE_USER_HOME":str(o.inputs/"gradle-home"),"JAVA_HOME":str(Path(shutil.which("javac")).resolve().parents[1])}
    with (o.out/"official.log").open("wb") as f:
        proc=subprocess.run(["cmd","/d","/c","gradlew.bat","--no-daemon","--max-workers=3","runGameTestServer"],cwd=w,
            env=env,stdout=f,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    if proc.returncode:raise SystemExit(proc.returncode)
    xml=next(w.rglob("reference-results.xml"));shutil.copy2(xml,o.out/"official.xml")
    import xml.etree.ElementTree as ET
    tests=[e.get("name","").lower() for e in ET.parse(xml).iter("testcase")]
    assert any("testcustomhelper" in n.replace("_","") for n in tests) and any("testlavabuckettoemptybucket" in n.replace("_","") for n in tests)
    assert not list(ET.parse(xml).iter("failure"))
    (o.out/"reference.json").write_text(json.dumps({"tests":tests,"xml":digest(xml),"log":digest(o.out/"official.log"),"build":digest(build)},indent=2))
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ("inputs","out"):p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    main(p.parse_args())
