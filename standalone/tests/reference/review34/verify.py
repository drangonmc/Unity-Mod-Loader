"""Verify a single-jar installation, mixed authors' releases, root plugins and rollback-safe upgrades."""
import argparse,contextlib,json,os,re,shutil,subprocess,sys,time,zipfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path[:0]=[str(HERE.parent),str(HERE.parent.parent),str(HERE.parent/'review30')]
import beta25 as qa,beta26,acceptance,content
from dual import exited
import run as builders

def test(o):
    root=o.out;root.mkdir();home=root/'distribution';home.mkdir()
    qa.acceptance.EVIDENCE=content.EVIDENCE=root
    o.regions=True;o.plain=True
    g,games,port,_,_=qa.setup_game(o,home,True,beta26.observer(home,o.cache))
    public=home/'plugins';(g/'plugins').rename(public);(g/'plugins').mkdir()
    fixture=public/'Unity34Check.jar';libs=sorted((o.cache/'libraries').rglob('*.jar'))
    builders.HERE=HERE
    builders.fixture('PluginCheck.java',[*sorted((qa.ROOT/'standalone/vendor/bukkit').glob('*.jar')),*libs],'plugin.yml',
        'name: Unity34Check\napi-version: "26.2"\nversion: 1\nmain: qa.unity34.PluginCheck\ncommands:\n  u34check: {}\n',fixture)
    source=o.previous/'instance';pins=json.loads((o.previous/'result.json').read_bytes())['matrix']
    selected=['BedrockCrafter-','moreberryuses-','advanced-furnace-']
    authors={}
    for folder in [g,*games]:
        mods=folder/'mods';mods.mkdir(exist_ok=True)
        for pattern in selected:
            name,=[n for n in pins[folder.name] if n.startswith(pattern)]
            src=source/folder.name/'mods'/name
            assert acceptance.digest(src,'sha512')==pins[folder.name][name]
            shutil.copy2(src,mods);authors[name]=pins[folder.name][name]
    sdk=o.sdk;mc=o.cache/'versions/26.2/server-26.2.jar'
    cases=[('ForgeWorld','forge34world','META-INF/mods.toml',[sdk/n for n in ('forge.jar','javafmllanguage.jar','fmlcore.jar','bus.jar')]),
           ('NeoWorld','neo34world','META-INF/neoforge.mods.toml',[sdk/n for n in ('neo.jar','neo-bus.jar','neo-loader.jar','dist.jar')]),
           ('FabricChunks','fab34world','fabric.mod.json',[sdk/'fabric-loader.jar',*sdk.glob('fabric-lifecycle-events-v1*.jar'),*sdk.glob('fabric-api-base-*.jar')])]
    for cls,id,desc,upstream in cases:
        metadata=(json.dumps(dict(schemaVersion=1,id=id,version='1',entrypoints={'main':['qa.unity34.'+cls]})) if desc.endswith('json') else
                  'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="'+id+'"\nversion="1"\ndisplayName="'+id+'"\n')
        f=g/'mods'/(id+'.jar');builders.fixture(cls+'.java',[*upstream,mc,*libs],desc,metadata,f)
        for client in games:shutil.copy2(f,client/'mods')
    engine=o.dist/'unity-loader-0.3.4-Beta.jar';bundle=o.cache/'mojang-server-26.2.jar'
    cmd=['java','-Xmx2g','-XX:ActiveProcessorCount=4','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',
         '-Dmc.unity.contentQA=true','-Dmc.unity.shutdownDiagnostics=true','-Dmc.unity.pluginsDir='+str(public),'-jar',str(engine),'--server-jar',str(bundle),'--runtime-dir',str(o.cache),'nogui']
    results=[];stops=[]
    for first in (True,False):
        phase='first' if first else 'upgrade'
        if not first:
            # Unchanged previous public helpers, renamed so filename-based skipping would fail.
            shutil.copy2(o.old_dist/'UnityEditRegions.jar',g/'plugins/old-region-copy.jar')
            shutil.copy2(o.old_dist/'unity-world-marker.jar',g/'unity-mods/old-marker-copy.jar')
            we,=public.glob('*worldedit*.jar');shutil.copy2(we,g/'plugins')
        server=acceptance.Server(g,phase,engine,bundle,o.cache,cmd)
        try:
            server.expect('Standalone ready',150)
            server.send('unitymark count','Unity marks saved: '+('0' if first else '1'),20)
            if first:
                qa.prepare(server)
                server.send('unitymark place 6 84 7','Unity marker placed on game tick',20)
            content.observed(server,'block 6 84 7 amethyst_block','U34_MARKER')
            with contextlib.ExitStack() as clean:
                ws=[]
                for name,game in zip(qa.NAMES,games):
                    w=content.Window(o.cache/'client-home',game,phase+name,port,engine,name)
                    clean.callback(exited,w)
                    st=w.act('status',timeout=180);assert st['remote'] and st['gpu'] and not st['visible']
                    ws.append(w)
                server.send('u34check','U34 BUKKIT PASS',15)
                data=beta26.cases(server,ws,public/'Beta25Probe/events.tsv',first)
                results.append(data)
                if first:
                    server.send("gamemode creative VolumeA","Creative")
                    server.send("execute in minecraft:the_end run forceload add 100 0")
                    server.send("execute in minecraft:the_end run setblock 100 50 0 gold_block","Changed the block",45)
                    for mode in ("cancel","redirect","forbid","allow"):
                        server.send("setblock 38 80 7 end_portal","Changed the block")
                        server.send("u34check "+mode,"U34 PORTAL MODE "+mode)
                        server.send("tp VolumeA 38.5 80.0 7.5","Teleported")
                        server.expect("U34 PORTAL "+mode,25)
                        server.send("setblock 38 80 7 air","Changed the block")
                        if mode=="redirect":content.observed(server,"entity @a[name=VolumeA,x=28,y=80,z=8,distance=..2]","U34_REDIRECT")
                        if mode in ("allow","forbid"):
                            server.send("execute in minecraft:the_end as VolumeA at @s if dimension minecraft:the_end run say U34_ENTERED","U34_ENTERED",25)
                        expected="air" if mode=="allow" else "gold_block"
                        server.send("execute in minecraft:the_end if block 100 50 0 "+expected+" run say U34_PLATFORM_"+mode,r"\[Server\] U34_PLATFORM_"+mode)
                        server.send("execute in minecraft:overworld run tp VolumeA 18.5 80.0 4.5","Teleported")
                    server.send("gamemode survival VolumeA")

            server.send('save-all flush','Saved the game',150)
        finally:
            started=time.monotonic();server.close();stops.append(round(time.monotonic()-started,3))
        log=(root/(phase+'.log')).read_text('utf8',errors='replace')
        assert log.count('Enabled built-in WorldEdit / WorldGuard region protection')==1
        for n in qa.NAMES:
            clientlog=(root/(phase+n+'-client.log')).read_text('utf8',errors='replace')
            for key in ('FORGEWORLD','NEOWORLD'):
                assert all('U34 CLIENT '+key+' '+phase in clientlog for phase in ('load','unload')),(n,key)
        for plat in ('FORGE','NEO'):
            for action in ('load','save','unload'):assert 'U34 '+plat+' '+action+' minecraft:overworld' in log,(plat,action)
        assert 'U34 FABRIC load '+('true' if first else 'false') in log
        if first:assert 'U34 FABRIC generate' in log
        assert 'U34 FABRIC unload' in log
        for bad in ('U34 check failed','failed handling ','UnsupportedCall:','Could not register flag','Exception in thread'):assert bad not in log,bad
    assert results[0]==results[1],results
    (root/'result.json').write_text(json.dumps(dict(loader=acceptance.digest(engine),authors=authors,runs=results,stopSeconds=stops,pluginLayout='root plus legacy instance',logs={x.name:acceptance.digest(x) for x in root.glob('*.log')}),indent=2),'utf8')

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for key in ('out','dist','cache','eula','sdk','authors','previous','old_dist'):p.add_argument('--'+key,required=True,type=lambda s:Path(s).resolve())
    test(p.parse_args())
