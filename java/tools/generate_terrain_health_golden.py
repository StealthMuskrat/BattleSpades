"""Unchanged original native-world block ledger, paint, shade, reveal and canonical repair."""
import gzip,hashlib,io,json,pathlib,random,struct,sys
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT);sys.path.insert(0,str(ROOT/'java/build/reference-python'))
from aoslib import vxl
from server.world_manager import WorldManager
from server.game_rules import GameRules
from server.terrain_repair import TerrainRepairService
from server.prefab_actions import block_state_packets,PrefabActionService
bits=lambda value:struct.pack('>d',float(value)).hex()
cells=[(255,255,180),(255,255,190),(10,10,100),(11,10,100),(12,10,239)]
rows=[];rng=random.Random(19261)
def packet_rows(manager,wanted,replay):
 user,damaged=manager.block_manager_rows(wanted,replay_hits=replay);return ';'.join(bytes(p).hex() for p in block_state_packets(user,damaged)) or '-'
def shade_rows(manager,wanted):return ';'.join(','.join(map(str,(*r[:3],(r[3][0]<<16)|(r[3][1]<<8)|r[3][2]))) for r in manager.block_shade_rows(wanted)) or '-'
def hit_rows(manager,wanted):return ';'.join(','.join(map(str,r[:3]))+','+','.join(map(bits,r[3])) for r in manager.block_hit_replays(wanted)) or '-'
for trace in range(36):
 rules=GameRules.server_defaults();scale=[.25,1.,1.25,10.][trace%4];rules.values['RULE_BLOCK_HEALTH']=scale;mode=['tdm','cctf','ugc'][trace%3] if trace<32 else 'tdm';scale=scale if trace<32 else 10.;rules.values['RULE_BLOCK_HEALTH']=scale;config=NS(game_rules=rules,game_mode='cctf' if mode=='cctf' else 'tdm',ugc_runtime=mode=='ugc');manager=WorldManager(config);raw=(OUT/'world/cave.vxl').read_bytes();manager.map=vxl.VXL(None,raw,len(raw));repair=TerrainRepairService(NS(world_manager=manager,loop_count=1000))
 for step in range(100 if trace<32 else 160):
  cell=cells[step%len(cells)];health=[None,3.,5.,9.,30.][step//5%5];rgb=[0,0x406080,0x102030,0xffffff,0x804020][step//7%5]
  if trace>=32:
   cell=cells[1] if trace==33 else cells[3]
   if step==0 and trace!=33:op='set';args='0,'+bits(30.);manager.set_block(*cell,True,0,health=30.);result='-'
   elif step==72 and trace!=33:op='set';args=str(0x406080)+',-';manager.set_block(*cell,True,0x406080);result='-'
   else:
    op='damage';amount=.7 if trace==34 and step==1 else .25;args=bits(amount);total,gone=manager.apply_block_damage(*cell,amount);result=bits(total)+','+str(int(gone))
  elif step%17==0:op='scale';scale=[.25,1.,1.25,10.][step//17%4];rules.values['RULE_BLOCK_HEALTH']=scale;args=bits(scale);result='-'
  elif step%11==0 and cell[2]<239:op='remove';args='-';manager.set_block(*cell,False);result='-'
  elif step%7 in [0,1]:op='set';args=str(rgb)+','+('-' if health is None else bits(health));manager.set_block(*cell,True,rgb,health=health);result='-'
  else:
   op='damage';amount=[.25,.5,1.,2.,.7,4.,9.][step//3%7];args=bits(amount);total,gone=manager.apply_block_damage(*cell,amount);result=bits(total)+','+str(int(gone))
  state=';'.join(','.join(map(str,(*p,int(manager.get_solid(*p)),int(manager.get_color(*p)),int(manager.map.has_explicit_color(*p)),bits(manager.initial_block_health(*p)),bits(manager.block_damage.get(p,0.))))) for p in cells)
  canonical=';'.join(bytes(p).hex() for p in repair.canonical_packets(cell,7)) or '-'
  reveal=[];service=PrefabActionService.__new__(PrefabActionService);service.server=NS(world_manager=manager,loop_count=1000,config=NS(prefab_health_state_batch=[0,1,2,128,5000][trace%5]));service.reveal_to(NS(player=NS(id=7),send=lambda data,**kw:reveal.append(bytes(data).hex())))
  rows.append((trace,step,mode,op,','.join(map(str,cell)),args,result,state,packet_rows(manager,None,False),packet_rows(manager,None,True),packet_rows(manager,[cell],False),shade_rows(manager,None),hit_rows(manager,None),canonical,';'.join(reveal) or '-'))
raw=('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'terrain-health.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'terrain-health-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/world_manager.py','server/block_damage_model.py','server/terrain_repair.py','server/prefab_actions.py']},operations=len(rows),traces=36,fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),method_substitutions=False,scope='Unchanged original WorldManager with actual native VXL and unchanged canonical repair/packet builders. Controlled normal/Classic/UGC modes and health-rule values; damage, replacement/paint/removal, raw colors/explicitness, initial/accumulated health, full/target/replay rows, compound shades, replay history and canonical repair bytes. No complete mode or live join/repair scheduling proven.'),indent=2)+'\n');print('Terrain health operations',len(rows),'bytes',path.stat().st_size)
