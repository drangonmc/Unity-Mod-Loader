import json,os,zipfile,tempfile,subprocess
from pathlib import Path
def build(root,machines,sdk,cache,core,dest):
    cp=[core,sdk/'neo.jar',sdk/'neo-bus.jar',sdk/'neo-loader.jar',sdk/'dist.jar',cache/'versions/26.2/server-26.2.jar',*sorted((cache/'libraries').rglob('*.jar'))]
    for slug in ('iron-tanks','tiny-pipes','logistics-drones'):
        row=json.loads((machines/(slug+'.json')).read_bytes());cp.append(machines/row['filename'])
    with tempfile.TemporaryDirectory(dir=root/'.work') as tmp:
        d=Path(tmp)
        subprocess.run(['javac','--release','25','-proc:none','-g:none','-cp',os.pathsep.join(map(str,cp)),'-d',d,Path(__file__).with_name('TransferSetup.java')],check=True)
        with zipfile.ZipFile(dest,'w') as out:
            out.writestr(zipfile.ZipInfo('META-INF/unity.properties'),'api=1\nid=unity29transfer\nentrypoint=qa.unity29.TransferSetup\n')
            for f in sorted(d.rglob('*.class')):out.writestr(zipfile.ZipInfo(f.relative_to(d).as_posix()),f.read_bytes())
