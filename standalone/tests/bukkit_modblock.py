"""Two rendered clients observe a Bukkit plugin protecting a third-party Forge block by namespaced state."""
import argparse,contextlib,json,os,shutil,socket,subprocess,tempfile,zipfile
from pathlib import Path
from acceptance import ROOT,VERSION,DIST,EVIDENCE,Server,digest
from content import Window,observed
from dual import exited
from gameplay import world_check,teleport,eventually
from server_configs import line

def run(eula):
    c=ROOT/'cache'/VERSION;jar=next(DIST.glob('unity-loader*.jar'));home=c/'client-home';bundle=c/'mojang-server-26.2.jar'
    mod=next((c/'performance-samples').glob('stackedblocks-*.jar'))
    lock=next(r for r in json.loads((ROOT/'standalone/performance-pack.json').read_bytes())['files'] if r['filename']==mod.name)
    assert digest(mod,'sha512')==lock['sha512'] and 'eula=true' in eula.read_text()
    with tempfile.TemporaryDirectory(dir=ROOT/'.work',prefix="mod-guard-") as tmp:
        root=Path(tmp);server=root/'server';games=[root/'A',root/'B'];out=root/'classes';out.mkdir()
        cp=[*(ROOT/'standalone/vendor/bukkit').glob('*.jar'),*(c/'libraries').rglob('*.jar')]
        subprocess.run(['javac','-proc:none','-g:none','--release','25','-cp',os.pathsep.join(map(str,cp)),'-d',out,ROOT/'standalone/tests/bukkit/ModBlockGuard.java'],check=True)
        for g in [server,*games]:
            (g/'mods').mkdir(parents=True);shutil.copy2(mod,g/'mods');(g/'unity-mods').mkdir()
            if g!=server:
                shutil.copy2(DIST/'unity-client-witness.jar',g/'unity-mods')
                (g/'options.txt').write_text('onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nrenderDistance:3\nfullscreen:false\nmaxFps:30\ntutorialStep:none\n')
        (server/'plugins').mkdir()
        with zipfile.ZipFile(server/'plugins/ModBlockGuard.jar','w') as z:
            for p in out.rglob('*.class'):z.writestr(zipfile.ZipInfo(p.relative_to(out).as_posix()),p.read_bytes())
            z.writestr(zipfile.ZipInfo('plugin.yml'),'name: ModBlockGuard\nversion: 1.0\napi-version: "26.2"\nmain: qa.bukkit.ModBlockGuard\n')
        shutil.copy2(eula,server/'eula.txt')
        with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
        (server/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nspawn-protection=0\npause-when-empty-seconds=-1\nlevel-seed=2424\n')
        block='stackedblocks:stacked_cobblestone_blocks';result={'runtime':digest(jar),'original':mod.name,'sha512':lock['sha512'],'runs':[]}
        for step in ['first','restart']:
            phase='mod-guard-'+step
            with contextlib.ExitStack() as stack:
                srv=Server(server,phase,jar,bundle,c);stack.callback(srv.close);srv.expect('Standalone ready',120)
                ws=[]
                for g in games:
                    win=Window(home,g,phase+g.name,port,jar,'Guard'+g.name);ws.append(win);stack.callback(exited,win)
                states=[win.act('status',timeout=150) for win in ws];assert all(s['gpu'] and s['remote'] and not s['visible'] for s in states)
                srv.send('forceload add 40 15 47 21');observed(srv,'loaded 44 201 18','QA_LOADED',30)
                if step=='first':
                    srv.send('fill 40 200 15 47 200 21 minecraft:stone')
                    srv.send('setblock 44 201 18 '+block,'Changed the block')
                    srv.send('item replace entity GuardA hotbar.0 with minecraft:diamond_pickaxe[minecraft:damage=3]','Replaced')
                teleport(srv,ws[0],'GuardA',44.5,201,15.8);teleport(srv,ws[1],'GuardB',44.5,201,20.7)
                world_check(srv,ws,44,block)
                if step=='first':
                    ws[0].act('qaMine',slot=0,x=44,y=201,z=18);line(EVIDENCE/(phase+'.log'),'QA24 MODDED BREAK CANCELLED '+block)
                seen=world_check(srv,ws,44,block)
                tool=eventually(lambda:ws[0].act('qaPlayer'),lambda p:p['damage']==3 and p['hand']=='minecraft:diamond_pickaxe')
                inventory=srv.send('data get entity GuardA Inventory',r'following entity data: \[')
                if 'diamond_pickaxe' not in inventory or 'damage' not in inventory:raise AssertionError(inventory)
                srv.send('save-all flush','Saved the game')
                result['runs'].append(dict(players=[x['uuid'] for x in states],states=seen,toolDamage=tool['damage'],serverBag=inventory))
        assert result['runs'][0]['players']==result['runs'][1]['players']
        result['logs']={f.name:digest(f) for f in EVIDENCE.glob('mod-guard-*.log')}
        (EVIDENCE/'mod-guard.json').write_text(json.dumps(result,indent=2),'utf8');print('PASS protected unmodified Forge block across restart')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--eula',required=True,type=Path);run(p.parse_args().eula)
