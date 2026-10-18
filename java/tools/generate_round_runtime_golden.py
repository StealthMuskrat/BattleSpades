"""Original disconnect reconciliation and delayed round ending/restart policy.

Policy methods are intact. Controlled actors, virtual mode-start hook, asynchronous
mode/plugin hooks, vote/transition/timer services and terminal audio/score IO.
"""
import asyncio,collections,hashlib,json,logging,pathlib,types
from types import SimpleNamespace as N
from unittest.mock import patch
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from server.main import BattleSpadesServer as Server
from modes.base_mode import BaseMode
from server.team import Team
logging.disable(logging.CRITICAL)
rows=[];sleep=asyncio.sleep
async def leave_case(case):
 log=[];gates=[];actors=[N(id=0),N(id=1),N(id=0)];s=N(_mode_events=collections.deque(),_connection_tasks=set());modes=[]
 s._LEAVER_REACQUIRE_EVENTS=Server._LEAVER_REACQUIRE_EVENTS;s._LEAVER_SETTLE_EVENTS=Server._LEAVER_SETTLE_EVENTS
 for i in range(2):
  m=N()
  for name in ['on_player_death','on_player_kill','on_player_leave']:
   async def hook(*args,i=i,name=name):
    log.append(f'M{i}:{name}')
    if name=='on_player_death' and case%3==0:s.mode=modes[1]
    if case%5==0 and name=='on_player_kill':raise RuntimeError('mode fault')
    if case%2:
     gate=asyncio.get_running_loop().create_future();gates.append(gate);await gate;log.append(f'A{i}:{name}')
   setattr(m,name,hook)
  modes.append(m)
 async def plugin(name,*args):
  log.append('P:'+name)
  if case%7==0 and name=='on_player_death':raise RuntimeError('plugin fault')
  if case%2:
   gate=asyncio.get_running_loop().create_future();gates.append(gate);await gate;log.append('AP:'+name)
 s.mode=modes[0];s.plugin_manager=N(call_event=plugin);s._drive_hook_now=types.MethodType(Server._drive_hook_now,s)
 names=['on_player_spawn','on_player_team_change','on_player_death','on_player_kill','block']
 for k in range(10):
  name=names[k%5];args=(actors[(k+case)%3],actors[(k//2+case)%3],actors[0]);s._mode_events.append((name,args))
 def queued():return '|'.join(name+':'+','.join(str(actors.index(a)) if a is not actors[2] else '2' for a in args) for name,args in s._mode_events)
 Server._run_player_leave_hooks(s,actors[0]);prefix=list(log);pending=len(s._connection_tasks);queue=queued()
 for gate in gates:gate.set_result(None)
 if s._connection_tasks:await asyncio.gather(*list(s._connection_tasks))
 await sleep(0)
 rows.append(dict(domain='leave',case=case,prefix=prefix,suffix=sorted(log[len(prefix):]),queue=queue,pending=pending,final_pending=len(s._connection_tasks)))
async def end_case(case):
 log=[];kind=case%3;facade=(case//3)%3;fault=(case//9)%4;scoreboard=case%2==0;dwell=[-5,0,7,130][case%4];recoverable=case%7!=0;busy=case%11==0;bad_restart=case%13==0
 if case>=36:facade=1;dwell=float('nan') if case==39 else 7
 current=['Straße','arena','Οσ','current'][case-36] if case>=36 else 'arena'
 target=['STRASSE','','ος','different'][case-36] if case>=36 else [None,'Arena','Other'][kind]
 config=N(default_map=current,end_screen_seconds=dwell,end_round_scoreboard=scoreboard,end_round_headline=case%5!=0)
 s=N(config=config,_stopping=False,teams={2:Team(2,'Blue',(0,0,255)),3:Team(3,'Green',(0,255,0))},players={});m=N(server=s,ended=True,_end_sequence_running=True,SCORES_SCREEN_SECONDS=10)
 for name in ['_end_round_scoreboard_enabled','_end_round_headline_enabled','_end_screen_seconds','_restart_round','_recover_failed_end_sequence']:
  setattr(m,name,types.MethodType(getattr(BaseMode,name),m))
 async def start():log.append('start');m.ended=False
 m.on_mode_start=start;m.end_message_id=lambda winner:4
 s.mode=m if recoverable else object()
 async def prepare():log.append('prepare')
 async def reset_bots():log.append('reset-bots')
 s.bots=N(on_match_phase=lambda phase:log.append('bots:'+phase),prepare_for_game_transition=prepare,reset_after_round_restart=reset_bots)
 def reset():
  log.append('reset')
  if bad_restart:raise RuntimeError('reset failed')
 s.reset_round_runtime=reset
 async def wait():log.append('vote-wait');await sleep(0)
 def consume():log.append('consume');return target
 s.vote_manager=N(wait_for_map_result=wait,consume_next_map=consume)
 def outcome():
  if fault==3:raise RuntimeError('transition failed')
  return N(ok=fault==0,reconnect_required=fault==2,message='transition failed')
 async def change(name,**kw):
  log.append('change:'+name+(':'+str(int(kw['end_screen_seconds']))+':'+('4' if kw['headline_message_id'] is not None else '-') if kw else ''));await sleep(0);return outcome()
 async def restart():log.append('transition-restart');return N(ok=not bad_restart,message='restart failed')
 async def timer(seconds):log.append('sleep:'+str(int(seconds)));await sleep(0)
 if facade:
  transition=N(change_map=change,restart_round=restart,_transition_busy=lambda **kw:busy)
  if facade==1:transition.change_map_after_end_screen=change
  s.match_transition=transition
 with patch('asyncio.sleep',timer),patch('server.audio.play_ending_music',lambda server:log.append('music')),patch('server.scoreboard.force_show_scores',lambda server,forced:log.append('scores:'+str(int(forced)))),patch('server.scoreboard.broadcast_game_stats',lambda server,winner:log.append('stats:'+str(winner))),patch('server.scoreboard.send_team_score',lambda server,team:log.append('team:'+str(team.id))):
  await BaseMode._end_sequence_task(m,2)
 rows.append(dict(domain='end',case=case,log=log,ended=m.ended,sequence=m._end_sequence_running))
async def main():
 for case in range(16):await leave_case(case)
 for case in range(40):await end_case(case)
asyncio.run(main())
p=ROOT/'java/src/test/resources/golden/round-runtime.json';p.write_text(json.dumps(dict(cases=rows),separators=(',',':'))+'\n')
files=['server/main.py','modes/base_mode.py','server/team.py','server/simulation_runtime.py']
(p.parent/'round-runtime-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',observations=dict(collections.Counter(r['domain'] for r in rows)),fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),source_files_sha256={f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest() for f in files},policy_method_substitutions=False,scope=__doc__.strip(),independent_remainders='Prefix order is strict; post-suspension suffixes are sorted because disconnect hooks resume independently.'),indent=2)+'\n')
print('Round runtime',len(rows),'source cases',p.stat().st_size,'bytes')
