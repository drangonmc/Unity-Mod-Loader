from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile,zipfile
ROOT=Path(__file__).resolve().parents[3]
def build(folder,loader):
    folder.mkdir(exist_ok=True,parents=True)
    sources=Path(__file__).parent
    paper=ROOT/'standalone/vendor/bukkit'; game=ROOT/'cache/0.2.4-Beta'
    lock=json.loads((ROOT/'standalone/tests/interop/references.json').read_bytes())['fabric-loader']
    if hashlib.sha256(loader.read_bytes()).hexdigest()!=lock['sha256']:raise ValueError('Fabric Loader SDK differs')
    with tempfile.TemporaryDirectory(dir=folder) as tmp:
        work=Path(tmp);classes=work/'classes'; classes.mkdir()
        vendor=next((ROOT/'standalone/vendor').glob('fabric-api-*.jar'))
        expected=json.loads((ROOT/'standalone/beta-gameplay.json').read_bytes())['fabricApi']['sha512']
        if hashlib.sha512(vendor.read_bytes()).hexdigest()!=expected:raise ValueError('Fabric API differs')
        api=[]
        with zipfile.ZipFile(vendor) as z:
            for name in ('fabric-lifecycle-events-v1','fabric-api-base'):
                member=next(n for n in z.namelist() if n.startswith('META-INF/jars/'+name+'-') and n.endswith('.jar'))
                f=work/(name+'.jar');f.write_bytes(z.read(member));api.append(f)
        cp=[loader,*api,game/'client-home/versions/26.2/26.2.jar',*list(paper.glob('*.jar')),*list((game/'libraries').rglob('*.jar'))]
        subprocess.run(['javac','-proc:none','--release','25','-encoding','UTF-8','-d',classes,'-cp',os.pathsep.join(map(str,cp)),sources/'UnityEventsProbe.java',sources/'PluginScopeChecks.java',*sorted((sources/'deps').glob('*.java')),sources/'ReentrantFabric.java',sources/'ResourceProbe.java'],check=True)
        tsv=work/'events.tsv'
        subprocess.run(['java',sources.parent/'BukkitEventInventory.java',next(paper.glob('paper-api-*.jar')),tsv],check=True)
        for target,prefix in [('UnityEventsProbe.jar','bukkit'),('entry-probe.jar','entry')]:
            with zipfile.ZipFile(folder/target,'w') as out:
                for f in (classes/'qa'/prefix).rglob('*.class'):out.writestr(zipfile.ZipInfo(f.relative_to(classes).as_posix()),f.read_bytes())
                if prefix=='bukkit':
                    out.writestr(zipfile.ZipInfo('plugin.yml'),'name: UnityEventsProbe\nversion: 1.0\napi-version: "26.2"\nload: STARTUP\ndepend: [UnityFacade]\nmain: qa.bukkit.UnityEventsProbe\n')
                    out.writestr(zipfile.ZipInfo('event-types.tsv'),tsv.read_bytes())
                else:out.writestr(zipfile.ZipInfo('fabric.mod.json'),json.dumps({'schemaVersion':1,'id':'entry_probe','version':'1','entrypoints':{'main':['qa.entry.ReentrantFabric'],'client':['qa.entry.ResourceProbe'],'qa_nested':['qa.entry.ReentrantFabric$Nested'],'qa_parallel':['qa.entry.ReentrantFabric$Parallel'],'qa_retry':['qa.entry.ReentrantFabric$Retry'],'qa_cycle':['qa.entry.ReentrantFabric$Cycle'],'qa_compare':['qa.entry.ReentrantFabric::order']}}))
        def package(name, package_name, metadata_file, metadata):
            with zipfile.ZipFile(folder/(name+'.jar'),'w') as out:
                for file in sorted((classes/'qa'/package_name).rglob('*.class')):
                    out.writestr(zipfile.ZipInfo(file.relative_to(classes).as_posix()),file.read_bytes())
                out.writestr(zipfile.ZipInfo(metadata_file),metadata)
        package('UnityFacade','libb','plugin.yml','name: UnityFacade\nversion: 1\napi-version: "26.2"\nload: STARTUP\ndepend: [UnityTypes]\nmain: qa.libb.Facade\n')
        package('UnityTypes','libc','plugin.yml','name: UnityTypes\nversion: 1\napi-version: "26.2"\nload: STARTUP\nsoftdepend: [UnityEventsProbe]\nmain: qa.libc.Types\n')
        locks=json.loads((ROOT/'standalone/tests/interop/references.json').read_bytes())
        refs=loader.parent
        deps=[refs/(n+'.jar') for n in ('neo','neo-loader','neo-bus','fmlloader','javafmllanguage')]
        for file in deps:
            if hashlib.sha256(file.read_bytes()).hexdigest()!=locks[file.stem]['sha256']:raise ValueError(file)
        forge_dist=sources/'sdk/mergetool-1.1.5-api.jar'
        for file,lock_file in [(forge_dist,sources/'forge-dist.json'),(refs/'dist.jar',sources.parent/'worlds/dist-reference.json')]:
            if hashlib.sha256(file.read_bytes()).hexdigest()!=json.loads(lock_file.read_bytes())['sha256']:raise ValueError(file)
        subprocess.run(['javac','-proc:none','--release','25','-g:none','-d',classes,'-cp',os.pathsep.join(map(str,[*deps,forge_dist,refs/'dist.jar',*cp])),sources/'ForgeSideGuard.java',sources/'NeoSideGuard.java'],check=True)
        for label,meta in [('forge','META-INF/mods.toml'),('neo','META-INF/neoforge.mods.toml')]:
            package(label+'-guard',label+'guard',meta,'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="qa_'+label+'_guard"\nversion="1"\n')
        runtime=ROOT/'versions/0.2.4-Beta/unity-loader-0.2.4-Beta.jar'
        subprocess.run(['javac','-proc:none','--release','25','-g:none','-encoding','UTF-8','-d',classes,'-cp',os.pathsep.join(map(str,[runtime,game/'versions/26.2/server-26.2.jar',*cp])),sources/'ReviewLoginProbe.java'],check=True)
        package('login-review','login','META-INF/unity.properties','api=1\nenvironment=server\nid=review-login\nentrypoint=qa.login.ReviewLoginProbe\n')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('out',type=Path);p.add_argument('fabric_loader',type=Path);a=p.parse_args();build(a.out,a.fabric_loader)
