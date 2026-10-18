"""Intact Arena/BaseMode lifecycle with native Player, forced deaths and RoundLifecycle respawns.
Only clocks, external world spawn proposals, IO/terminal services and failure boundaries are controlled.
The late-join oracle calls intact server._send_join_audio and BaseMode.join_music_track with seeded randomness.
"""
import asyncio,collections,hashlib,json,logging,pathlib,random,time
from types import SimpleNamespace as N,MethodType
from unittest.mock import patch
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from aoslib import vxl,world
from server import audio
from server.player import Player
from server.team import Team
from server.game_rules import GameRules
from server.class_selection import normalize_server_selection
from server.round_lifecycle import RoundLifecycle
from server.main import BattleSpadesServer as Server
from modes.arena import ArenaMode
from server.map_metadata import MapAmbientSound
logging.disable(logging.CRITICAL);rows=[];clock=[1000.,100.]
async def arena_case(case,actions,overlay):
 wire=[];effects=[];respawned=[];failures=set();serial=[0]
 rules=GameRules.server_defaults();config=N(game_rules=rules,game_mode='arena',default_mode='arena',respawn_time=1.5,mode_start_music=False,mode_settings={'arena':overlay},end_round_scoreboard=True)
 native_map=vxl.VXL(-1,b'',0,2);native_world=world.World(native_map)
 s=N(config=config,players={},connections={},teams={2:Team(2,'Blue',(18,52,86)),3:Team(3,'Green',(50,60,70))},world_manager=N(world=native_world,get_spawn_point=lambda t:(36.,36.,97.)),tick_rate=60,_stopping=True,_mode_events=collections.deque(),map_resources=N(rebuild=lambda:effects.append('resources')),revival_master=N(reset_scoreboard_baselines=lambda:effects.append('baseline')),achievements=N(active=True,died=lambda *args:None,match_started=lambda:effects.append('start'),match_ended=lambda winner:effects.append('end:'+str(winner))),vote_manager=N(ensure_round_end_map_vote=lambda now:effects.append('vote-end'),ensure_map_vote=lambda now:effects.append('vote')))
 s.broadcast=lambda data,**kw:wire.append(['B',None,bytes(data).hex()]);s.queue_mode_event=lambda *args:None;s._broadcast_create_player=MethodType(Server._broadcast_create_player,s);lifecycle=RoundLifecycle(s);s.round_lifecycle=lifecycle
 def respawn(p):
  respawned.append(p.id)
  if p.id in failures:effects.append('respawn-failure:'+str(p.id));raise RuntimeError('controlled respawn')
  lifecycle.respawn_player(p)
 s.respawn_player=respawn;mode=ArenaMode(s);s.mode=mode;mode.escape_watch_enabled=False
 def add(i,t):
  conn=N(server=s,in_game=True,known_player_lives={},known_player_deaths={},known_corpse_cleanups={});p=Player(i,'P'+str(i),t,6,conn);conn.player=p;conn.send=lambda data,*args,**kw:wire.append(['P',i,bytes(data).hex()]);p.apply_class_selection(normalize_server_selection(config,0));p.token=serial[0];serial[0]+=1;s.players[i]=p;s.connections[i]=conn
  if t in (2,3):s.teams[t].add_player(p);p.spawn(36,36,97)
  return p
 for i,t in enumerate([2,3,2,0]):add(i,t)
 wire.clear();effects.clear();steps=[];departed={}
 for a in actions:
  result=None;op=a[0]
  if op=='start':await mode.on_mode_start()
  elif op=='time':clock[0]=float(a[1]);clock[1]=clock[0]-900
  elif op=='tick':await mode.on_tick(a[1])
  elif op=='death':
   p=s.players[a[1]];killer=None if a[2]is None else s.players[a[2]];p.die(killer=killer,kill_type=a[3]);await mode.on_player_death(p,killer,a[3])
   if killer is not None and killer is not p and killer.team!=p.team:await mode.on_player_kill(killer,p,a[3])
  elif op=='dead':s.players[a[1]].die(kill_type=0)
  elif op=='leave':
   p=s.players.pop(a[1]);s.teams[p.team].remove_player(p);departed[a[1]]=p;await mode.on_player_leave(p)
  elif op=='replace':
   p=s.players.pop(a[1]);s.teams[p.team].remove_player(p);departed[a[1]]=p;add(a[1],a[2])
  elif op=='old_leave':await mode.on_player_leave(departed[a[1]])
  elif op=='new':add(a[1],a[2])
  elif op=='respawns':await lifecycle.process_respawns()
  elif op=='fail_respawn':failures.add(a[1])
  elif op=='stage':s.players[a[1]].stage_class_selection(normalize_server_selection(config,a[2]))
  elif op=='retire':mode.begin_retirement(end_round=a[1])
  elif op=='deactivate':await mode.deactivate()
  elif op=='damage':result=mode.modify_incoming_damage(s.players[0],a[1],s.players[1],0)
  else:raise AssertionError(a)
  steps.append(dict(action=a,result=result,round=mode.current_round,wins=mode.round_wins.copy(),round_started=mode.round_started,round_ended=mode.round_ended,round_start=mode.round_start_time,countdown=mode.countdown_started,countdown_end=mode.countdown_end_time,next=mode.next_round_at,waiting=mode.waiting_for_players,fighters=[p.token for p in mode.alive_players],started=mode.started,ended=mode.ended,retiring=mode.retiring,winner=mode.winner,players=[[p.id,p.token,p.team,p.alive,p.spawned,p.score,mode.can_player_respawn(p),mode.respawn_time_for(p),mode.bot_retire_safe(p),p.class_id]for p in s.players.values()],teams=[t.score for t in s.teams.values()],wire=list(wire),effects=list(effects),respawned=list(respawned)))
 rows.append(dict(case=case,overlay=overlay,steps=steps))
async def main():
 cases=[
 ([['start'],['damage',25],['time',1004],['tick',60],['time',1005],['tick',61],['damage',25],['time',1015],['tick',62],['damage',25],['time',1020],['tick',63]],{}),
 ([['leave',1],['start'],['time',1006],['tick',60],['time',1010],['tick',120],['new',4,3],['time',1011],['tick',121],['time',1015],['tick',180],['time',1016],['tick',181]],{}),
 ([['start'],['time',1005],['tick',60],['death',0,1,0],['respawns'],['death',2,1,0],['time',1009],['respawns'],['time',1010],['tick',61],['respawns']],{}),
 ([['start'],['time',1005],['tick',60],['leave',2],['replace',0,2],['old_leave',0],['time',1010],['tick',61]],{}),
 ([['start'],['time',1005],['tick',60],['dead',0],['dead',2],['time',1015],['tick',120],['time',1020],['tick',121]],{}),
 ([['start'],['time',1005],['tick',60],['dead',0],['dead',1],['dead',2],['time',1015],['tick',120]],{}),
 ([['start'],['time',1003],['tick',60]],{'time_limit':3}),
 ([['start'],['time',1005],['tick',60],['death',0,1,1],['death',2,1,0],['time',1010],['tick',120],['start']],{'rounds_to_win':1}),
 ([['start'],['time',1005],['tick',60],['death',1,0,0],['stage',1,3],['time',1010],['tick',61],['time',1015],['tick',120],['death',1,0,0]],{'rounds_to_win':2}),
 ([['start'],['time',1005],['tick',60],['death',1,0,0],['fail_respawn',1],['time',1010],['tick',61]],{}),
 ([['start'],['time',1005],['tick',60],['death',0,0,4],['death',2,1,0]],{}),
 ([['start'],['time',1005],['tick',60],['retire',True],['leave',1],['time',1010],['tick',61],['deactivate']],{}),
 ([['start'],['time',1005],['tick',60],['new',4,2],['death',0,1,0],['death',2,1,0]],{}),
 ([['start'],['time',1005],['tick',60],['time',1500],['respawns'],['tick',120]],{'round_time_limit':300})]
 for i,(a,o)in enumerate(cases):
  clock[:]=[1000.,100.]
  with patch.object(audio,'random',random.Random(0)):await arena_case(i,a,{'round_time_limit':10,**o})
with patch('time.time',side_effect=lambda:clock[0]),patch('time.monotonic',side_effect=lambda:clock[1]):asyncio.run(main())
# Intact join audio uses one shared seeded source random stream across mode choice and fallback.
audio_rows=[]
for seed in [0,7]:
 wire=[];flags=N(started=False,ended=False,_end_sequence_running=False,retiring=False,_timeout_music_played=False)
 from modes.base_mode import BaseMode
 flags.join_music_track=MethodType(BaseMode.join_music_track,flags)
 server=N(config=N(mode_start_music=True),mode=flags,world_manager=N(map_metadata=N(ambient_sounds=[MapAmbientSound('amb_rural')])));p=N(x=36.,y=36.,z=97.);conn=N(player=p,join_audio_sent=False);sent_count=[0];fail_at=[None]
 def send(data,**kw):
  sent_count[0]+=1;wire.append(bytes(data).hex())
  if sent_count[0]==fail_at[0]:raise RuntimeError('controlled audio send')
 conn.send=send;p.send=send;steps=[]
 actions=[['reveal'],['reveal'],['forget'],['flag','started',True],['flag','_timeout_music_played',True],['reveal'],['forget'],['flag','ended',True],['flag','_end_sequence_running',True],['bed',False],['reveal'],['forget'],['flag','retiring',True],['reveal'],['forget'],['flag','ended',False],['reveal'],['forget'],['flag','_timeout_music_played',False],['bed',True],['fail',6],['reveal'],['fail',None],['reveal']]
 state=random.getstate();random.seed(seed)
 try:
  for a in actions:
   if a[0]=='reveal':Server._send_join_audio(server,conn)
   elif a[0]=='forget':conn.join_audio_sent=False;sent_count[0]=0
   elif a[0]=='flag':setattr(flags,a[1],a[2])
   elif a[0]=='bed':server.config.mode_start_music=a[1]
   elif a[0]=='fail':fail_at[0]=a[1];sent_count[0]=0
   steps.append(dict(action=a,sent=conn.join_audio_sent,wire=list(wire)))
 finally:random.setstate(state)
 audio_rows.append(dict(seed=seed,steps=steps))
settings=[]
for overlay in [{},{'score_limit':3},{'rounds_to_win':2,'score_limit':7,'round_time_limit':.5,'time_limit':-1}]:
 m=ArenaMode(N(config=N(mode_settings={'arena':overlay})));settings.append(dict(input=overlay,values=[m.rounds_to_win,m.round_time_limit,m.time_limit]))
out=ROOT/'java/src/test/resources/golden/arena-rounds.json';out.write_text(json.dumps(dict(arena=rows,audio=audio_rows,settings=settings),separators=(',',':'))+'\n');files=['modes/arena.py','modes/base_mode.py','server/round_lifecycle.py','server/main.py','server/audio.py','server/player.py']
(out.parent/'arena-rounds-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',arena_cases=len(rows),audio_traces=len(audio_rows),fixture_sha256=hashlib.sha256(out.read_bytes()).hexdigest(),policy_method_substitutions=False,source_files_sha256={f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest()for f in files},scope=__doc__.strip()),sort_keys=True,indent=2)+'\n');print('Arena source oracle',len(rows),'round traces;',len(audio_rows),'seeded late-join traces')
