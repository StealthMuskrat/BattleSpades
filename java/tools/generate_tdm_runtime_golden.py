"""Actual original TDM, serial mode-event dispatch and tick-start packet queue.

Uses the real server methods (including their helper methods), native packets,
real Team and ServerConfig. Only actor/config/clock and terminal IO are controlled.
This generator needs the pinned development toml package; it does not use -S.
"""
import asyncio,collections,gzip,hashlib,io,json,logging,pathlib,struct,types
from types import SimpleNamespace as N
from unittest.mock import patch
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server.main import BattleSpadesServer as Server
from server.simulation_runtime import SimulationRuntime
from server.config import ServerConfig
from server.team import Team
from server.game_rules import GameRules
from modes.tdm import TDMMode
from server.combat_scores import RoundCombatStats
from server import audio
import shared.constants as C
logging.disable(logging.CRITICAL)
bits=lambda v:struct.pack('>d',float(v)).hex()
rows=[]
def write(domain,trace,step,op,arg,result,state,log):rows.append((domain,trace,step,op,arg,result,state,'|'.join(log) or '-'))
# The production class methods are imported intact, not copied from source or replaced.
def server_methods(s):
 for name in ['_pending_packet_counts','_per_connection_packet_cap','_queue_ingame_packet','queue_mode_event']:
  setattr(s,name,types.MethodType(getattr(Server,name),s))
async def packet_cases(trace):
 log=[];capacity=[1,4,8,16][trace%4];share=min(capacity,1+trace%4);budget=1+trace%5
 s=N(config=N(max_pending_packets=capacity,max_pending_packets_per_connection=share,packet_drain_budget=budget),_pending_ingame_packets=collections.deque(),_pending_ingame_counts={},_dropped_ingame_packets=0,metrics=N(dropped_ingame_packets=0),connections={},players={});server_methods(s)
 conns=[]
 for i in range(3):
  c=N(peer=i,player=N(id=i));conns.append(c);s.connections[i]=c;s.players[i]=c.player
  async def receive(data,c=c,i=i):
   v=data[0];log.append(f'R:{i}:{v}')
   if v%7==0:raise RuntimeError('controlled packet IO fault')
   if v%11==0:s.connections.pop(i,None)
   if v%13==0:s.players[i]=N(id=i)
   if v%17==0:s._queue_ingame_packet(c,b'\xff')
   await asyncio.sleep(0);log.append(f'A:{i}:{v}')
  c.on_receive=receive
 def state():
  queue=','.join(f'{c.peer}:{d.hex()}' for c,d in s._pending_ingame_packets) or '-'
  counts=','.join(str(s._pending_ingame_counts.get(id(c),0)) for c in conns)
  live=''.join(str(int(s.connections.get(c.peer) is c and s.players.get(c.player.id) is c.player)) for c in conns)
  return f'{queue};{counts};{live};{s._dropped_ingame_packets}'
 for step in range(128):
  op='offer' if step%12<8 else ['drain','restore','forget','clear'][step%12-8];i=(step//3+trace)%3;value=(step*17+trace*19)%256;arg=f'{i},{value}';result='-';log.clear()
  if op=='offer':result=str(int(s._queue_ingame_packet(conns[i],bytes([value]))))
  elif op=='drain':await Server._drain_ingame_packets(s)
  elif op=='restore':s.connections[i]=conns[i];s.players[i]=conns[i].player
  elif op=='forget':
   c=conns[i];s._pending_ingame_packets=collections.deque((peer,data) for peer,data in s._pending_ingame_packets if peer is not c);s._pending_ingame_counts.pop(id(c),None);s.connections.pop(i,None)
  else:s._pending_ingame_packets.clear();s._pending_ingame_counts.clear()
  write('packets',trace,step,op,arg,result,state(),log)
async def mode_cases(trace):
 log=[];capacity=4+trace%5;budget=1+trace%4;s=N(config=N(mode_event_queue_limit=capacity,mode_event_drain_budget=budget),_mode_events=collections.deque(),metrics=N(dropped_mode_events=0,subsystem_failures={}),mode=None);server_methods(s);modes=[]
 for index in range(2):
  m=N(ended=False,_timeout_events_remaining=None);modes.append(m)
  for name in ['work','fault','append','swap','clear']:
   async def handler(arg,name=name,index=index):
    log.append(f'H:{index}:{name}:{arg}')
    if name=='fault':raise RuntimeError('controlled mode handler fault')
    if name=='append':s.queue_mode_event('work',arg+1000)
    if name=='swap':s.mode=modes[1-index]
    if name=='clear':s._mode_events.clear()
    await asyncio.sleep(0);log.append(f'A:{index}:{name}:{arg}')
   setattr(m,name,handler)
  async def tick(loop,index=index):
   log.append(f'T:{index}:{loop}')
   if loop%37==0:raise RuntimeError('controlled on_tick fault')
  m.on_tick=tick
 async def plugin(name,arg):
  log.append(f'P:{name}:{arg}');await asyncio.sleep(0)
  if arg%11==0:raise RuntimeError('controlled plugin fault')
  if arg%17==0:s.queue_mode_event('work',arg+2000)
 s.plugin_manager=N(call_event=plugin);s.mode=modes[0];runtime=SimulationRuntime(s)
 def state():
  queue=','.join(f'{n}:{a[0]}' for n,a in s._mode_events) or '-';active=-1 if s.mode is None else next(i for i,m in enumerate(modes) if m is s.mode)
  clocks=','.join(f'{int(m.ended)}:{m._timeout_events_remaining if m._timeout_events_remaining is not None else "-"}' for m in modes)
  failures=','.join(f'{k}:{v}' for k,v in sorted(s.metrics.subsystem_failures.items())) or '-'
  return f'{queue};{active};{clocks};{s.metrics.dropped_mode_events};{failures}'
 for step in range(128):
  op='offer' if step%11<7 else ['tick','cutoff','select','reset'][step%11-7];arg=f'{step},{trace}';result='-';log.clear()
  if op=='offer':
   name=['work','fault','missing','append','swap','clear'][(step//2+trace)%6];arg=f'{name},{step}';s.queue_mode_event(name,step)
  elif op=='tick':
   s.loop_count=step
   try:await runtime._tick_mode()
   except RuntimeError:result='RuntimeError'
  elif op=='cutoff':
   for i,m in enumerate(modes):m._timeout_events_remaining=(step+trace+i)%5;m.ended=(step+trace+i)%3==0
  elif op=='select':s.mode=[modes[0],modes[1],None][(step//11+trace)%3]
  else:
   for m in modes:m.ended=False;m._timeout_events_remaining=None
  write('events',trace,step,op,arg,result,state(),log)
async def tdm_cases(trace):
 log=[];fault=[False];peer_fault=[False]
 def broadcast(data,**kw):
  if data[0] in (85,50,72):
   log.append('B:'+bytes(data).hex())
   if fault[0]:raise RuntimeError('controlled score/HUD send fault')
 config=ServerConfig();config.default_mode='tdm';config.mode_settings={'tdm':dict(score_limit=[0,1,3,200][trace%4],time_limit=[0.,3.,90.,900.][trace%4],kill_points=[-1,0,1,3][trace%4],headshot_bonus=trace%3)};config.mode_start_music=False;config.end_round_scoreboard=trace%2==0
 s=N(config=config,players={},teams={2:Team(2,'Blue',(17,29,251)),3:Team(3,'Green',(4,5,6))},broadcast=broadcast,_stopping=True,_mode_events=collections.deque(),map_resources=N(rebuild=lambda:log.append('R')),revival_master=N(reset_scoreboard_baselines=lambda:log.append('Z')),achievements=N(active=True,match_started=lambda:log.append('AS'),match_ended=lambda winner:log.append('AE:'+str(winner))),vote_manager=N(ensure_round_end_map_vote=lambda wall:log.append('VE:'+bits(wall)),ensure_map_vote=lambda wall:log.append('V:'+bits(wall))))
 actors=[]
 for i,team in enumerate([2,3,2,0]):
  def send(data,**kw):
   log.append('P'+str(i)+':'+bytes(data).hex())
  p=N(id=i,team=team,score=10+i,kills=0,deaths=0,captures=0,kill_streak=0,is_bot=False,alive=True,spawned=True,position=(36.,36.,97.),health=100,tool=6,class_id=0,died_reloading=False,death_penalty_exempt=False,kill_bonuses={},damage_contributions={},round_combat_stats=RoundCombatStats())
  def personal(data,p=p,**kw):
   if p.connection is None:return
   log.append('P'+str(p.id)+':'+bytes(data).hex())
   if peer_fault[0]:raise RuntimeError('controlled personal send fault')
  p.send=personal;p.connection=N(server=s,in_game=True,send=personal);actors.append(p);s.players[i]=p
 mode=TDMMode(s);mode.escape_watch_enabled=False;s.mode=mode
 def state():
  scores=','.join(str(p.score) for p in actors);teams=','.join(f'{t.score}:{t.captures}:{t.kills}:{t.spawn_index}' for t in s.teams.values())
  return ';'.join([scores,teams,str(int(mode.started)),str(int(mode.ended)),str(int(mode.retiring)),str(mode.winner),bits(mode.start_time),bits(mode.elapsed_time),str(mode._timeout_events_remaining),str(int(mode._end_sequence_running))])
 for step in range(128):
  op=['start','kill','kill','death','kill','retire','kill','time','start','kill','reveal','score','deactivate','tick'][step%14];kind=[0,int(C.HEADSHOT_KILL),int(C.MELEE_KILL),int(C.FALL_KILL),int(C.CLASS_CHANGE_KILL)][(step+trace)%5];ki=(step//14+trace)%4;vi=(step//7+trace+1)%4;flag=(step+trace)%2==0;now=100.+step*1.125
  for i,p in enumerate(actors):
   p.is_bot=(step+i+trace)%17==0;p.died_reloading=(step+i)%4==0;p.death_penalty_exempt=(step+i)%23==0
   # Connection presence and in-game gate are distinct source predicates.
   if p.connection is None:p.connection=N(server=s,in_game=True,send=p.send)
   p.connection.in_game=(step+i)%13!=0
   if (step+i+trace)%19==0:p.connection=None
  for i,t in enumerate(s.teams.values()):t.locked_score=(step+i)%5==0
  fault[0]=step%37==0;peer_fault[0]=step%41==0;log.clear();result='-';arg=f'{ki},{vi},{kind},{int(flag)}'
  try:
   with patch('time.time',return_value=now),patch('time.monotonic',return_value=now):
    if op=='start':await mode.on_mode_start()
    elif op=='kill':await mode.on_player_kill(actors[ki],actors[vi],kind)
    elif op=='death':await mode.on_player_death(actors[vi],actors[ki] if flag else None,kind)
    elif op=='retire':mode.begin_retirement(end_round=flag)
    elif op=='time':await mode._end_by_time()
    elif op=='score':await mode._end_by_score([2,3][ki%2])
    elif op=='deactivate':await mode.deactivate()
    elif op=='reveal':mode.reveal_to(actors[vi].connection or N(send=lambda data,**kw:log.append('P'+str(vi)+':'+bytes(data).hex())))
    else:await mode.on_tick(step)
  except RuntimeError:result='RuntimeError'
  write('tdm',trace,step,op,arg,result,state(),log)
async def main():
 for trace in range(16):await packet_cases(trace);await mode_cases(trace);await tdm_cases(trace)
asyncio.run(main())
# Real mode-rule/time precedence, with a generic CTF-era score_limit deliberately different.
for case in range(64):
 c=ServerConfig();c.default_mode='tdm';c.score_limit=case+1000;c.mode_settings={'tdm':{}};c.game_rules=GameRules.server_defaults();target=[5,10,15,20,50,100,200][case%7];c.game_rules.apply({'RULE_TDM_SCORE_TARGET':target})
 if case%2:c.mode_settings['tdm']['score_limit']=case-3
 if case%3==0:c.match_length_minutes=15
 if case%5==0:c.mode_settings['tdm']['time_limit']=case-10
 if case%7==0:c.mode_settings['tdm']['kill_points']=case-2
 if case%11==0:c.mode_settings['tdm']['headshot_bonus']=case-1
 m=TDMMode(N(config=c));r=m.score_limit,m.time_limit,m.kill_points,m.headshot_bonus
 rows.append(('settings',case,json.dumps(c.mode_settings['tdm'],sort_keys=True),c.match_length_minutes if c.match_length_minutes is not None else '-',target,*r))
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buf=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buf,mtime=0) as stream:stream.write(raw)
p=OUT/'tdm-runtime.tsv.gz';p.write_bytes(buf.getvalue())
catalog=ROOT/'java/src/main/resources/net/battlespades/catalog/tdm-runtime.toml';catalog.write_text(f'killPoints = {TDMMode.kill_points}\npositiveSound = {audio.SND_EVENT_POSITIVE}\nnegativeSound = {audio.SND_EVENT_NEGATIVE}\n')
files=['modes/tdm.py','modes/base_mode.py','server/main.py','server/simulation_runtime.py','server/config.py','server/player.py','server/team.py','server/scoreboard.py','server/combat_scores.py','server/profile_stats.py','server/audio.py','shared/packet.pyx']
(OUT/'tdm-runtime-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',observations=dict(collections.Counter(r[0] for r in rows)),fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),catalog_sha256=hashlib.sha256(catalog.read_bytes()).hexdigest(),source_files_sha256={f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest() for f in files},method_substitutions=False,scope='Actual unchanged server packet queue/drain helpers, SimulationRuntime._tick_mode, TDM/BaseMode/Team/config and native score/HUD/personal sound bytes. Controlled async handlers/plugins, actor/config/clock inputs, native audio filtering, achievement/resource/vote/result terminal IO and send faults. Delayed end/restart task body is deliberately suppressed with stopping=True and remains a required service; no complete audio/achievement/transition implementation claimed.'),indent=2)+'\n')
print('TDM/runtime',collections.Counter(r[0] for r in rows),'bytes',p.stat().st_size)
