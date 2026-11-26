"""Independent 26.2 acceptance. Python is a test driver; both game JVMs run from the Unity executable."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import uuid
import zipfile
from urllib.parse import unquote, urlsplit

VERSION = "0.2.4-Beta"
ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / "versions" / VERSION
EVIDENCE = ROOT / "evidence" / VERSION
SERVER_HASH = "cdacdfb25898de5e4b4b0e5ddcc2722f77067e46605709c2d886c000ebb63ec5"
NAME = "UnityNativeQA"


def digest(file, algorithm="sha256"):
    with file.open("rb") as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def clean_env():
    env = dict(os.environ)
    for key in ("CLASSPATH", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "UNITY_INSTANCE", "UNITY_QA_TOKEN"):
        env.pop(key, None)
    return env


class Server:
    def __init__(self, game, name, jar, bundle, cache, command=None, *, private_console=False):
        self.allowed_exit_codes={0,130,0xC000013A} if private_console else {0}
        self.name = name
        self.checks = []
        self.queue = queue.Queue()
        self.log = (EVIDENCE / (name+".log")).open("w", encoding="utf8")
        args = ["java","-Xms256m","-Xmx2g","-Dmc.unity.contentQA=true","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8",
                f"-Dmc.unity.dualQA={str(name.startswith('dual-')).lower()}",f"-Xlog:class+load=info:file={name}-classes.log","-jar",str(jar),
                "--server-jar",str(bundle),"--runtime-dir",str(cache),"nogui"]
        flags=getattr(subprocess,"CREATE_NO_WINDOW",0)
        startup=None
        if private_console:
            if os.name!="nt":raise ValueError("A Windows console is required")
            flags=subprocess.CREATE_NEW_CONSOLE
            startup=subprocess.STARTUPINFO()
            startup.dwFlags|=subprocess.STARTF_USESHOWWINDOW
            startup.wShowWindow=subprocess.SW_HIDE
        self.process = subprocess.Popen(command or args,cwd=game,env=clean_env(),creationflags=flags,startupinfo=startup,stdin=subprocess.PIPE,stdout=subprocess.PIPE,
                                        stderr=subprocess.STDOUT,text=True,encoding="utf8",errors="replace",bufsize=1)
        self.thread = threading.Thread(target=self.read,daemon=True)
        self.thread.start()

    def read(self):
        try:
            for line in self.process.stdout:
                self.log.write(line)
                self.log.flush()
                self.queue.put(line.rstrip())
        finally:
            self.queue.put(None)

    def expect(self, pattern, timeout=60):
        end = time.monotonic()+timeout
        while time.monotonic()<end:
            try: line=self.queue.get(timeout=max(.01,end-time.monotonic()))
            except queue.Empty: break
            if line is None: raise RuntimeError(f"{self.name} exited waiting for {pattern}")
            if re.search(pattern,line):
                self.checks.append(line)
                return line
        raise TimeoutError(f"{self.name}: {pattern}")

    def send(self, command, pattern=None, timeout=60):
        self.process.stdin.write(command+"\n")
        self.process.stdin.flush()
        if pattern: return self.expect(pattern,timeout)

    def close(self):
        try:
            if self.process.poll() is None:
                try: self.send("stop")
                except (BrokenPipeError,OSError): pass
                try: self.process.wait(180)
                except subprocess.TimeoutExpired:
                    self.process.kill()
                    self.process.wait()
                    raise
        finally:
            self.thread.join(5)
            self.log.close()
        if self.process.returncode not in self.allowed_exit_codes: raise RuntimeError(f"{self.name}: exit {self.process.returncode}")


def class_audit(log, allowed, required, nested=None, capture_prefixes=()):
    origins={}
    selected={}
    fabric_modules = {m["filename"]:m["sha512"] for m in json.loads((ROOT/"standalone/beta-gameplay.json").read_bytes())["fabricModules"]}
    trusted_fabric = {origin for p,origin in allowed.items() if origin in fabric_modules and digest(p,"sha512")==fabric_modules[origin]}
    for line in log.read_text(encoding="utf8",errors="replace").splitlines():
        match=re.search(r"\[class,load\] (\S+) source: (.+?)\s*$",line)
        if not match: continue
        name,source=match.groups()
        if name.startswith(("org.sinytra.","cpw.mods.","net.fabricmc.loader.impl.")) or name.startswith("net.neoforged.fml.loading.") and name not in ("net.neoforged.fml.loading.FMLPaths","net.neoforged.fml.loading.FMLEnvironment"):
            raise ValueError("Unexpected platform class "+name)
        if source.startswith("file:"):
            path=unquote(urlsplit(source).path)
            if os.name=="nt" and re.match(r"^/[A-Za-z]:",path): path=path[1:]
            real=Path(path).resolve()
            if nested and real.suffix==".jar" and real.stem in nested and real.parent.name.startswith("unity-nested-") and real.parent.parent==Path(tempfile.gettempdir()).resolve():
                allowed[real]="nested:"+nested[real.stem]
            if real not in allowed: raise ValueError(f"Unapproved runtime source {name}: {real}")
            origin=allowed[real]
            if name.startswith(("net.neoforged.","net.fabricmc.","net.minecraftforge.","org.spongepowered.asm.")) and origin!="unity-loader" and not (origin in trusted_fabric and name.startswith("net.fabricmc.fabric.")):raise ValueError("Unexpected provider for "+name)
            origins[origin]=origins.get(origin,0)+1
            if name in required or name.startswith(("mc.unity.","net.minecraftforge.")+capture_prefixes): selected[name]=origin
    for name,expected in required.items():
        if selected.get(name)!=expected: raise AssertionError((name,expected,selected.get(name)))
    return {"fileOrigins":origins,"classes":selected,"traceSHA256":digest(log)}


def client_libraries(home):
    import platform
    os_name={"Windows":"windows","Linux":"linux","Darwin":"osx"}[platform.system()]
    base=json.loads((home/"versions/26.2/26.2.json").read_bytes())
    result={}
    for lib in base["libraries"]:
        allowed="rules" not in lib
        for rule in lib.get("rules",[]):
            if rule.get("os",{}).get("name",os_name)==os_name:allowed=rule["action"]=="allow"
        if allowed:result[(home/"libraries"/lib["downloads"]["artifact"]["path"]).resolve()]="Mojang library"
    return result


def actual_client(game, home, port, server, installed, witness, allowed, forge):
    game.mkdir()
    mods=game/"unity-mods"; mods.mkdir(); shutil.copy2(witness,mods/witness.name)
    (game/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:5\nsimulationDistance:5\nmaxFps:35\nfullscreen:false\ntutorialStep:none\n")
    native=game/"natives";native.mkdir()
    args=["java","-Xms384m","-Xmx2304m","--enable-native-access=ALL-UNNAMED",
          "-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8",f"-Djava.library.path={native}",
          f"-Dmc.unity.clientHome={home}","-Dmc.unity.standaloneQA=true","-Dmc.unity.hiddenQA=true","-Xlog:class+load=info:file=client-classes.log",
          "-cp",str(installed),"mc.unity.bootstrap.ClientEntry","--username",NAME,
          "--version","26.2","--versionType","release","--gameDir",str(game),
          "--assetsDir",str(home/"assets"),"--assetIndex","32","--uuid",uuid.uuid5(uuid.NAMESPACE_DNS,NAME).hex,
          "--accessToken","0","--clientId","0","--xuid","0","--width","960","--height","540",
          "--quickPlayPath",str(game/"quickplay.json"),"--quickPlayMultiplayer",f"127.0.0.1:{port}"]
    with (EVIDENCE/"graphical-client.log").open("wb") as log:
        process=subprocess.Popen(args,cwd=game,env={**clean_env(),"ALSOFT_DRIVERS":"null"},creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0),stdout=log,stderr=subprocess.STDOUT)
        data=game/"unity-data/client-witness"
        try:
            server.expect(NAME+r".*joined the game",160)
            server.send(f"gamemode creative {NAME}")
            server.send("fill 0 199 0 8 199 9 minecraft:stone")
            server.send(f"tp {NAME} 3.5 200 6.2 facing 3.5 200.5 3.5","Teleported")
            until=time.monotonic()+70
            while time.monotonic()<until:
                shots=[p for p in (game/"screenshots").glob("*.png") if p.stat().st_size>1000 and p.read_bytes().endswith(b"IEND\xaeB`\x82")]
                if shots and (data/"seen.properties").is_file(): break
                if process.poll() is not None:raise RuntimeError("Client exited before observing mod block")
                time.sleep(.2)
            else:raise TimeoutError("Client world witness")
            props=dict(line.split("=",1) for line in (data/"seen.properties").read_text("latin1").splitlines() if line and not line.startswith("#"))
            if props["player"]!=NAME or props["block"]!="minecraft\\:amethyst_block" or props["gameLoader"]!="Unity Minecraft 26.2" or props["modLoader"]!="unity-native":
                raise AssertionError(props)
            shutil.copy2(shots[0],EVIDENCE/"standalone-world.png")
            props["block"]=props["block"].replace("\\:",":")
            result={"witness":props,"screenshotSHA256":digest(EVIDENCE/"standalone-world.png")}
            if forge:
                server.send(f"clear {NAME}")
                server.send(f"execute unless data entity {NAME} Inventory[0] run say UNITY_EMPTY",r"\[Server\] UNITY_EMPTY")
                server.send(f"execute as {NAME} run getRandomItem 1 1")
                line=server.send(f"data get entity {NAME} Inventory[0]",r'entity data: .*id: "[a-z0-9_.-]+:')
                if not re.search(r"count: 1\b",line):raise AssertionError(line)
                result["randomItem"]=re.search(r'id: "([a-z0-9_./:-]+)"',line)[1]
                server.send(f"item replace entity {NAME} weapon.mainhand with minecraft:iron_pickaxe[minecraft:damage=77]")
                for damage in (77,0):
                    if damage==0:server.send(f"execute as {NAME} run repair")
                    server.send(f"execute if items entity {NAME} weapon.mainhand minecraft:iron_pickaxe[minecraft:damage={damage}] run say UNITY_DAMAGE_{damage}",
                                rf"\[Server\] UNITY_DAMAGE_{damage}")
                result["pickaxeDamage"]=[77,0]
        finally:
            data.mkdir(parents=True,exist_ok=True);(data/"quit").touch()
            try: process.wait(40)
            except subprocess.TimeoutExpired:process.kill();process.wait();raise
    if process.returncode:raise RuntimeError(f"Graphical client exit {process.returncode}")
    allowed[installed.resolve()]="unity-loader";allowed[(mods/witness.name).resolve()]="client-witness"
    result["classAudit"]=class_audit(game/"client-classes.log",allowed,{"mc.unity.bootstrap.ClientEntry":"unity-loader",
          "mc.unity.example.ClientWitness":"client-witness","net.minecraft.client.main.Main":"Mojang client","net.minecraft.client.Minecraft":"Mojang client"})
    result["exit"]=process.returncode
    return result


def verify(options):
    if digest(options.bundle)!=SERVER_HASH:raise ValueError("Wrong original Mojang server bundle")
    eula=options.eula_from.read_text()
    if not re.search(r"(?im)^eula\s*=\s*true\s*$",eula):raise ValueError("Accepted local QA instance required")
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    work=ROOT/".work";work.mkdir(exist_ok=True)
    result={"version":VERSION,"minecraft":"26.2","mojangBundleSHA256":SERVER_HASH,"runs":{}}
    with tempfile.TemporaryDirectory(prefix="Unity clean ",dir=work) as temp:
        clean=Path(temp);game=clean/"game";game.mkdir()
        bundle=clean/"original-server.jar";shutil.copy2(options.bundle,bundle)
        jar=clean/"unity.jar";shutil.copy2(DIST/f"unity-loader-{VERSION}.jar",jar)
        empty=clean/"runtime"
        with socket.socket() as s:s.bind(("127.0.0.1",0));port=s.getsockname()[1]
        (game/"eula.txt").write_text(eula)
        (game/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nuse-native-transport=false\nspawn-protection=0\npause-when-empty-seconds=-1\nview-distance=4\nsimulation-distance=3\nlevel-seed=2606001\n")
        result["loaderSHA256"]=digest(jar)
        for phase in ("bare","native","restart"):
            if phase=="native":
                marker=Path(shutil.copy2(DIST/"unity-world-marker.jar",game/"unity-mods"))
                result["worldMarkerSHA256"]=digest(marker)
                if options.old_probe:shutil.copy2(options.old_probe,game/"unity-mods/previous-native.jar")
            if phase=="restart" and options.forge_samples:
                specs=json.loads((ROOT/"standalone/forge-samples.json").read_bytes())
                result["unmodifiedForge"]={}
                for spec in specs:
                    dest=game/"mods"/spec["filename"];shutil.copy2(options.forge_samples/spec["filename"],dest)
                    if digest(dest,"sha512")!=spec["sha512"]:raise ValueError(dest)
                    result["unmodifiedForge"][dest.name]=spec["sha512"]
            server=Server(game,phase,jar,bundle,empty)
            try:
                server.expect(r"\[Unity\] Standalone ready",160)
                server.send("unity",r"standalone \| MC 26\.2 \| native \[")
                if phase=="bare":
                    assert "native []" in server.checks[-1]
                else:
                    assert "world-marker" in server.checks[-1]
                    server.send("forceload add 0 0")
                    for _ in range(40):
                        server.send("execute if loaded 3 200 3 run say UNITY_CHUNK_READY")
                        line=server.send("execute unless loaded 3 200 3 run say UNITY_CHUNK_WAIT",r"\[Server\] UNITY_CHUNK_(READY|WAIT)")
                        if line.endswith("READY"):break
                        time.sleep(.25)
                    else:raise TimeoutError("Force-loaded chunk did not become available")
                    if phase=="native":
                        server.send("unitymark count",r"Unity marks saved: 0$")
                        server.send("unitymark place 3 200 3","Unity marker placed on game tick at 3, 200, 3; total 1")
                    server.send("execute if block 3 200 3 minecraft:amethyst_block run say UNITY_WORLD_OK",r"\[Server\] UNITY_WORLD_OK")
                    server.send("unitymark count",r"Unity marks saved: 1$")
                    server.send("data get storage unity_example:marks count",r"following contents: 1$|following .*: 1$")
                    if options.old_probe:server.send("unityprobe","Unity native probe executed")
                    if phase=="restart":
                        server.send("reload","Reloading!")
                        server.expect("Loaded .* advancements",80)
                        server.send("unitymark count",r"Unity marks saved: 1$")
                        if options.client_home:
                            home=options.client_home.resolve();installed=home/f"libraries/mc/unity/unity-loader/{VERSION}/unity-loader-{VERSION}.jar"
                            subprocess.run(["java","-jar",str(jar),"--install-client",str(home)],env=clean_env(),check=True)
                            allowed=client_libraries(home)
                            allowed[(home/"versions/26.2/26.2.jar").resolve()]="Mojang client"
                            result["client"]=actual_client(clean/"client",home,port,server,installed,DIST/"unity-client-witness.jar",allowed,options.forge_samples)
                            result["clientWitnessSHA256"]=digest(clean/"client/unity-mods/unity-client-witness.jar")
                server.send("save-all flush","Saved the game",150)
            finally: server.close()
            allowed={p.resolve():"Mojang library" for p in (empty/"libraries").rglob("*.jar")}
            allowed[(empty/"versions/26.2/server-26.2.jar").resolve()]="Mojang server"
            allowed[jar.resolve()]="unity-loader"
            for p in (game/"unity-mods").glob("*.jar"):allowed[p.resolve()]=p.name
            for p in (game/"mods").glob("*.jar"):allowed[p.resolve()]=p.name
            required={"mc.unity.bootstrap.Main":"unity-loader","net.minecraft.server.Main":"Mojang server",
                      "net.minecraft.server.MinecraftServer":"Mojang server","mc.unity.runtime.ServerHooks":"unity-loader"}
            if phase!="bare":required["mc.unity.example.WorldMarker"]="unity-world-marker.jar"
            if phase=="restart" and options.forge_samples:
                for n,spec in zip(("net.micux21.repair.RepairCommandsForgeOfficialNewBus","com.example.randomitem.RandomItemMod"),specs):
                    required[n]=spec["filename"]
                    if digest(game/"mods"/spec["filename"],"sha512")!=spec["sha512"]:raise ValueError("Changed original "+spec["filename"])
            result["runs"][phase]={"exit":server.process.returncode,"checks":server.checks,"classAudit":class_audit(game/(phase+"-classes.log"),allowed,required)}
            print("PASS",phase,flush=True)
        if digest(bundle)!=SERVER_HASH:raise AssertionError("Original server changed")
        if options.old_probe:
            assert digest(game/"unity-mods/previous-native.jar")==digest(options.old_probe)
            lifecycle=(game/"unity-data/unity-probe/lifecycle.txt").read_text().splitlines()
            if lifecycle!=["initialize","started","command","stopping"]*2:raise AssertionError(lifecycle)
            result["previousNativeProbe"]={"sha256":digest(options.old_probe),"events":lifecycle}
    result["logSHA256"]={p.name:digest(p) for p in [*(EVIDENCE/(name+".log") for name in result["runs"]),
                         *([EVIDENCE/"graphical-client.log"] if "client" in result else [])]}
    temporary=EVIDENCE/"acceptance.tmp";temporary.write_text(json.dumps(result,indent=2),encoding="utf8")
    temporary.replace(EVIDENCE/"acceptance.json")


if __name__=="__main__":
    args=argparse.ArgumentParser()
    args.add_argument("--bundle",required=True,type=Path);args.add_argument("--eula-from",required=True,type=Path)
    args.add_argument("--client-home",type=Path);args.add_argument("--old-probe",type=Path);args.add_argument("--forge-samples",type=Path)
    verify(args.parse_args())
