"""Check the independent SDK, exact-member trust, and the 0.2.4 preflight changes without starting a world."""
import argparse,hashlib,json,os,shutil,subprocess,tempfile,zipfile
from pathlib import Path
from acceptance import ROOT,VERSION,DIST,EVIDENCE,digest,clean_env

def run():
    cache=ROOT/'cache'/VERSION;jar=next(DIST.glob('unity-loader*.jar'))
    original=next((ROOT/'standalone/vendor').glob('fabric-api-*.jar'))
    cp=[jar,cache/'versions/26.2/server-26.2.jar',*sorted((cache/'libraries').rglob('*.jar'))]
    proof={"runtime":digest(jar)}
    result=subprocess.run(['java','-Dstdout.encoding=UTF-8','-cp',os.pathsep.join(map(str,cp)),ROOT/'standalone/tests/Review24Checks.java'],capture_output=True,check=True)
    text=result.stdout.decode('utf8');assert "PASS Forge and NeoForge" in text and "PASS bounded diagnostic" in text
    proof["checks"]=text.splitlines()
    with tempfile.TemporaryDirectory(prefix='identity24-',dir=ROOT/'.work') as tmp:
        game=Path(tmp);mods=game/'mods';mods.mkdir()
        with zipfile.ZipFile(original) as source,zipfile.ZipFile(mods/'altered-fabric.jar','w') as out:
            for n in source.namelist():out.writestr(n,source.read(n))
            out.writestr("unexpected-resource.txt","independent negative fixture")
        cmd=['java','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-jar',jar,'--server-jar',cache/'mojang-server-26.2.jar','--runtime-dir',cache,'--version']
        result=subprocess.run(cmd,cwd=game,env=clean_env(),capture_output=True,timeout=90)
        text=(game/'logs/unity-mod-error.txt').read_text('utf8')
        assert result.returncode and 'Unexpected or duplicate Fabric API member unexpected-resource.txt' in text
        proof["alteredUmbrellaRejected"]=True
        (mods/'altered-fabric.jar').unlink()
        with zipfile.ZipFile(mods/'bad-owner.jar','w') as z:
            z.writestr('fabric.mod.json',json.dumps(dict(schemaVersion=1,id='first',version='1')))
            z.writestr('META-INF/mods.toml','modLoader="javafml"\n[[mods]]\nmodId="second"\nversion="1"\n')
        result=subprocess.run(cmd,cwd=game,env=clean_env(),capture_output=True,timeout=90)
        text=(game/'logs/unity-mod-error.txt').read_text('utf8')
        assert result.returncode and 'conflicting mod IDs or versions' in text and 'first' in text and 'second' in text
        proof["conflictingDescriptorsRejected"]=True
    out=EVIDENCE/'review24.json';out.write_text(json.dumps(proof,indent=2),'utf8');print("PASS",out)
if __name__=="__main__":run()
