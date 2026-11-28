"""Exercise the real Residence-only denial with the previous accepted drone world, without logging in the owner."""
import argparse,json,os,shutil,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path[:0]=[str(HERE.parent.parent),str(HERE.parent/'review33')]
import acceptance,content,machines
def verify(o):
    o.out.mkdir();g=o.out/'instance';g.mkdir();src=o.previous/'instance/server'
    report=json.loads((o.previous/'result.json').read_bytes())
    for d in ('mods','world','plugins','config','defaultconfigs','unity-mods'):
        if (src/d).is_dir():shutil.copytree(src/d,g/d,ignore=shutil.ignore_patterns('UnityEditRegions.jar'))
    shutil.copy2(o.eula,g/'eula.txt')
    prop=(src/'server.properties').read_text('utf8')
    import re
    (g/'server.properties').write_text(re.sub(r'(?m)^server-port=\d+$','server-port=0',prop),'utf8')
    for name,h in report['plugins'].items():assert acceptance.digest(g/'plugins'/name,'sha512')==h
    for name,h in report['matrix']['server'].items():assert acceptance.digest(g/'mods'/name,'sha512')==h
    acceptance.EVIDENCE=o.out;engine=o.dist/'unity-loader-0.3.4-Beta.jar';bundle=o.cache/'mojang-server-26.2.jar'
    os.environ['PAPER_DEFAULT_CENTRAL_REPOSITORY']='https://repo.maven.apache.org/maven2/'
    cmd=['java','-Xmx2g','-XX:ActiveProcessorCount=4','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Dmc.unity.contentQA=true','-Dmc.unity.machineQA=true','-jar',str(engine),'--server-jar',str(bundle),'--runtime-dir',str(o.cache),'nogui']
    s=acceptance.Server(g,'residence',engine,bundle,o.cache,cmd)
    try:
        s.expect('Standalone ready',150)
        s.send('execute unless entity @a run say U34_OWNER_OFFLINE',r'\[Server\] U34_OWNER_OFFLINE',20)
        s.send('item replace block 67 80 35 container.0 with emerald 6','Replaced',30)
        s.send('u33drone VolumeA route 67 69','U33 ROUTE')
        s.expect('U33 POLICY EXTRACT 67, 80, 35 d72a93df-334e-34e6-81f8-67ddcdeb22db WorldGuard=true Residence=false',45)
        s.send('u33drone VolumeA off','U33 DRONE OFF')
        machines.cargo(s,'VolumeA',0);machines.chest(s,67,'emerald',6);machines.chest(s,69,'emerald',12)
        s.send('save-all flush','Saved the game',150)
    finally:s.close()
    log=(o.out/'residence.log').read_text('utf8',errors='replace')
    assert 'failed handling ' not in log and 'region query failed' not in log and 'UnsupportedCall:' not in log
    (o.out/'result.json').write_text(json.dumps(dict(engine=acceptance.digest(engine),offline='VolumeA',worldGuard=True,residence=False,source=6,destination=12,cargo=0,mods=report['matrix']['server'],plugins=report['plugins'],log=acceptance.digest(o.out/'residence.log')),indent=2),'utf8')
if __name__=='__main__':
    p=argparse.ArgumentParser()
    for k in ('previous','out','cache','dist','eula'):p.add_argument('--'+k,type=lambda s:Path(s).resolve(),required=True)
    verify(p.parse_args())
