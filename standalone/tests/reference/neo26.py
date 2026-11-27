"""Compile the NeoForge interoperability witness against the upstream SDK, with no Unity classes on javac's path."""
import os
import subprocess
import tempfile
import zipfile
from pathlib import Path

def fixture(root, cache, sdk, output, extra_sources=()):
    cp=[sdk/n for n in ("neo.jar","neo-loader.jar","neo-bus.jar","dist.jar")]
    cp.extend([cache/"versions/26.2/server-26.2.jar",*sorted((cache/"libraries").rglob("*.jar"))])
    with tempfile.TemporaryDirectory(dir=root/".work") as tmp:
        classes=Path(tmp)
        subprocess.run(["javac","--release","25","-proc:none","-g:none","-cp",os.pathsep.join(map(str,cp)),
                        "-d",classes,Path(__file__).with_name("Neo26Probe.java"),*extra_sources],check=True)
        with zipfile.ZipFile(output,"w") as archive:
            for c in sorted(classes.rglob("*.class")):
                archive.writestr(zipfile.ZipInfo(c.relative_to(classes).as_posix()),c.read_bytes())
            archive.writestr(zipfile.ZipInfo("META-INF/neoforge.mods.toml"),
                'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="unity_neo26_probe"\nversion="1"\ndisplayName="NeoForge SDK witness"\n')
