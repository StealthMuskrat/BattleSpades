"""Unchanged admitted terrain gun/melee paths with actual Player, native map, health and collapse."""
import gzip,hashlib,io,json,logging,pathlib,random,struct,sys,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT);sys.path.insert(0,str(ROOT/'java/build/reference-python'))
from aoslib import vxl,world
from server.player import Player
from server.class_selection import ClassSelection
from server.combat_runtime import CombatSystem
from server.world_manager import WorldManager
from server.game_rules import GameRules
from server import profile_stats,combat_scores,achievements,audio
from server.dig_profiles import MELEE_DIG_PROFILES
from shared.packet import ShootPacket
import shared.constants as C
bits=lambda v:struct.pack('>d',float(v)).hex()
encode=lambda cells:';'.join(','.join(map(str,p)) for p in cells) or '-'
logging.disable(logging.CRITICAL);rows=[];tools=sorted(MELEE_DIG_PROFILES)+[6]
original=(time.monotonic,time.time);time.monotonic=lambda:100.;time.time=lambda:1000.
try:
 for case in range(256):
  events=[];wire=[];tool=tools[case%len(tools)];allows=case%13!=0;build=case%7!=0;ground=case%2==0;health=[None,3.,9.,30.][case//3%4];scale=[.5,1.,2.][case//5%3];secondary=bool(case%2);kind='gun' if case%5==0 else 'dig';active=case%2==0;mode=['tdm','cctf','ugc'][case//9%3]
  rules=GameRules.server_defaults();rules.values.update(RULE_BLOCK_HEALTH=scale,RULE_SPAWN_PROTECTION_TIME=0)
  config=NS(game_rules=rules,default_mode='tdm',game_mode='cctf' if mode=='cctf' else 'tdm',ugc_runtime=mode=='ugc',build_damage=build,respawn_time=1.5,conduct=NS(grief_kick_enabled=False,grief_warn_points=1e9))
  manager=WorldManager(config);raw=(OUT/'world/empty.vxl').read_bytes();manager.map=vxl.VXL(None,raw,len(raw));manager.world=world.World(manager.map)
  server=NS(config=config,loop_count=1000,world_manager=manager,players={},mode=NS(allow_action=lambda *a:allows),teams={2:NS(color=(18,52,86))})
  server.broadcast=lambda data,**kw:wire.append(bytes(data).hex()) if data[0]==37 else None
  server.queue_mode_event=lambda method,p,cells,mined:events.append('mode:'+str(int(mined))+':'+encode(cells))
  server.terrain_repair=NS(record_cells=lambda cells:events.append('repair:'+encode(cells)),record_collapse_cells=lambda cells:events.append('collapsed:'+encode(cells)))
  player=Player(0,'Life',2,6,NS(server=server,in_game=True,send=lambda *a,**kw:None));server.players[0]=player;player.apply_class_selection(ClassSelection(0,(5,6,23)));player.spawn(36,36,97);player.orientation=(1.,0.,0.);player.set_tool(tool,raw=True)
  cube=[(x,y,z) for x in range(39,42) for y in range(35,38) for z in range(96,99)];support=[(41,37,z) for z in range(99,240)] if ground else []
  for cell in cube+support:manager.set_block(*cell,True,0x406080,health=health)
  combat=CombatSystem(server)
  if active:combat._begin_shot_tally(player)
  packet=ShootPacket();packet.secondary=int(secondary);origin=(36.,36.,97.);direction=(0.,1.,0.) if case%19==0 else (1.,0.,0.);eyes=None if case%4==0 else [] if case%11==0 else [(99.,99.,99.)] if case%17==0 else [origin];slack=.5 if case%3==0 else 0.;random.seed(8000+case)
  def observe(frame,event,arg):
   if event!='call':return
   f=frame.f_locals;code=frame.f_code
   if code is profile_stats.add.__code__ and f.get('stat')==C.MAP_BLOCKS_DESTROYED_TOTAL:events.append('profile:'+str(f['count']))
   elif code is combat_scores.record_blocks_destroyed.__code__:events.append('score:'+str(f['count'])+':'+('|'.join(encode(c) for c in f['chunks']) or '-'))
   elif code is achievements.blocks_destroyed.__code__ and f.get('method')=='blocks_destroyed':
    a=f['args'];events.append('achieve:'+encode(a[1])+':'+encode(a[2])+':'+('|'.join(encode(c) for c in a[3]) or '-')+':'+str(a[4] if len(a)>4 else None))
   elif code is audio.play_sound.__code__:events.append('sound:'+str(f['sound_id'])+':'+encode([f['position']]))
   elif code is CombatSystem._reject.__code__:events.append('reject:'+f['kind'])
  for step in range(3):
   events.clear();wire.clear();sys.setprofile(observe)
   try:result=combat._apply_block_damage(player,(39,36,97),[.7,2.,10.][step]) if kind=='gun' else combat._resolve_spade_dig(player,origin,direction,packet,reach_eyes=eyes,reach_slack=slack)
   finally:sys.setprofile(None)
   state=';'.join(','.join(map(str,(*cell,int(manager.get_solid(*cell)),bits(manager.block_damage.get(cell,0.)),bits(manager.initial_block_health(*cell))))) for cell in cube+support)
   rows.append((case,step,tool,kind,int(allows),int(build),int(ground),'-' if health is None else bits(health),bits(scale),int(secondary),int(active),mode,int(case%19==0),'-' if eyes is None else 'empty' if not eyes else ';'.join(','.join(map(bits,e)) for e in eyes),bits(slack),int(result),player.blocks,state,'|'.join(wire) or '-','~'.join(events) or '-'))
finally:time.monotonic,time.time=original
raw=('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'terrain-commit.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'terrain-commit-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),cases=256,fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/combat_runtime.py','server/world_manager.py','server/dig_profiles.py','server/block_damage_model.py']},method_substitutions=False,scope='Unchanged admitted gun/spade terrain resolvers, actual original Player/native VXL, seeded global random, block health/collapse and Damage wire; sys.setprofile observes sound/profile/score/achievement boundaries. Controlled mode permissions, repairs and mode-event IO. No outer shot admission, full concrete mode/award/audio/repair scheduling or supported-client effects proven.'),indent=2)+'\n');print('Terrain commit operations',len(rows),'bytes',path.stat().st_size)
