"""Unchanged registry/device behaviors/shared blast/HP/native map and actual canonical support callback."""
import gzip,hashlib,io,json,logging,pathlib,struct,sys,time
from types import SimpleNamespace as NS,MethodType
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT);sys.path.insert(0,str(ROOT/'java/build/reference-python'))
from aoslib import vxl,world
from server.player import Player
from server.class_selection import ClassSelection
from server.world_manager import WorldManager
from server.game_rules import GameRules
from server.entities.registry import EntityRegistry
from server.entities.behaviors import GraveBehavior,TimedExplosiveBehavior,ProximityMineBehavior,RemoteChargeBehavior
from reference_blast import methods
source=methods(ROOT);bits=lambda v:struct.pack('>d',float(v)).hex();vec=lambda v:','.join(map(bits,v));clock=[100.,1000.];original=(time.monotonic,time.time);time.monotonic=lambda:clock[0];time.time=lambda:clock[1];logging.disable(logging.NOTSET);rows=[];listener_errors=[]
class ListenerErrors(logging.Handler):
 def emit(self,record):listener_errors.append(record.getMessage())
logging.getLogger('server.world_manager').addHandler(ListenerErrors(level=logging.ERROR))
try:
 for trace in range(32):
  clock[:]=[100.,1000.];wire=[];events=[];rules=GameRules.server_defaults();rules.values['RULE_SPAWN_PROTECTION_TIME']=0;config=NS(game_rules=rules,default_mode='tdm',game_mode='tdm',respawn_time=1.5,build_damage=False,friendly_fire=False,conduct=NS(grief_kick_enabled=False,grief_warn_points=1e9));manager=WorldManager(config);raw=(OUT/'world/empty.vxl').read_bytes();manager.map=vxl.VXL(None,raw,len(raw));manager.world=world.World(manager.map)
  server=NS(config=config,loop_count=1000,tick_interval=1/60,world_manager=manager,players={},connections={},teams={2:NS(color=(18,52,86)),3:NS(color=(50,60,70))},mode=NS(),entity_registry=EntityRegistry(),queue_mode_event=lambda *args:None,bot_stimuli=NS(publish=lambda *args,**kw:events.append('stimulus')),_map_cell_sequence=0)
  for name in ['_apply_blast','_build_entity_ctx','_record_canonical_map_mutation']:setattr(server,name,MethodType(getattr(source,name),server))
  server.broadcast_create_entity=lambda e:events.append('create:'+str(e.entity_id));server.broadcast_destroy_entity=lambda id:events.append('destroy:'+str(id));server.broadcast_change_entity_position=lambda e:None
  def capture(lane,data,**kw):
   if data[0] in [5,46]:wire.append(lane+':'+bytes(data).hex());events.append('wire:'+lane+':'+str(data[0]))
  server.broadcast=lambda data,**kw:capture('B',data,**kw)
  for id,team in [(0,2),(1,3),(2,2)]:
   p=Player(id,'Life',team,6,NS(server=server,in_game=True,send=lambda data,_id=id,**kw:capture('O'+str(_id),data,**kw)));server.players[id]=p;p.apply_class_selection(ClassSelection(0,(5,6,23)));p.spawn(36+id*3,36,95 if id==1 else 97);p.orientation=(1.,0.,0.);p.health=1000 if trace%3 else 100
  saved=[]
  for index in range(3):
   kind=(trace+index)%4;at=(39.+index,36.,97.);face=(trace+index)%7;health=1. if trace%2 else 10.;damage=30. if trace%3 else 100.;fuse=.5;center=(39.,36.,96.) if trace%2 else None
   if kind==0:b=GraveBehavior(0,fuse=fuse,damage=damage,block_damage=0.,blast_radius=6.,kill_type=13,explosion_center=center)
   elif kind==1:b=TimedExplosiveBehavior(0,fuse,damage,0.,1,15,blast_radius=6.,health=health)
   elif kind==2:b=ProximityMineBehavior(0,2,damage,0.,1,14,blast_radius=6.,health=health,arm_delay=.25)
   else:b=RemoteChargeBehavior(0,damage=damage,block_damage=0.,crater_radius=1,blast_radius=6.,health=health)
   cell=tuple(map(int,at));manager.set_block(*cell,True,0x406080);e=server.entity_registry.place(38 if kind==3 else 9,*at,behavior=b,face=face,player_id=0,support_cell=cell);saved.append(e)
   values=[e.entity_id,kind,face,bits(health),bits(damage),bits(b.blast_radius),bits(b.knockback_min),bits(b.knockback_max),'-' if getattr(b,'damage_type',None) is None else b.damage_type,int(b.force_destroy),int(getattr(b,'ignore_player_los',False))]
   rows.append(('init',trace,index,','.join(map(str,values))))
  server.players[0]._c4_entity_ids=[0,1,2,0];manager.subscribe_mutations(server._record_canonical_map_mutation)
  for step in range(24):
   clock[0]+=.25;clock[1]+=.25;events.clear();wire.clear();op=step%8;index=(trace+step)%3;e=saved[index];ctx=server._build_entity_ctx();arg=str(index);result='-'
   if op in [0,3]:result=str(server.entity_registry.tick(ctx,max_on_tick=1 if op==3 else None))
   elif op==1:server.entity_registry.damage_entity(e.entity_id,1.0,server.players[trace%3] if trace%4 else None,ctx)
   elif op==2:
    if trace%2==0:manager.destroy_blocks([(39+index,36,97),(39+(index+1)%3,36,97)])
    else:manager.set_block(39+index,36,97,False)
   elif op==4:
    player=server.players[1];player.position=(39.5,36.,95.);player.input.crouch=not player.input.crouch
   elif op==5:result=str(server.entity_registry.support_removed((39+index,36,97),ctx))
   elif op==6:
    if isinstance(e.behavior,RemoteChargeBehavior):e.behavior.detonate(e,ctx)
   elif op==7:
    if step==15:server.entity_registry.clear()
   state=';'.join(str(e.entity_id)+','+str(int(e.alive))+','+bits(getattr(e.behavior,'health',0.))+','+('-' if e.support_cell is None else ':'.join(map(str,e.support_cell))) for e in saved)
   players=';'.join(str(p.id)+','+str(p.health)+','+str(int(p.alive))+','+vec(p.velocity) for p in server.players.values());present=','.join(str(e.entity_id) for e in server.entity_registry.all()) or '-';owner=','.join(map(str,server.players[0]._c4_entity_ids)) or '-'
   rows.append((trace,step,op,arg,result,state,players,present,owner,';'.join(events) or '-','|'.join(wire) or '-'))
finally:time.monotonic,time.time=original
assert not listener_errors,listener_errors
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'deployable.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'deployable-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=32*24,source_listener_errors=0,source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/main.py','server/entities/behaviors.py','server/entities/registry.py','server/conduct.py','server/player.py','server/world_manager.py']},fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),method_substitutions=False,scope='Unchanged concrete grave/timed/mine/C4 behavior, registry, decorated shared blast method AST, actual original Player HP/native map and canonical-support method AST. Controlled clocks, settings and lifecycle/network IO. Chained instigator damage/HP bytes, support removal, fuse/caps, direct C4 damage vs detonation, stale owner lists/registry reset. No placement authorization, concrete achievement/mode/stimulus service, terrain blast commit or full gameplay-loop readiness.'),indent=2)+'\n');print('Deployable operations',32*24)
