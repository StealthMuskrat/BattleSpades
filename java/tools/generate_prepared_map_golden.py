"""Actual WorldManager.load_map worker prewarm with native VXL and parsed sidecars.

Only map bytes and metadata inputs are constructed. No loader, spawn search, anchor,
or lifecycle policy is replaced. Worker thread identities are observed as booleans.
"""
import asyncio,hashlib,json,logging,pathlib,tempfile,threading
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from server.world_manager import WorldManager
from server.config import ServerConfig
logging.disable(logging.CRITICAL)
rows=[]
async def main():
 for case in range(4):
  raw=bytes([0,239,239,0,1,2,3,127])+(bytes([0,100,100,0,1,2,3,127])*255) if case else bytes([0,239,239,0,1,2,3,127])
  meta={'vxl_format':'retail'};mode='oc' if case==3 else 'tdm'
  if case:
   meta.update(team_one_spawn_area=[[[252,252,96],[4,4,12]]],team_two_spawn_area=[[[259,259,96],[4,4,12]]])
  if case==2:meta.update(team_one_base_point=[252,252,100],team_one_base_w_h_d=[4,4,12],team_two_base_point=[259,259,100],team_two_base_w_h_d=[4,4,12])
  if case==3:meta.update(oc_team_one_spawn_area=[[[259,252,96],[4,4,12]]],oc_team_two_spawn_area=[[[252,259,96],[4,4,12]]])
  with tempfile.TemporaryDirectory() as directory:
   name='Prepared'+str(case);path=pathlib.Path(directory)/(name+'.vxl');path.write_bytes(raw);path.with_suffix('.json').write_text(json.dumps(meta))
   config=ServerConfig();config.maps_path=directory;config.default_mode=mode;caller=threading.get_ident()
   def load():
    world=WorldManager(config);assert world.load_map(name)
    return dict(worker=threading.get_ident()!=caller,teams=[dict(team=t,candidates=world._spawn_candidates[t],anchor=world._team_base_anchors[t]) for t in (2,3)])
   result=await asyncio.to_thread(load);assert result['worker'];rows.append(dict(case=case,name=name,mode=mode,raw=raw.hex(),metadata=json.dumps(meta,separators=(',',':')),**result))
asyncio.run(main())
out=ROOT/'java/src/test/resources/golden/prepared-map.json';out.write_text(json.dumps({'cases':rows},separators=(',',':'))+'\n')
files=['server/world_manager.py','server/map_metadata.py','server/config.py','aoslib/vxl.pyx','aoslib/world.pyx']
(out.parent/'prepared-map-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',observations=len(rows),team_caches=2*len(rows),fixture_sha256=hashlib.sha256(out.read_bytes()).hexdigest(),policy_method_substitutions=False,source_files_sha256={f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest() for f in files},scope=__doc__.strip()),indent=2)+'\n')
print('Prepared map source oracle',len(rows),'worker loads')
