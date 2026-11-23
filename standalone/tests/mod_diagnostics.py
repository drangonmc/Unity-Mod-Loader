"""Verify player-facing Mixin diagnostics on the original incompatible Disable Portal Checks release."""
import argparse,json,shutil,subprocess,tempfile
from pathlib import Path
from acceptance import ROOT,VERSION,DIST,EVIDENCE,clean_env,digest

ORIGINAL="ee92ad1d229081643bace5a043b9b33111daeb92bb06642e0c2a680a436961102a58f1606a82b2910461a4711c56bf7753abc1737c986a868899387d5357249e"
def run(author,cache):
    if digest(author,"sha512")!=ORIGINAL:raise ValueError("Wrong author JAR for this negative test")
    loader=DIST/f"unity-loader-{VERSION}.jar"
    with tempfile.TemporaryDirectory(prefix="mod-diagnostic-",dir=ROOT/".work") as temp:
        game=Path(temp);mods=game/"mods";mods.mkdir()
        local=Path(shutil.copy2(author,mods))
        args=["java","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-jar",loader,"--runtime-dir",cache,"--server-jar",cache/"mojang-server-26.2.jar","nogui"]
        # Request the vanilla server version, so a missed negative cannot open a listening server.
        result=subprocess.run(args+["--version"],cwd=game,capture_output=True,env=clean_env(),timeout=90,creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0))
        if not result.returncode or digest(local,"sha512")!=ORIGINAL:raise AssertionError("Expected original JAR to be rejected")
        message=(game/"logs/unity-mod-error.txt").read_text("utf8")
        for marker in ("Unity 模组加载失败","disableportalchecks.mixins.json",author.name,"net.minecraft.class_2424"):
            if marker not in message:raise AssertionError(marker)
        out=EVIDENCE/"mod-diagnostic.txt";out.write_text(message,encoding="utf8")
        (EVIDENCE/"mod-diagnostic.json").write_text(json.dumps({"version":VERSION,"loader":digest(loader),"origin":"https://modrinth.com/mod/disable-portal-checks/version/zW17oIr0","sha512":ORIGINAL,"exit":result.returncode,"report":digest(out)},indent=2))
        print("PASS Chinese Mixin diagnostic identifies original JAR, config and missing target")
if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--mod",required=True,type=Path)
    run(p.parse_args().mod.resolve(),ROOT/"cache"/VERSION)
