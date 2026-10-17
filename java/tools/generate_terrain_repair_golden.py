"""Execute unchanged bounded repair service with actual original native-world canonical packets."""
import gzip,hashlib,io,json,pathlib,random,sys
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT);sys.path.insert(0,str(ROOT/'java/build/reference-python'))
from aoslib import vxl
from server.world_manager import WorldManager
from server.game_rules import GameRules
from server.terrain_repair import TerrainRepairService
encode=lambda cells:';'.join(','.join(map(str,c)) for c in cells) or '-'
rows=[]
for trace in range(32):
 rng=random.Random(901+trace);config=NS(game_rules=GameRules.server_defaults(),game_mode='tdm',ugc_runtime=False);manager=WorldManager(config);raw=(OUT/'world/empty.vxl').read_bytes();manager.map=vxl.VXL(None,raw,len(raw));metrics=NS(dropped_terrain_repairs=0,terrain_repair_queue_peak=0,failed_terrain_repair_sends=0,terrain_repair_cells=0,terrain_repair_sends=0);wire=[];attempts=[0];fault=[0]
 def send(id,data,**kw):
  attempts[0]+=1
  if fault[0] and id==2 and attempts[0]%fault[0]==0:raise RuntimeError('Controlled send fault')
  wire.append(str(id)+':'+bytes(data).hex())
 connections={id:NS(player=NS(id=id),in_game=True,send=lambda data,_id=id,**kw:send(_id,data,**kw)) for id in [7,2,5]};server=NS(config=config,loop_count=0,world_manager=manager,metrics=metrics,connections=connections);service=TerrainRepairService(server)
 settings=[True,[1,64,70,8192][trace%4],[-1,1,6][trace%3],[-1,1,3][trace%3],[0,1,3][trace%3],[0,1,4][trace%3],[0,1,5][trace%3]]
 names=['terrain_repair_enabled','terrain_repair_queue_limit','terrain_repair_delay_ticks','terrain_collapse_repair_delay_ticks','terrain_repair_interval_ticks','terrain_repair_batch_limit','terrain_collapse_repair_batch_limit']
 for name,value in zip(names,settings):setattr(config,name,value)
 for step in range(96):
  server.loop_count+=rng.randrange(1,7);wire.clear();op=step%12;args='-';result=0
  if op in [0,1,2,3,7,8]:
   cells=[(10+x,10+y,100+z) for x in range(5) for y in range(5) for z in range(5)] if op in [1,7] else [(rng.randrange(8,16),rng.randrange(8,16),rng.randrange(98,106)) for _ in range(20)]+[(-1,0,0),(512,0,0),(0,0,240)]
   args=encode(cells);service.record_collapse_cells(cells) if op in [1,7,8] else service.record_cells(cells)
  elif op==4:
   cell=(10,10,100);solid=bool((step//12)%2);manager.set_block(*cell,solid,0x406080,health=9 if solid else None);args=str(int(solid))
  elif op==5:
   settings[0]=not settings[0];config.terrain_repair_enabled=settings[0];args=str(int(settings[0]))
  elif op==6:
   eligible=step//12%3;fault[0]=3 if step//12%2 else 0;args=str(eligible)+','+str(fault[0]);connections[7].in_game=eligible!=0;connections[2].in_game=eligible==2;connections[5].player=None
  elif op==9:
   if step//12%4==0:service.reset();args='reset'
   else:result=service.tick();args='tick'
  elif op==10:manager.apply_block_damage(12,12,102,2)
  else:result=service.tick()
  state=','.join(map(str,[service.pending_count,metrics.dropped_terrain_repairs,metrics.terrain_repair_queue_peak,metrics.terrain_repair_cells,metrics.terrain_repair_sends,metrics.failed_terrain_repair_sends,attempts[0]]));rows.append((trace,step,server.loop_count,op,args,int(result),state,'|'.join(wire) or '-'))
assert any(row[7]!='-' for row in rows) and max(int(row[6].split(',')[5]) for row in rows)>0
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'terrain-repair.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'terrain-repair-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/terrain_repair.py','server/world_manager.py']},operations=len(rows),fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),method_substitutions=False,scope='Unchanged repair service and native-world canonical health packets; controlled settings/clocks/recipient and send-fault IO. Bounded lanes, surface-first overflow, duplicates/rebuilds, enable/reset/no-recipient behavior, exact per-recipient bytes and metrics. No supported-client convergence or full simulation-loop binding established.'),indent=2)+'\n');print('Repair source operations',len(rows),'bytes',path.stat().st_size)
