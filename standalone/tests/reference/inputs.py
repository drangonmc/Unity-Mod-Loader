"""Fetch or verify the locked public research inputs. Worlds, accounts and installed games are untouched."""
import argparse,hashlib,json,shutil,tempfile,urllib.request
from pathlib import Path
def check(folder,names=None,fetch=False):
    lock=json.loads(Path(__file__).with_name("references.json").read_bytes())
    for name in names or lock:
        row=lock[name];target=folder/name
        def matches(p):
            if not p.is_file():return False
            with p.open("rb") as f:return hashlib.file_digest(f,"sha256").hexdigest()==row["sha256"]
        if matches(target):continue
        if not fetch:raise ValueError("Missing or changed reference "+name)
        folder.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=folder) as tmp:
            file=Path(tmp)/name
            req=urllib.request.Request(row["url"],headers={"User-Agent":"Unity-26.2-Research (https://github.com/drangonmc/Unity-Mod-Loader)"})
            with urllib.request.urlopen(req,timeout=120) as response,file.open("wb") as stream:shutil.copyfileobj(response,stream)
            if not matches(file):raise ValueError("Publisher download differs from lock: "+name)
            file.replace(target)
if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("folder",type=Path);p.add_argument("--fetch",action="store_true")
    options=p.parse_args();check(options.folder,fetch=options.fetch)
