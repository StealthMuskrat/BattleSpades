"""Unchanged complete source handle_shot sequence with real Player/native geometry/health, controlled ordinary-mode domain inputs."""
import gzip,hashlib,io,json,logging,pathlib,struct,sys,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
sys.path.insert(0,str(ROOT/'java/build/reference-python'))
from aoslib import vxl,world
from server.player import Player
from server.combat_runtime import CombatSystem
from server.world_manager import WorldManager
from server.game_rules import GameRules,RULE_DEFINITIONS
from server.class_selection import ClassSelection
from server import profile_stats,combat_scores,anticheat_report,anticheat,achievements
from shared.packet import ShootPacket
logging.disable(logging.CRITICAL);clock=[100.,1000.];original=(time.monotonic,time.time);time.monotonic=lambda:clock[0];time.time=lambda:clock[1]
bits=lambda x:struct.pack('>d',float(x)).hex()
rows=[];voxels=vxl.VXL(-1,b'',0,2);native=world.World(voxels);tools=[6,7,8,9,15,18,52,60,0,5]
def aggregates(p):
 s=getattr(p,'anticheat_stats',{});return ','.join(str(s.get(k,0)) for k in ['shots','hits','headshots','pellet_hits'])+'|'+(';'.join(str(k)+','+','.join(str(v[n]) for n in ['shots','hits','headshots']) for k,v in sorted(s.get('weapons',{}).items())) or '-')+'|'+(';'.join(str(k)+','+str(v) for k,v in sorted(s.get('pellet_seeds',{}).items())) or '-')
try:
 for case in range(800):
  clock[:]=[100.,1000.];wire=[];events=[];tool=tools[case%len(tools)];cid=case//10%18;protection=3. if case%5==0 else 0.;elapsed=[0.,.1,1.,4.][case//3%4];health=[1,100,300][case//7%3];god=case%19==0;alive=case%37!=0;allows=case%13!=0;burden=case%17==0;intel=case%2==0;mounted=case%3!=0;bot=case%11==0;warm=case%4==0;clip=0 if case%23==0 else 7;aim=[(1.,0.,0.),(0.,0.,0.),(0.,1.,0.),(float('nan'),0.,0.)][case//29%4];origin=(999.,999.,999.) if case%31==0 else (36.,36.,97.);key=next((r.key for r in RULE_DEFINITIONS.values() if r.tool_id==tool),None) if case%41==0 else None
  rules=GameRules.server_defaults();rules.values['RULE_SPAWN_PROTECTION_TIME']=protection
  if key:rules.values[key]=False
  manager=WorldManager.__new__(WorldManager);manager.map=voxels;manager.world=native
  mode=NS(allow_action=lambda p,a,pos:allows,weapon_fired=lambda p,t:events.append('fired'),shoot_with_intel=intel)
  server=NS(loop_count=1000,config=NS(game_rules=rules,default_mode='tdm',game_mode='tdm',respawn_time=1.5,lag_compensation_enabled=False,conduct=NS(grief_kick_enabled=False,grief_warn_points=1e9)),mode=mode,players={},teams={2:NS(color=(18,52,86)),3:NS(color=(50,60,70))},world_manager=manager,queue_mode_event=lambda *args:None,bot_stimuli=NS(publish=lambda *args,**kw:events.append('stimulus')))
  def capture(lane,data,**kw):
   data=bytes(data)
   if data[0] in (5,46,8,9,20):wire.append(lane+':'+str(int(kw.get('reliable',True)))+':'+('-' if kw.get('exclude') is None else str(kw['exclude'].id))+':'+data.hex())
  server.broadcast=lambda data,**kw:capture('B',data,**kw)
  for id,team in [(0,2),(1,3)]:
   conn=NS(server=server,in_game=True,send=lambda data,**kw:capture('O1',data,**kw));p=Player(id,'Life',team,6,conn);server.players[id]=p;p.apply_class_selection(ClassSelection(cid if id else 0,(5,6,23)));p.spawn(39 if id else 36,36,97);p.orientation=(1.,0.,0.)
  attacker=server.players[0];target=server.players[1];attacker.set_tool(tool,raw=True);attacker.is_bot=bot;attacker.ammo_clip,attacker.ammo_reserve=clip,100;attacker.pickup_burdensome=burden;attacker.disguised=True;attacker._observe_input_arrival_order(101,1000);target.health=health;target.god_mode=god
  if mounted and tool==15:
   attacker.mounted_entity_id=10;entity=NS(alive=True,type=12,player_id=0,behavior=NS(carrier_id=0));server.entity_registry=NS(get=lambda id:entity,all=lambda:[])
   import shared.constants as C
   entity.type=int(C.MACHINE_GUN)
  if warm:attacker.consume_shot(clock[0],loop=100)
  if not alive:attacker.alive=False;attacker.spawned=False
  clock[0]+=elapsed;clock[1]+=elapsed;packet=ShootPacket();packet.loop_count=101;packet.shooter_id=254;packet.shot_on_world_update=0;packet.x,packet.y,packet.z=origin;packet.ori_x,packet.ori_y,packet.ori_z=aim;packet.damage=32767;packet.penetration=32767;packet.affect_shooter=1;packet.secondary=1;packet.seed=case%256;combat=CombatSystem(server);wire.clear();events.clear()
  def observe(frame,event,arg):
   if event!='call':return
   code=frame.f_code;f=frame.f_locals
   if code is profile_stats.shot.__code__:events.append('profile')
   elif code is combat_scores.record_shot.__code__:events.append('record')
   elif code is anticheat_report.observe_shot.__code__:events.append('observe')
   elif code is profile_stats.hit.__code__:events.append('hit')
   elif code is achievements.shot_resolved.__code__ and f.get('method')=='shot_resolved':events.append('resolved')
   elif code is CombatSystem._resolve_spade_dig.__code__:events.append('dig')
   elif code is CombatSystem._log_near_miss.__code__:events.append('near')
   elif code is CombatSystem._log_shot_drop.__code__:events.append('drop:'+f['reason'])
   elif code is anticheat.report.__code__:events.append('report:'+f['kind'])
  sys.setprofile(observe)
  try:result=combat.handle_shot(attacker,packet)
  finally:sys.setprofile(None)
  fields=[case,tool,cid,bits(protection),bits(elapsed),health,int(god),int(alive),int(allows),int(burden),int(intel),int(mounted),int(bot),int(warm),clip,key or '-',','.join(map(bits,origin)),','.join(map(bits,tuple((packet.ori_x,packet.ori_y,packet.ori_z)))),packet.seed,int(result),attacker.ammo_clip,int(attacker.disguised),bits(attacker.spawn_protection_remaining()),target.health,int(target.alive),attacker.kills,target.deaths,aggregates(attacker),';'.join(events) or '-','|'.join(wire) or '-']
  rows.append(tuple(map(str,fields)))
finally:time.monotonic,time.time=original
raw=('\n'.join('\t'.join(r) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
path=OUT/'combat-commit.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'combat-commit-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/combat_runtime.py','server/player.py','server/anticheat.py','server/class_selection.py')},method_substitutions=False,scope='Unchanged handle_shot/validation/cadence/pellets/actual native geometry/Player HP/death/tally and codecs. Controlled ordinary-mode inputs, original mounted-MG authority with controlled registry entry, no terrain on dig rays. Observed source callback boundaries via sys.setprofile; complete concrete modes, scoring/profile/aim-report implementations, attachments and terrain mutations remain outside this fixture.'),indent=2)+'\n');print('combat commit operations',len(rows))
