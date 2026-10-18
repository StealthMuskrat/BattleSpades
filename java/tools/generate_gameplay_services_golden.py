"""Intact TeamBalancer/forced TeamChanges/native Player and gameplay audio routing.
Only mode/domain state, IO, clocks and awaited bot director lifecycle are controlled.
"""
import asyncio,hashlib,json,logging,pathlib,time
from types import SimpleNamespace as N
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from aoslib import vxl,world
from server.player import Player
from server.team import Team
from server.game_rules import GameRules
from server.team_balance import TeamBalancer
from server.class_selection import normalize_server_selection
from server import audio
logging.disable(logging.CRITICAL)
clock=[100.,1000.];original=time.monotonic,time.time;time.monotonic=lambda:clock[0];time.time=lambda:clock[1]
rows=[]
async def balance_case(actions):
 clock[:]=[100.,1000.];log=[];direct=[];denied=set();retire_denied=set();director_denied=set()
 rules=GameRules.server_defaults();config=N(respawn_time=1.5,game_rules=rules,game_mode='tdm',auto_balance=True,balance_mid_match=True,balance_threshold=2,balance_grace_seconds=0.,balance_bot_wait_seconds=10.,balance_player_cooldown=600.,team_change_cooldown=5.)
 native_map=vxl.VXL(-1,b'',0,2);native_world=world.World(native_map)
 mode=N(started=True,ended=False,retiring=False,auto_balance_enabled=True,vips={},patient_zero_ids=set(),last_survivor_id=None,allows_team_change=lambda p,t:p.id not in denied,bot_retire_safe=lambda p:p.id not in retire_denied)
 server=N(config=config,mode=mode,players={},connections={},teams={i:Team(i,'Team',(18,52,86))for i in (2,3)},world_manager=N(world=native_world),round_lifecycle=N(remove_owned_deployables=lambda p:log.append(['cleanup',p.id,p.team])),broadcast=lambda *a,**k:None,match_transition=N(in_progress=False))
 server.queue_mode_event=lambda name,*args:log.append(['change',args[0].id,args[1],args[2]]) if name=='on_player_team_change' else None
 bots=N(_started=True,_reconnect_count=None,bots=[]);server.bots=bots;add_failure=[False];remove_false=set()
 def add(i,t,alive=False,bot=False):
  conn=N(server=server,in_game=True,known_player_lives={},known_player_deaths={},known_corpse_cleanups={});p=Player(i,'P'+str(i),t,6,conn);conn.player=p;conn.send=lambda data,*a,**k:direct.append([i,bytes(data).hex()]);p.apply_class_selection(normalize_server_selection(config,0));p.spawn(36,36,97);server.players[i]=p;server.connections[i]=conn;server.teams[t].add_player(p)
  if not alive:p.die(kill_type=0)
  p.is_bot=bot
  if bot:bots.bots.append(p)
  return p
 async def remove(p):
  log.append(['remove',p.id]);await asyncio.sleep(0)
  if p.id in remove_false:return False
  bots.bots.remove(p);server.teams[p.team].remove_player(p);server.players.pop(p.id);return True
 async def replace(team):
  log.append(['add',team]);await asyncio.sleep(0)
  if add_failure[0]:raise RuntimeError('controlled replacement')
  add(max(server.players,default=0)+1,team,True,True)
 bots._safe_to_retire=lambda p:p.id not in director_denied;bots.remove_bot=remove;bots.add_bot=replace
 for i in range(5):add(i,3 if i==4 else 2)
 log.clear();direct.clear();balancer=TeamBalancer(server);steps=[]
 for action in actions:
  op=action[0];result=None
  if op=='tick':clock[0]=float(action[1]);result=await balancer.tick(clock[0])
  elif op=='force':result=await balancer.force_balance()
  elif op=='config':setattr(config,action[1],action[2])
  elif op=='mode':setattr(mode,action[1],action[2])
  elif op=='assign':mode.prepare_join_team=lambda t:t
  elif op=='transition':server.match_transition.in_progress=action[1]
  elif op=='bot':
   p=server.players[action[1]];p.is_bot=True;bots.bots.append(p)
  elif op=='alive':server.players[action[1]].spawn(36,36,97)
  elif op=='dead':server.players[action[1]].die(kill_type=0)
  elif op=='deny':denied.add(action[1])
  elif op=='retire_deny':retire_denied.add(action[1])
  elif op=='director_deny':director_denied.add(action[1])
  elif op=='remove_false':remove_false.add(action[1])
  elif op=='add_failure':add_failure[0]=True
  elif op=='reconnecting':bots._reconnect_count=3
  elif op=='director_stopped':bots._started=False
  elif op=='loading':server.players[action[1]].connection.in_game=False
  elif op=='vip':mode.vips[action[1]]=server.players[action[1]]
  elif op=='zero':mode.patient_zero_ids.add(action[1])
  elif op=='last':mode.last_survivor_id=action[1]
  elif op=='pickup':server.players[action[1]].pickup_id=7
  elif op=='score':server.players[action[1]].score=action[2];server.players[action[1]].captures=action[3]
  elif op=='moved':server.players[action[1]]._balance_moved_at=float(action[2])
  elif op=='new':add(action[1],action[2],action[3])
  elif op=='replace':
   old=server.players.pop(action[1]);server.teams[old.team].remove_player(old);add(action[1],action[2],False)
  elif op=='new_balancer':balancer=TeamBalancer(server)
  else:raise AssertionError(action)
  steps.append(dict(action=action,result=result,players=[[p.id,p.team,p.alive,getattr(p,'_balance_moved_at',None),balancer.joined_at(p)]for p in server.players.values()],moves=[balancer.bot_moves,balancer.human_moves],log=list(log),direct=list(direct)))
 rows.append(dict(actions=steps))
async def main():
 cases=[
 [['tick',100],['tick',100.5],['tick',101]],
 [['config','balance_grace_seconds',5],['tick',100],['tick',104],['tick',105.1]],
 [['alive',0],['alive',1],['alive',2],['alive',3],['tick',100],['dead',2],['tick',101]],
 [['vip',3],['pickup',2],['deny',1],['tick',100]],
 [['zero',3],['last',2],['retire_deny',1],['loading',0],['tick',100]],
 [['config','auto_balance',False],['tick',100],['force']],
 [['config','balance_mid_match',False],['tick',100],['force']],
 [['assign'],['tick',100],['force']],
 [['mode','ended',True],['tick',100],['force']],
 [['transition',True],['tick',100],['force'],['transition',False],['tick',101]],
 [['bot',0],['bot',2],['tick',100]],
 [['bot',0],['alive',0],['tick',100],['tick',109],['tick',110]],
 [['bot',0],['alive',0],['add_failure'],['tick',100],['tick',110]],
 [['bot',0],['alive',0],['director_deny',0],['tick',100],['tick',110]],
 [['bot',0],['alive',0],['remove_false',0],['tick',100],['tick',110]],
 [['bot',0],['reconnecting'],['tick',100]],
 [['bot',0],['director_stopped'],['tick',100]],
 [['score',0,-5,1],['score',1,-4,0],['score',2,50,0],['score',3,60,0],['tick',100]],
 [['config','auto_balance',False],['tick',100],['replace',3,2],['config','auto_balance',True],['tick',101]],
 [['moved',0,100],['moved',1,100],['moved',2,100],['moved',3,100],['tick',100],['new_balancer'],['tick',101],['force']],
 [['moved',0,101],['moved',1,101],['moved',2,101],['moved',3,101],['force']],
 [['config','balance_threshold',10],['tick',100],['force']],
 [['bot',0],['bot',1],['alive',0],['alive',1],['remove_false',0],['force']],
 [['config','auto_balance',False],['tick',100],['new',5,2,False],['score',5,-1,0],['config','auto_balance',True],['tick',101]]]
 for actions in cases:await balance_case(actions)
asyncio.run(main())
time.monotonic,time.time=original
# Actual audio helpers, including native wire bytes, all delivery routes and hearing order.
wire=[];hearing=[];players={}
for i,team,settled,connected in [(0,2,True,True),(1,2,False,True),(2,3,True,True),(3,0,True,True),(4,2,True,False)]:
 p=N(id=i,team=team,connection=N(in_game=settled)if connected else None);p.send=lambda data,reliable=True,i=i:wire.append(['direct',i,reliable,bytes(data).hex()]);
 if connected:p.connection.send=p.send
 players[i]=p
server=N(players=players,broadcast=lambda data,exclude=None,reliable=True:wire.append(['broadcast',-1 if exclude is None else exclude.id,reliable,bytes(data).hex()]),bot_stimuli=N(publish=lambda kind,pos,**kw:hearing.append([kind.name,list(pos),kw['source_id'],kw['team'],kw['lifetime']])))
audio_steps=[]
actions=[['play',33,.5,[10.25,-2,62],1.25,0,False,0],['play',2,1,None,1,None,True,0],['personal',1,3,1,None,1,True],['personal',4,2,1,None,1,False],['personal',0,13,.25,[1,2,3],2,False],['team',2,4,None],['team',2,5,0],['team',0,6,None],['relative',3,8,7,.75],['relative',0,2,3,1],['stop',255],['stop_to',1,0]]
for a in actions:
 result=None
 if a[0]=='play':audio.play_sound(server,a[1],volume=a[2],position=a[3],attenuation=a[4],exclude=None if a[5]is None else players[a[5]],reliable=a[6],source=None if a[7]is None else players[a[7]])
 elif a[0]=='personal':audio.play_sound_to(players[a[1]],a[2],volume=a[3],position=a[4],attenuation=a[5],reliable=a[6])
 elif a[0]=='team':result=audio.play_sound_to_team(server,a[1],a[2],exclude=None if a[3]is None else players[a[3]])
 elif a[0]=='relative':audio.play_team_relative(server,a[1],good=a[2],bad=a[3],volume=a[4])
 elif a[0]=='stop':audio.stop_sound(server,a[1])
 elif a[0]=='stop_to':audio.stop_sound_to(players[a[1]],a[2])
 audio_steps.append(dict(action=a,result=result,wire=list(wire),hearing=list(hearing)))
from server.game_constants import WATER_LEVEL
choices=dict(water_level=WATER_LEVEL,skyboxes=[[s,audio.airstrike_flyby_sound(N(world_manager=N(map_metadata=N(skybox_name=s))))]for s in [None,'LunarBase.txt','User_Lunar.txt','lunarbase.txt','Rural.txt']],explosions=[[z,audio.explosion_sound((0,0,z),18,17)]for z in [None,-1,WATER_LEVEL,WATER_LEVEL+.001,240]])
out=ROOT/'java/src/test/resources/golden/gameplay-services.json';out.write_text(json.dumps(dict(balance=rows,audio=audio_steps,choices=choices),separators=(',',':'))+'\n')
files=['server/team_balance.py','server/handlers/team.py','server/audio.py','server/main.py','server/player.py']
(out.parent/'gameplay-services-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',balance_cases=len(rows),audio_steps=len(audio_steps),fixture_sha256=hashlib.sha256(out.read_bytes()).hexdigest(),policy_method_substitutions=False,source_files_sha256={f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest()for f in files},scope=__doc__.strip()),sort_keys=True,indent=2)+'\n')
print('Gameplay services oracle:',len(rows),'balance traces,',len(audio_steps),'audio steps')
