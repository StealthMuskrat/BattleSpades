"""Unchanged medpack/radar methods, original Player healing/HP, registry and native canonical support lifecycle."""
import ast,gzip,hashlib,io,json,logging,pathlib,struct,sys,time
from types import SimpleNamespace as NS,MethodType
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from aoslib import vxl,world
from server.player import Player
from server.class_selection import ClassSelection
from server.world_manager import WorldManager
from server.game_rules import GameRules
from server.entities.registry import EntityRegistry
from server.entities.behaviors import MedpackBehavior,RadarStationBehavior
from reference_blast import methods
source=methods(ROOT);main=ast.parse((ROOT/'server/main.py').read_text());cls=next(n for n in main.body if isinstance(n,ast.ClassDef) and n.name=='BattleSpadesServer');removed=next(n for n in cls.body if isinstance(n,ast.FunctionDef) and n.name=='_radar_station_removed');ns={};exec(compile(ast.fix_missing_locations(ast.Module(body=[removed],type_ignores=[])),str(ROOT/'server/main.py'),'exec'),ns)
bits=lambda v:struct.pack('>d',float(v)).hex();clock=[100.,1000.];original=(time.monotonic,time.time);time.monotonic=lambda:clock[0];time.time=lambda:clock[1];logging.disable(logging.NOTSET);rows=[];listener_errors=[]
class ListenerErrors(logging.Handler):
 def emit(self,record):listener_errors.append(record.getMessage())
logging.getLogger('server.world_manager').addHandler(ListenerErrors(level=logging.ERROR))
try:
 for trace in range(32):
  clock[:]=[100.,1000.];wire=[];events=[];rules=GameRules.server_defaults();rules.values['RULE_SPAWN_PROTECTION_TIME']=0;config=NS(game_rules=rules,game_mode='hc_tdm' if trace%2 else 'tdm',default_mode='tdm',respawn_time=1.5,conduct=NS(grief_kick_enabled=False));manager=WorldManager(config);raw=(OUT/'world/empty.vxl').read_bytes();manager.map=vxl.VXL(None,raw,len(raw));manager.world=world.World(manager.map)
  server=NS(config=config,loop_count=1000,tick_interval=1/60,world_manager=manager,players={},connections={},entity_registry=EntityRegistry(),teams={2:NS(color=(18,52,86)),3:NS(color=(50,60,70))},mode=NS(),queue_mode_event=lambda *a:None,_map_cell_sequence=0,_radar_station_counts={2:1});server._radar_station_removed=MethodType(ns['_radar_station_removed'],server)
  for name in ['_build_entity_ctx','_record_canonical_map_mutation']:setattr(server,name,MethodType(getattr(source,name),server))
  server.broadcast_create_entity=lambda e:events.append('create:'+str(e.entity_id));server.broadcast_destroy_entity=lambda id:events.append('destroy:'+str(id));server.broadcast_change_entity_position=lambda e:None;server.broadcast=lambda *a,**kw:None
  for id,team in [(0,2),(1,3)]:
   p=Player(id,'Life',team,6,NS(server=server,in_game=True,send=lambda data,_id=id,**kw:wire.append(str(_id)+':'+bytes(data).hex())));server.players[id]=p;p.apply_class_selection(ClassSelection(0,(5,6,23)));p.spawn(39 if id==0 and trace%4==0 else 36+id,36,97);p.max_health=50 if trace%5==0 else 100;p.health=25
  packs=[]
  for index in range(2):packs.append(server.entity_registry.place(30,39+index,36,97,behavior=MedpackBehavior(2,heal_amount=[-1,0,25,100][trace%4] if index==0 else 25,uses=[0,1,3][trace%3] if index==0 else 3)))
  radar=server.entity_registry.place(36,42,36,97,player_id=0,support_cell=(42,36,97),behavior=RadarStationBehavior(2,lifetime=[.25,1.,45.][trace%3],health=1. if trace%2 else 45.));radar.fuse=45.;server.players[0]._radar_entity_id=99 if trace%3==0 else radar.entity_id;manager.set_block(42,36,97,True,0x406080);manager.subscribe_mutations(server._record_canonical_map_mutation)
  for step in range(32):
   clock[0]+=.5;clock[1]+=.5;wire.clear();events.clear();op=step%16;ctx=server._build_entity_ctx();result='-'
   if op in [0,1,8,9,13]:result=str(int(packs[op%8 if op!=13 else 0].behavior.on_touch(packs[op%8 if op!=13 else 0],server.players[1 if op==13 else 0],ctx)))
   elif op in [2,10]:server.players[0].damage(1.,source=None,kill_type=7)
   elif op in [3,11,15]:result=str(server.entity_registry.tick(ctx))
   elif op==4 and trace%4==0:manager.set_block(42,36,97,False)
   elif op==5 and trace%4==1:server.entity_registry.damage_entity(radar.entity_id,50.,None,ctx)
   elif op==6:config.game_mode='hc_tdm' if config.game_mode=='tdm' else 'tdm'
   elif op==7:server.players[0]._radar_entity_id=radar.entity_id if step==7 else 99
   elif op==12:server.players[0].health=25
   elif op==14 and trace%4==2:radar.behavior.on_destroyed(radar,None,ctx)
   players=';'.join(str(p.health)+','+bits(p._last_damage_at)+','+bits(getattr(p,'_hardcore_next_heal',0.)) for p in server.players.values());state=';'.join(str(int(e.alive))+','+str(getattr(e.behavior,'uses',0))+','+bits(e.behavior.health)+','+bits(e.fuse) for e in [*packs,radar]);slot=server.players[0]._radar_entity_id;rows.append((trace,step,op,result,players,state,server._radar_station_counts[2],'-' if slot is None else slot,';'.join(events) or '-','|'.join(wire) or '-'))
  clock[0]+=10;clock[1]+=10;server.config.game_mode='hc_tdm';player=server.players[0];player.health=25;pack=server.entity_registry.place(30,39,36,97,behavior=MedpackBehavior(2,heal_amount=25,uses=3));wire.clear()
  def fail_send(data,**kw):
   wire.append('0:'+bytes(data).hex());raise RuntimeError('Controlled reliable IO fault')
  player.connection.send=fail_send
  try:pack.behavior.on_touch(pack,player,server._build_entity_ctx())
  except RuntimeError as failure:assert str(failure)=='Controlled reliable IO fault'
  else:raise AssertionError('Expected original heal send failure')
  rows.append(('fault',trace,player.health,pack.behavior.uses,bits(player._hardcore_next_heal),int(pack.alive),len(server.entity_registry.all()),'|'.join(wire)))
finally:time.monotonic,time.time=original
assert not listener_errors,listener_errors
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'care-radar.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'care-radar-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/entities/behaviors.py','server/entities/registry.py','server/player.py','server/hardcore.py','server/main.py']},fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),method_substitutions=False,listener_errors=len(listener_errors),heal_io_fault_cases=32,scope='Unchanged medpack/radar behaviors, actual Player heal/damage, shared cooldown, registry, native canonical support callback and original radar-count method AST. Controlled clocks/rules/mode flag and IO. Does not establish full Hardcore lifecycle, pickup physics, placement, full server loop or client detection.'),indent=2)+'\n');print('Care/radar operations',len(rows))
