"""Unchanged blast terrain dispatcher, original CombatSystem/native health/collapse and exact known-causer wire routing."""
import gzip,hashlib,io,json,logging,pathlib,random,struct,sys,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT);sys.path.insert(0,str(ROOT/'java/build/reference-python'))
from aoslib import vxl,world
from server.world_manager import WorldManager
from server.game_rules import GameRules
from server.player import Player
from server.class_selection import ClassSelection
from server.combat_runtime import CombatSystem
from server import block_damage_model,profile_stats,combat_scores,achievements,audio
from reference_blast import methods
import shared.constants as C
source=methods(ROOT);bits=lambda v:struct.pack('>d',float(v)).hex();cells=lambda points:';'.join(','.join(map(str,c)) for c in points) or '-';logging.disable(logging.CRITICAL);original=(time.monotonic,time.time);time.monotonic=lambda:100.;time.time=lambda:1000.;rows=[];types=[None,*sorted(block_damage_model.FOOTPRINTS),99]
try:
 for case in range(192):
  events=[];wire=[];kind=types[case%len(types)];health=[None,3.,9.][case//3%3];scale=[.5,1.,2.][case//7%3];ground=case%2==0;allows=case%13!=0;causer=91 if case%2 else None;rules=GameRules.server_defaults();rules.values.update(RULE_BLOCK_HEALTH=scale,RULE_SPAWN_PROTECTION_TIME=0);config=NS(game_rules=rules,default_mode='tdm',game_mode='tdm',ugc_runtime=False,build_damage=True,respawn_time=1.5,conduct=NS(grief_kick_enabled=False,grief_warn_points=1e9));manager=WorldManager(config);raw=(OUT/'world/empty.vxl').read_bytes();manager.map=vxl.VXL(None,raw,len(raw));manager.world=world.World(manager.map)
  server=NS(config=config,loop_count=1000,world_manager=manager,players={},mode=NS(allow_action=lambda *args:allows),teams={2:NS(color=(18,52,86))},queue_mode_event=lambda method,p,positions,mined:events.append('mode:'+str(int(mined))+':'+cells(positions)),terrain_repair=NS(record_cells=lambda positions:events.append('repair:'+cells(positions)),record_collapse_cells=lambda positions:events.append('collapsed:'+cells(positions))))
  server.broadcast=lambda data,**kw:wire.append('B:'+bytes(data).hex());server._record_map_mutation=lambda data:None
  server.connections={id:NS(in_game=id!=5,known_entity_ids={91} if id in [7,5] else set(),send=lambda data,_id=id,**kw:wire.append('R'+str(_id)+':'+bytes(data).hex())) for id in [7,2,5]};server.broadcast_known_entity_packet=lambda *args,**kw:None
  player=Player(0,'Life',2,6,NS(server=server,in_game=True,send=lambda *args,**kw:None));server.players[0]=player;player.apply_class_selection(ClassSelection(0,(5,6,23)));player.spawn(36,36,97);thrower=None if case%11==0 else player;combat=CombatSystem(server);server.combat=combat
  cube=[(x,y,z) for x in range(39,42) for y in range(35,38) for z in range(96,99)];support=[(41,37,z) for z in range(99,240)] if ground else []
  for cell in cube+support:manager.set_block(*cell,True,0x406080,health=health)
  random.seed(8000+case)
  def observe(frame,event,arg):
   if event!='call':return
   f=frame.f_locals;code=frame.f_code
   if code is profile_stats.add.__code__ and f.get('stat')==C.MAP_BLOCKS_DESTROYED_TOTAL:events.append('profile:'+str(f['count']))
   elif code is combat_scores.record_blocks_destroyed.__code__:events.append('score:'+str(f['count'])+':'+('|'.join(cells(c) for c in f['chunks']) or '-'))
   elif code is achievements.blocks_destroyed.__code__ and f.get('method')=='blocks_destroyed':
    a=f['args'];events.append('achieve:'+cells(a[1])+':'+cells(a[2])+':'+('|'.join(cells(c) for c in a[3]) or '-')+':'+str(a[4] if len(a)>4 else None))
   elif code is audio.play_sound.__code__:events.append('sound:'+str(f['sound_id'])+':'+cells([f['position']]))
  for step in range(3):
   events.clear();wire.clear();amount=[.7,2.,10.][step];sys.setprofile(observe)
   try:result=source._apply_blast_terrain(server,40.25,36.5,97.125,amount,thrower,crater_radius=1,damage_type=kind,causer_entity_id=causer)
   finally:sys.setprofile(None)
   state=';'.join(','.join(map(str,[*cell,int(manager.get_solid(*cell)),bits(manager.block_damage.get(cell,0.)),bits(manager.initial_block_health(*cell)),manager.get_color(*cell)&0xffffff if manager.get_solid(*cell) else 0])) for cell in cube+support)
   rows.append((case,step,'-' if kind is None else kind,'-' if health is None else bits(health),bits(scale),int(ground),int(allows),'-' if causer is None else causer,int(thrower is not None),int(result),state,'|'.join(wire) or '-','~'.join(events) or '-'))
finally:time.monotonic,time.time=original
assert any('R7:' in r[11] and 'R2:' in r[11] for r in rows)
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'blast-terrain.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'blast-terrain-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/main.py','server/combat_runtime.py','server/world_manager.py','server/block_damage_model.py']},method_substitutions=False,scope='Unchanged _apply_blast_terrain method AST and original CombatSystem/native world health/collapse/packet methods. Seeded source random and sys.setprofile observe effect requests. Known entity causer receives native expanding packet; unknown gets exact destroyed/surviving cells; loading peer gated. Exact topology/health/color/wire and effect order. Network/journal/settings/mode IO controlled; raw legacy journal callback is IO-only here; Java uses previously verified canonical state journal. No concrete services, full server loop or client convergence proof.'),indent=2)+'\n');print('Blast terrain operations',len(rows))
