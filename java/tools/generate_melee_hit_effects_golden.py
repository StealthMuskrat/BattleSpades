"""Unchanged melee resolver, actual Player damage/death/knockback and source feedback, ordinary TDM test policy."""
import gzip,hashlib,io,json,logging,pathlib,struct,sys,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from aoslib import vxl,world
from server.player import Player
from server.combat_runtime import CombatSystem
from server.world_manager import WorldManager
from server.game_rules import GameRules
from server.class_selection import ClassSelection
from server import profile_stats
from server.entities import attachments
from server.game_constants import SPADE_TOOL_IDS
sys.path.insert(0,str(ROOT/'java/build/reference-python'))
logging.disable(logging.CRITICAL);clock=[100.,1000.];original=(time.monotonic,time.time);time.monotonic=lambda:clock[0];time.time=lambda:clock[1]
bits=lambda x:struct.pack('>d',float(x)).hex()
rows=[];voxels=vxl.VXL(-1,b'',0,2);native=world.World(voxels);guns=sorted(SPADE_TOOL_IDS)
try:
 for case in range(1088):
  clock[:]=[100.,1000.];wire=[];events=[];tool=guns[case%len(guns)];cid=case//len(guns)%18;protection=3. if case%5==0 else 0.;elapsed=[0.,1.,3.,4.][case%4];weapon=[0.,.5,1.,1.25,2.][case//3%5];one=case%7==0;modifier=['none','veto','half','double'][case//7%4];health=[1,20,100,300][case//13%4];god=case%19==0;shield=case%3==0;display=bool(case%2);facing=case%4<2;z=[97.,96.5,98.5,110.][case//11%4]
  rules=GameRules.server_defaults();rules.values.update(RULE_SPAWN_PROTECTION_TIME=protection,RULE_WEAPON_DAMAGE=weapon,RULE_ONE_HIT_KILL=one)
  manager=WorldManager.__new__(WorldManager);manager.map=voxels;manager.world=native
  server=NS(loop_count=1000,config=NS(game_rules=rules,default_mode='tdm',game_mode='tdm',respawn_time=1.5,conduct=NS(grief_kick_enabled=False,grief_warn_points=1e9)),mode=NS(),players={},teams={2:NS(color=(18,52,86)),3:NS(color=(50,60,70))},world_manager=manager,queue_mode_event=lambda *args:None)
  def capture(lane,data,**kw):
   data=bytes(data)
   if data[0] in (5,46,9,20):wire.append(lane+':'+str(int(kw.get('reliable',True)))+':'+data.hex())
  server.broadcast=lambda data,**kw:capture('B',data,**kw)
  for id,team in [(0,2),(1,3)]:
   conn=NS(server=server,in_game=True,send=lambda data,**kw:capture('O1',data,**kw));p=Player(id,'Life',team,6,conn);server.players[id]=p;p.apply_class_selection(ClassSelection(cid if id else 0,(5,6,23)));p.spawn(([39,40,46,36][case//5%4]) if id else 36,36,z if id else 97);p.orientation=(-1.,0.,0.) if id and facing else (1.,0.,0.)
  attacker=server.players[0];target=server.players[1];attacker.set_tool(tool,raw=True);target.set_tool(52 if shield else 6,raw=True);target.input.can_display_weapon=display;target.god_mode=god;target.health=health
  target.velocity=(1e-6,-1e-6,1e-6) if case%11==0 else (.125,-.25,.05)
  reach=None if case%13==0 else [] if case%17==0 else [(36.,36.,97.)]+([(39.,36.,97.)] if case%5==0 else []);slack=[0.,2.,4.][case//9%3];origin=([36.,38.,44.,36.][case//5%4],36.,97.)
  if modifier!='none':server.mode.modify_incoming_damage=lambda p,amount,source,kind:0. if modifier=='veto' else amount*(.5 if modifier=='half' else 2.)
  wire.clear();clock[0]+=elapsed;clock[1]+=elapsed;combat=CombatSystem(server)
  def observe(frame,event,arg):
   if event=='call' and frame.f_code is profile_stats.hit.__code__:events.append('hit')
   if event=='call' and frame.f_code is attachments.riot_shield_hit.__code__:events.append('shield')
   if event=='call' and frame.f_code is CombatSystem._reject.__code__:events.append('reject:'+frame.f_locals['kind'])
  sys.setprofile(observe)
  try:result=combat._resolve_melee_hit(attacker,origin,(1.,0.,0.),reach_eyes=reach,reach_slack=slack)
  finally:sys.setprofile(None)
  fields=[case,tool,cid,bits(protection),bits(elapsed),bits(weapon),int(one),modifier,health,int(god),int(shield),int(display),int(facing),bits(z),int(result),target.health,int(target.alive),int(target.spawned),attacker.kills,target.deaths,';'.join(events) or '-','|'.join(wire) or '-',','.join(map(bits,target.velocity)),','.join(map(bits,tuple(target._world_object.velocity))),str([39,40,46,36][case//5%4]),','.join(map(bits,origin)),'-' if reach is None else 'empty' if not reach else ';'.join(','.join(map(bits,v)) for v in reach),bits(slack)]
  rows.append(tuple(map(str,fields)))
finally:time.monotonic,time.time=original
raw=('\n'.join('\t'.join(r) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
path=OUT/'melee-hit-effects.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'melee-hit-effects-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/combat_runtime.py','server/player.py','server/world_manager.py','server/profile_stats.py')},method_substitutions=False,scope='Unchanged melee resolver and reach, actual native terrain/Player bodies and original knockback/HP/death/feedback. Controlled ordinary TDM policy hooks, rules, clocks, class/tool/shield/health inputs; sys.setprofile observes hit/shield requests. No full packet6 admission, concrete attachment/entity/corpse lifecycle, profile persistence or complete-mode proof.'),indent=2)+'\n');print('melee hit effects operations',len(rows))
