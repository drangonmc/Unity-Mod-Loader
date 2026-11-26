"""Repeat the existing author-corpus acceptance on an isolated candidate and write separate evidence."""
import argparse,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import acceptance,content,gameplay,server_configs,bukkit_acceptance
def run(o):
    o.out.mkdir(parents=True,exist_ok=True)
    for module in [acceptance,content,gameplay,server_configs,bukkit_acceptance]:
        module.EVIDENCE=o.out
    bukkit_acceptance.DIST=o.dist
    o.mixed=True;o.label="mixed-reference"
    bukkit_acceptance.main(o)
if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ["dist","out","samples","plugins","eula-from","fabric-loader"]:p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    run(p.parse_args())
