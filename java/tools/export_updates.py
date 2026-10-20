"""Observe unchanged source update-version and schema-2 manifest policies."""
import dataclasses,itertools,json,pathlib
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2]
prepare(ROOT)
from server import update_check as source
versions=['0.2.0-beta.1','0.2.0-beta.2','0.2.0-beta.10','0.2.0-rc.1','0.2.0','0.2.1','0.10.0','1.0.0','v1.0','1.0.0+build.123','1.0.0-1','1.0.0-a','99999999999999999999.0.0','garbage','01.0.0','1.0.0-01',' 1.0.0 ','1.2.3-1a','1.2.3-00a','1.2.3+...']
rows=[dict(candidate=a,current=b,newer=source.is_newer(a,b)) for a,b in itertools.product(versions,repeat=2)]
base=dict(version='0.3.0',protocol=168,package='BattleSpades-server.zip',size=123,sha256='aB'*32,root='server',urls=['https://mirror.example/server.zip'],preserve=['config.toml','bans.json'],mirror_directories=['_internal'],remove=['old/path'])
observations=[]
for override in [{},{'version':'garbage'},{'package':'../bad.zip'},{'urls':[]},{'urls':['http://example.com/s.zip']},{'size':True},{'size':0},{'size':1.2},{'sha256':'bad'},{'root':'../outside'},{'preserve':['../config']},{'remove':'old'},{'protocol':'168'},{'required':True},{'version':'v1.2'},{'urls':['http://localhost:8000/package.zip']},{'root':'bundle\\server'}]:
 document=dict(schema=2,product='BattleSpades',components=dict(server=base|override))
 try:result=dataclasses.asdict(source.parse_manifest(document));valid=True
 except source.ManifestError:result=None;valid=False
 observations.append(dict(document=document,valid=valid,release=result))
out=ROOT/'java/src/test/resources/golden/updates.json'
out.write_text(json.dumps(dict(source_commit='546c548780c400f21d73ca6713f86194ceb3c844',version=(ROOT/'VERSION').read_text().strip(),versions=rows,manifests=observations),indent=2)+'\n')
print(len(rows),'version comparisons;',len(observations),'manifest observations')
