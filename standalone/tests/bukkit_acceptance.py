"""Run the independent 26.2 server with original multiplatform mods, Bukkit plugins and two real clients."""
import argparse,contextlib,hashlib,json,os,re,shutil,socket,subprocess,sys,tempfile,time,zipfile
from pathlib import Path
from acceptance import ROOT,VERSION,DIST,EVIDENCE,Server,digest
from content import Window,observed,slot
from dual import exited
from gameplay import eventually,teleport,world_check
from server_configs import line

def asset(path):
    toml='modLoader="lowcodefml"\nlicense="MIT"\n[[mods]]\nmodId="qa24_assets"\nversion="${file.jarVersion}"\n'
    rows={'META-INF/MANIFEST.MF':'Manifest-Version: 1.0\r\nImplementation-Version: 1.2\r\n\r\n','fabric.mod.json':json.dumps(dict(schemaVersion=1,id='qa24_assets',version='1.2')),
          'META-INF/mods.toml':toml,'META-INF/neoforge.mods.toml':toml,'pack.mcmeta':json.dumps({'pack':{'description':'QA','min_format':88,'max_format':88},'overlays':{'entries':[{'min_format':88,'max_format':88,'directory':'client26'}]}}),
          'assets/qa24_assets/lang/en_us.json':'{"qa24.assets.only":"wrong layer"}','client26/assets/qa24_assets/lang/en_us.json':'{"qa24.assets.only":"pure client overlay"}'}
    with zipfile.ZipFile(path,'w') as z:
        for k,v in rows.items():z.writestr(zipfile.ZipInfo(k),v)
def main(o):
    c=ROOT/'cache'/VERSION;home=c/'client-home';bundle=c/'mojang-server-26.2.jar';runtime=next(DIST.glob('unity-loader*.jar'))
    records=json.loads((ROOT/'standalone/universal-samples.json').read_bytes())+[json.loads((ROOT/'standalone/forge-key-sample.json').read_bytes())]; plugins=json.loads((ROOT/'tools/plugins.lock.json').read_bytes())
    label=o.label
    if not re.fullmatch('[a-z0-9-]+',label):raise ValueError(label)
    with tempfile.TemporaryDirectory(prefix='bukkit24-',dir=ROOT/'.work') as temp:
        work=Path(temp);server=work/'server';clients=[work/'A',work/'B'];probe=work/'fixtures'
        subprocess.run([sys.executable,ROOT/'standalone/tests/bukkit/build_probes.py',probe,o.fabric_loader],check=True)
        original=next((ROOT/'standalone/vendor').glob('fabric-api-*.jar'))
        repacked=work/'repacked-api.jar'
        with zipfile.ZipFile(original) as source,zipfile.ZipFile(repacked,'w',compression=zipfile.ZIP_STORED) as out:
            for n in reversed(source.namelist()):out.writestr(n,source.read(n))
        assert digest(repacked)!=digest(original) and repacked.stat().st_size!=original.stat().st_size
        assetJar=work/'pure-assets.jar';asset(assetJar)
        mixed={}
        for g in [server,*clients]:
            mods=g/'mods';mods.mkdir(parents=True);native=g/'unity-mods';native.mkdir()
            shutil.copyfile(repacked,mods/'fabric-api.jar');shutil.copyfile(probe/'entry-probe.jar',mods/'entry-probe.jar')
            shutil.copyfile(assetJar,mods/assetJar.name)
            for guard in ['forge-guard.jar','neo-guard.jar']:shutil.copy2(probe/guard,mods)
            if g==server:shutil.copy2(probe/'login-review.jar',native)
            if o.mixed:
                baseline=json.loads((ROOT/'standalone/performance-pack.json').read_bytes())['files']
                cache=c/('performance-samples' if g==server else 'performance-client-samples')
                expected={r['filename']:r['sha512'] for r in baseline}
                used={}
                for src in sorted(cache.glob('*.jar')):
                    h=digest(Path(shutil.copy2(src,mods)),'sha512')
                    if expected.get(src.name)!=h:raise ValueError(src.name)
                    used[src.name]=h
                if len(used)!=(73 if g==server else 71):raise AssertionError(len(used))
                mixed[g.name]=used
            for r in records:
                f=Path(shutil.copy2(o.samples/r['filename'],mods));assert digest(f,'sha512')==r['sha512']
            name='unity-world-marker.jar' if g==server else 'unity-client-witness.jar';shutil.copy2(DIST/name,native)
            if g!=server:(g/'options.txt').write_text('onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:3\nmaxFps:30\nfullscreen:false\ntutorialStep:none\n')
        folder=server/'plugins';folder.mkdir()
        for r in plugins:
            f=Path(shutil.copy2(o.plugins/r['filename'],folder));assert digest(f,'sha512')==r['sha512']
        (folder/'KeepChunks').mkdir()
        (folder/'KeepChunks/config.yml').write_text("general:\n  pluginbanner: false\n  colourfulconsole: false\n  releaseallprotection: true\nupdates:\n  check: false\n  notify: false\n","utf8")
        (folder/'bStats').mkdir();(folder/'bStats/config.yml').write_text("enabled: false\n")
        for fixture in ['UnityEventsProbe.jar','UnityTypes.jar','UnityFacade.jar']:shutil.copy2(probe/fixture,folder)
        assert re.search(r'(?im)^eula\s*=\s*true\s*$',o.eula_from.read_text());shutil.copy2(o.eula_from,server/'eula.txt')
        with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
        (server/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\npause-when-empty-seconds=-1\nlevel-seed=262024\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\n')
        result={'baseline':mixed,'loader':digest(runtime),'apiOriginal':digest(original),'apiRepacked':digest(repacked),'authors':{r['filename']:r['sha512'] for r in records+plugins},'runs':[]}
        for iteration in ['first','restart']:
            phase=label+'-'+iteration;report={};log=EVIDENCE/(phase+'.log')
            with contextlib.ExitStack() as stack:
                srv=Server(server,phase,runtime,bundle,c);stack.callback(srv.close);srv.expect('Standalone ready',130)
                report['all']=line(log,'QA24 ALL HANDLERLISTS 263');report['entry']=line(log,'QA24 REENTRANT ENTRY + COMPARATOR PASS');line(log,'QA24 ASYNC OWNER PASS');line(log,'QA24 ASYNC TO SYNC TASK')
                discovered=line(log,"Discovered native mods")
                assert 'elytra_chestplate_swapper' not in discovered
                report['clientOnlyForgeSkipped']=True
                report['guardedInitializers']=[line(log,'QA24 '+platform+' GUARD DEDICATED_SERVER') for platform in ('FORGE','NEO')]
                for marker in ('TRANSITIVE CLASS IDENTITY PASS','ZERO DELAY SAME HEARTBEAT','NEXT TICK','ASYNC REPEAT OVERLAP','intentional task assertion'):
                    line(log,'QA24 '+marker)
                report['silentDiagnostic']=line(log,'QA24 DISTINCT LOGIN IDS, NO-REPLY TIMEOUT')
                packs=srv.send('datapack list enabled',r'data pack');assert 'unity/qa24_assets' not in packs
                windows=[];names=['UnityEventA','UnityEventB']
                for g,n in zip(clients,names):
                    win=Window(home,g,phase+'-'+g.name,port,runtime,n);stack.callback(exited,win);windows.append(win)
                report['players']=[win.act('status',timeout=180) for win in windows]
                for g in clients:line(EVIDENCE/(phase+'-'+g.name+'-client.log'),'QA24 PURE ASSET OVERLAY')
                for g in clients:
                    client_log=EVIDENCE/(phase+'-'+g.name+'-client.log')
                    line(client_log,'[ECS-READY]')
                    line(client_log,'QA24 FAILURE PUBLICATION RETRY PASS')
                    for platform in ('FORGE','NEO'):
                        line(client_log,'QA24 '+platform+' GUARD CLIENT');line(client_log,'QA24 '+platform+' CLIENT BRANCH Minecraft')
                for n in names:line(log,'QA24 JOIN '+n)
                report['uptime']=srv.send('uptime',r'(?i)server uptime')
                windows[0].act("qaCommand",cmd="ping")
                ping=line(EVIDENCE/(phase+'-A-client.log'),'Ping:')
                if not re.search(r"Ping:\s*\d+",re.sub("§.","",ping)):raise AssertionError(ping)
                report["ping"]=ping
                if iteration=='first':srv.send("kc keepchunk coords 6 8 world")
                report["keptChunk"]=srv.send("forceload query 96 128",r"marked for force loading")
                if "not marked" in report["keptChunk"]:raise AssertionError(report["keptChunk"])
                srv.send('forceload add 40 15 48 21')
                observed(srv,'loaded 44 200 18','QA24_LOADED')
                if iteration=='first':
                    srv.send('gamerule doDaylightCycle false','false')
                    srv.send('fill 40 200 14 50 200 22 minecraft:stone')
                    srv.send('fill 40 201 14 50 204 22 minecraft:air')
                    srv.send('setblock 44 201 18 minecraft:stone');srv.send('setblock 45 201 18 minecraft:stone')
                    srv.send('gamemode creative UnityEventA','Creative Mode')
                    eventually(lambda:windows[0].act('qaPlayer'),lambda p:p['mayfly'])
                    windows[0].act('qaFlight');line(log,'QA24 FLIGHT DENY')
                    eventually(lambda:windows[0].act('qaPlayer'),lambda p:p['mayfly'] and not p['flying'])
                    observed(srv,'entity @a[name=UnityEventA,nbt={abilities:{flying:0b}}]','QA24_FLIGHT_AUTHORITATIVE')
                    srv.send('gamemode survival @a')
                    srv.send('gamemode adventure UnityEventA')
                    line(log,'QA24 MODE DENY')
                    observed(srv,"entity @a[name=UnityEventA,gamemode=survival]","QA24_MODE_SURVIVAL")
                    for i,n in enumerate(names):srv.send(f'item replace entity {n} hotbar.8 with minecraft:lapis_lazuli {6+3*i}')
                    srv.send('item replace entity UnityEventA hotbar.0 with minecraft:diamond_pickaxe')
                    srv.send('item replace entity UnityEventA hotbar.1 with minecraft:oak_planks 3')
                    srv.send('item replace entity UnityEventA hotbar.2 with minecraft:oak_door 2')
                    teleport(srv,windows[1],names[1],45.5,201,20.5)
                    for x in [44,45,46]:
                        teleport(srv,windows[0],names[0],x+.5,201,15.8)
                        if x!=46:windows[0].act('qaMine',slot=0,x=x,y=201,z=18)
                        else:windows[0].act('qaRight',slot=1,x=x,y=200,z=18,top=True)
                        report[str(x)]=world_check(srv,windows,x,'minecraft:stone' if x==44 else 'minecraft:air')
                    line(log,'QA24 BREAK DENY');line(log,'QA24 BREAK ALLOW');line(log,'QA24 PLACE DENY')
                    slot(windows[0],1,'minecraft:oak_planks',3)
                    srv.send("setblock 47 201 18 minecraft:lever[face=floor,powered=false]")
                    teleport(srv,windows[0],names[0],47.5,201,15.8)
                    windows[0].act("qaRight",slot=8,x=47,y=201,z=18)
                    line(log,"QA24 INTERACT DENY RIGHT_CLICK_BLOCK")
                    world_check(srv,windows,47,"minecraft:lever",property="powered=false")
                    teleport(srv,windows[0],names[0],48.5,201,15.8)
                    windows[0].act('qaRight',slot=2,x=48,y=200,z=18,top=True)
                    line(log,'QA24 MULTIPLACE DENY 2')
                    world_check(srv,windows,48,'minecraft:air');world_check(srv,windows,48,'minecraft:air',y=202)
                    slot(windows[0],2,'minecraft:oak_door',2)
                    windows[0].act("qaSelect",slot=6);line(log,'QA24 HELD DENY')
                    eventually(lambda:windows[0].act('qaPlayer'),lambda p:p['selected']==2)
                    srv.send('execute if entity @a[name=UnityEventA,nbt={SelectedItemSlot:2}] run say QA24_HELD_AUTH',r'\[Server\] QA24_HELD_AUTH')
                    srv.send('summon armor_stand 48.5 201 18.5 {Tags:["qa24_stand"],NoGravity:1b,ShowArms:1b}',"Summoned")
                    time.sleep(.45)
                    windows[0].act("qaStandUse",slot=2);line(log,'QA24 ENTITY DENY')
                    slot(windows[0],2,"minecraft:oak_door",2)
                    srv.send('item replace entity UnityEventA armor.chest with minecraft:elytra','Replaced')
                    srv.send('item replace entity UnityEventA hotbar.5 with minecraft:iron_chestplate','Replaced')
                    slot(windows[0],5,'minecraft:iron_chestplate',1)
                    eventually(lambda:windows[0].act('qaPlayer'),lambda state:state['chest']=='minecraft:elytra')
                    windows[0].act('qaKeyPress',name='key.ecs.swap')
                    srv.send('experience set UnityEventA 9 levels',r'Set 9 experience levels');srv.send('experience set UnityEventB 2 levels',r'Set 2 experience levels')
                    eventually(lambda:windows[0].act('qaPlayer'),lambda info:info['xpLevel']==9)
                    eventually(lambda:windows[1].act('qaPlayer'),lambda info:info['xpLevel']==2)
                    windows[0].act('qaCommand',cmd='exptrade give UnityEventB levels 3')
                    line(EVIDENCE/(phase+"-B-client.log"),"wants to give you")
                    windows[1].act('qaCommand',cmd='exptrade accept UnityEventA')
                    line(EVIDENCE/(phase+"-B-client.log"),"You received")
                for i,n in enumerate(names):
                    slot(windows[i],8,'minecraft:lapis_lazuli',6+3*i)
                    observed(srv,f'entity @a[name={n},level=6]','QA24_LEVEL_'+n,20)
                    eventually(lambda:windows[i].act("qaPlayer"),lambda info:info["xpLevel"]==6 and info["xpPoints"]==7*i)
                    observed_xp=srv.send("data get entity "+n+" XpP",r"following entity data: [0-9.]+f")
                    fraction=float(re.search(r"entity data: ([0-9.]+)f",observed_xp)[1])
                    local=windows[i].act("qaPlayer")
                    if abs(fraction-local["xpProgress"])>1e-6 or round(fraction*19)!=7*i:raise AssertionError((fraction,local))
                    report['xp'+n]={'level':6,'progress':fraction,'pointsRoundedLikeAuthor':round(fraction*19),'rawTotal':72+round(fraction*19)}
                observed(srv,'items entity UnityEventA armor.chest minecraft:iron_chestplate','QA24_FORGE_EQUIPPED')
                slot(windows[0],5,'minecraft:elytra',1)
                for screen in windows:
                    eventually(lambda:screen.act('qaPlayer',name='UnityEventA'),lambda state:state['chest']=='minecraft:iron_chestplate')
                report['elytraSwap']={'chest':'minecraft:iron_chestplate','hotbar5':'minecraft:elytra'}
                report['levels']=[6,6]
                if iteration=="first":
                    srv.send("fill 50 199 18 53 203 18 minecraft:obsidian")
                    srv.send("fill 51 200 18 52 202 18 minecraft:nether_portal[axis=x]")
                    srv.send("gamerule minecraft:disable_nether true","true")
                    srv.send('summon pig 51.5 200 18.5 {Tags:["qa24_portal"],NoAI:1b,NoGravity:1b,PersistenceRequired:1b}',"Summoned")
                    clock=lambda:int(re.search(r"time is (\d+)",srv.send("time query gametime",r"time is \d+"))[1])
                    start=clock(); eventually(clock,lambda ticks:ticks>=start+35)
                    srv.send("execute in minecraft:overworld if entity @e[tag=qa24_portal,distance=0..] run say QA24_PORTAL_BLOCKED",r"\[Server\] QA24_PORTAL_BLOCKED")
                    srv.send("gamerule minecraft:disable_nether false","false")
                    end=time.monotonic()+30
                    while time.monotonic()<end:
                        srv.send("execute in minecraft:the_nether if entity @e[tag=qa24_portal,distance=0..] run say QA24_PORTAL_ARRIVED")
                        status=srv.send("execute in minecraft:the_nether unless entity @e[tag=qa24_portal,distance=0..] run say WAIT_PORTAL",r"\[Server\] (?:QA24_PORTAL_ARRIVED|WAIT_PORTAL)")
                        if "QA24_PORTAL_ARRIVED" in status:break
                        time.sleep(.2)
                    else:raise AssertionError("Control pig did not travel")
                    report["netherPortal"]="blocked, then allowed for same pig"
                    srv.send("forceload add 1248 1248")
                    observed(srv,"loaded 1248 201 1248","QA24_FAR_CHUNK",30)
                    srv.send("setblock 1248 201 1248 minecraft:glass",r"Changed the block")
                    srv.send("save-all flush",r"Saved the game")
                    srv.send("setblock 1248 201 1248 minecraft:emerald_block",r"Changed the block")
                    srv.send("forceload remove 1248 1248")
                    srv.expect("QA24 CHUNK NO SAVE",45)
                    srv.send("forceload add 1248 1248")
                    observed(srv,"block 1248 201 1248 minecraft:glass","QA24_SKIP_UNLOAD_SAVE",30)
                    report["chunk"]="plugin suppressed unload write; previous glass restored"
                report['rule']=srv.send('gamerule minecraft:advance_time',r'false')
                report['protected']=world_check(srv,windows,44,'minecraft:stone')
                for win in windows:exited(win)
                for n in names:line(log,'QA24 QUIT '+n)
                srv.send('save-all flush',r'Saved the game',180);line(log,'QA24 WORLD SAVE')
                if iteration=='restart':
                    missing=clients[0]/'mods'/next(r['filename'] for r in records if r['slug']=='classic-gamerule')
                    missing.unlink()
                    negative=Window(home,clients[0],phase+'-negative',port,runtime,'MissingExample')
                    try:
                        report['rejection']=srv.expect(r'Disconnecting MissingExample .*客户端缺少 classicgamerule',100)
                        report['clientRejection']=line(EVIDENCE/(phase+'-negative-client.log'),'QA24 DISCONNECT SCREEN');assert '客户端缺少 classicgamerule' in report['clientRejection'],report['clientRejection']
                    finally:exited(negative)
                result['runs'].append(report)
            for n in names:line(log,'QA24 QUIT '+n)
            text=log.read_text('utf8',errors='replace')
            expected_order=['ServerAboutToStartEvent','ServerStartingEvent','ServerStartedEvent','ServerStoppingEvent','ServerStoppedEvent']
            phases=re.findall(r'QA24 NEO LIFECYCLE (\w+)',text)
            if phases!=expected_order:raise AssertionError(phases)
            report['neoLifecycle']=phases
            if 'does not implement Scheduler' in text or 'failed handling ' in text or 'Exception in thread "Unity event probe"' in text:raise AssertionError('Plugin listener failed')
        result['logs']={f.name:digest(f) for phase in ('first','restart') for f in EVIDENCE.glob(label+'-'+phase+'*.log')}
        dest=EVIDENCE/(label+'.json');dest.write_text(json.dumps(result,indent=2),'utf8');print('PASS',dest)
if __name__=='__main__':
    p=argparse.ArgumentParser()
    for k in ['samples','plugins','fabric-loader','eula-from']:p.add_argument('--'+k,type=Path,required=True)
    p.add_argument('--mixed',action='store_true');p.add_argument('--label',default='bukkit');main(p.parse_args())
