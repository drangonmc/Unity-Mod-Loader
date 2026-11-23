"""Benchmark the same 26.2 server save under vanilla, bare Unity and an original mod set."""
import argparse
import contextlib
import csv
import ctypes
import hashlib
import json
import math
import os
import platform
import re
import shutil
import socket
import subprocess
import tempfile
import time
from pathlib import Path
from acceptance import ROOT, VERSION, DIST, EVIDENCE, Server, SERVER_HASH, clean_env, digest
from content import Window
from dual import exited
from gameplay import teleport
from concurrent.futures import ThreadPoolExecutor

def host_info():
    import winreg
    with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,r"HARDWARE\DESCRIPTION\System\CentralProcessor\0") as key:
        model,_=winreg.QueryValueEx(key,"ProcessorNameString")
    return {"system":platform.platform(),"cpu":model.strip(),"logicalProcessors":os.cpu_count(),
            "serverHeap":["-Xms1g","-Xmx2g"],"viewDistance":4,"simulationDistance":4,"clientMaxFps":30}

def working_set(pid):
    from ctypes import wintypes as wt
    class Counters(ctypes.Structure):
        _fields_=[("cb",wt.DWORD),("faults",wt.DWORD)]+[(k,ctypes.c_size_t) for k in ("peak","rss","a","b","c","d","e","f")]
    kernel=ctypes.WinDLL("kernel32",use_last_error=True)
    kernel.OpenProcess.argtypes=[wt.DWORD,wt.BOOL,wt.DWORD];kernel.OpenProcess.restype=wt.HANDLE
    kernel.CloseHandle.argtypes=[wt.HANDLE]
    api=ctypes.WinDLL("psapi",use_last_error=True).GetProcessMemoryInfo
    api.argtypes=[wt.HANDLE,ctypes.POINTER(Counters),wt.DWORD]
    handle=kernel.OpenProcess(0x410,False,pid)
    if not handle:raise ctypes.WinError(ctypes.get_last_error())
    try:
        c=Counters();c.cb=ctypes.sizeof(c)
        if not api(handle,ctypes.byref(c),c.cb):raise ctypes.WinError(ctypes.get_last_error())
        return c.rss
    finally:kernel.CloseHandle(handle)

def run(o):
    if not re.fullmatch(r"[a-z0-9-]+",o.tag):raise ValueError(o.tag)
    cache=ROOT/"cache"/VERSION;home=cache/"client-home";bundle=cache/"mojang-server-26.2.jar"
    jar=DIST/f"unity-loader-{VERSION}.jar";env=clean_env()
    assert digest(bundle)==SERVER_HASH
    eula=o.eula_from.read_text()
    if not re.search(r"(?im)^eula\s*=\s*true\s*$",eula):raise ValueError("Local test EULA required")
    java_version=subprocess.check_output(["java","-version"],stderr=subprocess.STDOUT).decode(errors="replace").splitlines()
    EVIDENCE.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="perf-",dir=ROOT/".work") as tmp:
        base=Path(tmp);classes=base/"observer";classes.mkdir()
        subprocess.run(["javac","--release","25","-d",classes,Path(__file__).with_name("TickSamples.java")],check=True)
        subprocess.run(["java","-jar",jar,"--install-client",home],check=True)
        profile=home/"libraries/mc/unity/unity-loader"/VERSION/jar.name
        assert digest(profile)==digest(jar)
        vanilla=cache/"versions/26.2/server-26.2.jar"
        # Use the extracted distribution's classpath and avoid measuring one-time vanilla bundle extraction.
        cp=[vanilla,*sorted((cache/"libraries").rglob("*.jar"))]
        game=["-cp",os.pathsep.join(map(str,cp)),"net.minecraft.server.Main"]
        def start(path,label,branch):
            args=["java","-Xms1g","-Xmx2g","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8"]
            args+=game if branch=="vanilla" else ["-jar",str(jar),"--server-jar",str(bundle),"--runtime-dir",str(cache)]
            return Server(path,label,jar,bundle,cache,args+["nogui"])
        def setup(path):
            path.mkdir();(path/"eula.txt").write_text(eula)
            with socket.socket() as s:s.bind(("127.0.0.1",0));port=s.getsockname()[1]
            (path/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nenable-jmx-monitoring=true\nview-distance=4\nsimulation-distance=4\nlevel-seed=262020\nspawn-protection=0\npause-when-empty-seconds=-1\n")
            return port
        seed=base/"seed";setup(seed)
        with contextlib.closing(start(seed,o.tag+"-world","vanilla")) as server:
            server.expect(r'Done \(',180)
            server.send("forceload add 0 0 15 15")
            server.send("execute in minecraft:overworld run setblock 8 200 8 minecraft:stone")
            server.send("save-all flush","Saved the game",90)
        template=digest(seed/"world/level.dat")
        originals=sorted(o.samples.glob("*.jar"))
        excluded={x["filename"] for x in json.loads((ROOT/"standalone/forge-samples.json").read_bytes())}
        for branch in o.groups:
            label=o.tag+"-"+branch;instance=base/branch;port=setup(instance)
            shutil.copytree(seed/"world",instance/"world")
            assert digest(instance/"world/level.dat")==template
            mods=instance/"mods";mods.mkdir()
            if branch=="mixed":
                for mod in originals:shutil.copy2(mod,mods)
            report={"version":VERSION,"server":branch,"java":java_version,"host":host_info(),"loader":None if branch=="vanilla" else digest(jar),"seedLevelDat":template,"serverBundle":digest(bundle),"sampleJars":{p.name:digest(p,"sha512") for p in mods.iterdir()},"workload":("two clients walking in parallel in repeated 50-tick trials on a measured stone lane" if o.walk else "two connected clients standing on the same prepared platform"),"windows":o.windows}
            with contextlib.ExitStack() as stack:
                before=time.perf_counter();server=start(instance,label,branch);stack.callback(server.close)
                server.expect(r'Done \(',180);report["startupSeconds"]=time.perf_counter()-before
                windows=[]
                for name in ("PerfA","PerfB"):
                    path=base/(label+name);path.mkdir()
                    (path/"unity-mods").mkdir();shutil.copy2(DIST/"unity-client-witness.jar",path/"unity-mods")
                    (path/"mods").mkdir()
                    for p in mods.iterdir():
                        if p.name not in excluded:shutil.copy2(p,path/"mods")
                    (path/"options.txt").write_text("onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:4\nmaxFps:30\nfullscreen:false\ntutorialStep:none\n")
                    w=Window(home,path,label+name,port,profile,name);stack.callback(exited,w);windows.append(w)
                report["clients"]=[w.act("status",timeout=180) for w in windows]
                if not all(s["remote"] and s["gpu"] and not s["visible"] for s in report["clients"]):raise AssertionError(report)
                server.send("fill 4 200 4 12 200 35 minecraft:stone")
                server.send("fill 4 201 4 12 203 35 minecraft:air")
                server.send("tp PerfA 6.5 201 8.5");server.send("tp PerfB 10.5 201 8.5")
                server.send("list",r"There are 2 of a max")
                time.sleep(15)
                raw=EVIDENCE/(label+"-ticks.csv")
                with subprocess.Popen(["java","-cp",str(classes),"TickSamples",str(server.process.pid),str(raw),str(o.windows)],env=env) as observer:
                    rss=[]
                    walks=[]
                    with ThreadPoolExecutor(max_workers=2) as pool:
                        while observer.poll() is None:
                            rss.append(working_set(server.process.pid))
                            if o.walk:
                                for w,name,x in zip(windows,("PerfA","PerfB"),(6.5,10.5)):teleport(server,w,name,x,201,8.5)
                                pair=list(pool.map(lambda w:w.act("qaWalk"),windows))
                                if any(t["distance"]<5 for t in pair):raise AssertionError(pair)
                                walks.append([t["distance"] for t in pair])
                            else:time.sleep(1)
                    if o.walk:report["walkDistances"]=walks
                    if observer.returncode:raise RuntimeError("Mojang JMX sampling failed")
                server.send("list",r"There are 2 of a max")
                with raw.open() as f:rows=list(csv.DictReader(f))
                ms=sorted(int(r["tickNanoseconds"])/1e6 for r in rows)
                assert len(ms)==100*o.windows
                report.update(rawSHA256=digest(raw),samples=len(ms),peakWorkingSetBytes=max(rss),heapUsedBytesByWindow=[int(r["heapBytes"]) for r in rows[::100]],mspt={k:ms[math.ceil(p*len(ms))-1] for k,p in [("p50",.5),("p95",.95),("p99",.99),("max",1)]})
            (EVIDENCE/(label+".json")).write_text(json.dumps(report,indent=2))
            print(branch,report["startupSeconds"],report["mspt"],flush=True)
if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--tag",default="perf");p.add_argument("--walk",action="store_true");p.add_argument("--eula-from",type=Path,required=True);p.add_argument("--samples",type=Path,required=True);p.add_argument("--windows",type=int,default=12);p.add_argument("--groups",nargs="+",choices=["vanilla","bare","mixed"],default=["vanilla","bare","mixed"])
    run(p.parse_args())
