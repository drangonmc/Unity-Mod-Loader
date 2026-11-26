"""Repeat the existing two-client inventory tests without changing the published receipts."""
import argparse,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import acceptance,content,gameplay,server_configs,bukkit_transactions

if __name__=="__main__":
    p=argparse.ArgumentParser()
    for k in ["dist","out","samples","plugins","authors","eula"]:p.add_argument("--"+k,required=True,type=lambda s:Path(s).resolve())
    options=p.parse_args()
    options.out.mkdir(exist_ok=True,parents=True)
    for module in (acceptance,content,gameplay,server_configs,bukkit_transactions):module.EVIDENCE=options.out
    bukkit_transactions.run(options)
