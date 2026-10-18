"""Unchanged BaseMode economy/clock, teabag and escape watch, original native world and packets."""
import asyncio,collections,gzip,hashlib,io,json,math,pathlib,struct
from types import SimpleNamespace as N
from unittest.mock import patch
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from modes.base_mode import BaseMode
from server import combat_scores as B,escape_watch as E,announcements as A
from server.game_rules import GameRules
from server.world_manager import WorldManager
from server.simulation_runtime import SimulationRuntime
from server.audio import TIMEOUT_MUSIC_SECONDS
from aoslib import vxl
from shared.packet import ChangePlayer,LocalisedMessage
from shared.bytes import ByteReader
import shared.constants as C
import shared.constants_gamemode as CG
bits=lambda v:struct.pack('>d',float(v)).hex();vec=lambda v:','.join(map(bits,v));hx=lambda s:s.encode().hex();rows=[]
fields={k:int(getattr(CG,n)) for k,n in dict(killScore='GENERIC_SCORE_KILL',headshotScore='GENERIC_SCORE_HEADSHOT',meleeScore='GENERIC_SCORE_MELEE',revengeScore='GENERIC_SCORE_REVENGE',paybackScore='GENERIC_SCORE_PAYBACK',reloadScore='GENERIC_SCORE_RELOAD',defendScore='GENERIC_SCORE_DEFEND',distractScore='TDM_SCORE_DISTRACT',suicideScore='GENERIC_SCORE_SUICIDE',teamkillScore='GENERIC_SCORE_TEAMKILL',teabagScore='NORMAL_SCORE_TEABAG',teabagClassicScore='CLASSIC_SCORE_TEABAG',voteLeadSeconds='TIME_AFTER_MAP_VOTE_START_BEFORE_END').items()}
fields.update({k:int(getattr(C,n)) for k,n in dict(killReason='KILL_SCORE_REASON',headshotReason='KILL_SCORE_HEADSHOT_REASON',meleeReason='KILL_SCORE_MELEE_REASON',revengeReason='KILL_SCORE_REVENGE_REASON',paybackReason='KILL_SCORE_PAYBACK_REASON',reloadReason='KILL_SCORE_RELOAD_REASON',defendReason='KILL_SCORE_DEFEND_REASON',distractReason='KILL_SCORE_DISTRACT_REASON',suicideReason='SUICIDE_SCORE_REASON',teamkillReason='KILL_SCORE_TEAMKILL_REASON',headshotType='HEADSHOT_KILL',meleeType='MELEE_KILL',airstrikeType='AIRSTRIKE_KILL',bombType='BOMB_KILL',defendAward='MOST_DEFENDS',distractAward='MOST_DISTRACTIONS',teabagAward='MOST_TEABAGS',teabagTotal='COMBAT_TEABAG_TOTAL',teabagClassicTotal='COMBAT_TEABAG_CLASSIC_TOTAL',teabagReason='TEABAG_SCORE_REASON',teabagPresses='TEABAG_CROUCH_COUNT',scoreHeadline='TEAM_SCORES_MESSAGE',drawHeadline='TEAM_SCORES_DRAW',firstHeadline='VIP_TEAM1_WIN_MESSAGE',secondHeadline='VIP_TEAM2_WIN_MESSAGE').items()})
lines=['# Original policy data; generated, not runtime Python.']+[f'{k} = {v}.0' if k=='voteLeadSeconds' else f'{k} = {v}' for k,v in fields.items()]+[f'teabagGap = {float(C.TEABAG_TIME_THRESHOLD)}',f'teabagDistance = {float(C.TEABAG_MAX_DISTANCE)}',f'timeoutMusicSeconds = {float(TIMEOUT_MUSIC_SECONDS)}',f'finalCountdown = {BaseMode.FINAL_COUNTDOWN_FROM}']
for t,s,p in BaseMode.COUNTDOWN_CUES:lines+=['[[cue]]',f'threshold = {t}',f'string = {json.dumps(s)}',f'parameters = {json.dumps(p)}']
catalog=ROOT/'java/src/main/resources/net/battlespades/catalog/match-flow.toml';catalog.write_text('\n'.join(lines)+'\n')
def collect(data,**kwargs):
 wire.append(bytes(data).hex())
 if fail:raise RuntimeError('terminal send fault')
class Config(N):
 def __getattribute__(self,name):
  if name=='game_rules' and vars(self).get('rule_fault',False):raise RuntimeError('controlled rule-service read fault')
  return super().__getattribute__(name)
def setup():
 server=N(players={},config=N(ugc_runtime=False,default_mode='tdm',mode_event_drain_budget=3,game_rules=GameRules.server_defaults()),broadcast=collect,_stopping=True,teams={})
 actors=[N(id=i,team=[2,2,3][i],alive=True,spawned=True,is_bot=False,position=(36.,36.,97.),x=36.,y=36.,z=97.,orientation=(1.,0.,0.),tool=6,class_id=0,health=100,score=0,kill_streak=1,kill_bonuses={},died_reloading=False,death_penalty_exempt=False,death_time=100.,damage_contributions={},round_combat_stats=B.RoundCombatStats(),connection=N(server=server,in_game=True)) for i in range(3)]
 old=actors[0];replacement=N(**vars(old));replacement.kill_bonuses={};replacement.round_combat_stats=B.RoundCombatStats();actors.append(replacement);server.players=dict(enumerate(actors[:3]));mode=BaseMode(server);server.mode=mode;return server,mode,actors

def state(actors):
 def actor(a):
  profile=getattr(a,'profile_stats',None);pv='-' if profile is None else ','.join(f'{k}:{n}:{s}' for k,(n,s) in profile.values.items())+'@'+str(profile.last_score)
  return ';'.join([str(a.score),str(int(a.death_penalty_exempt)),','.join(f'{k}:{int(v[0])}:{int(v[1])}' for k,v in a.kill_bonuses.items()) or '-',','.join(f'{k}:{v}' for k,v in a.round_combat_stats.awards.items()) or '-',pv])
 return '|'.join(actor(a) for a in actors)
for trace in range(16):
 wire=[];fail=False;server,mode,actors=setup();p,mate,victim,replacement=actors
 for step in range(128):
  op=step%3;mode.ended=step%19==0;mode.generic_scoring_enabled=step%7!=0;mode.GENERIC_TEAMPLAY_AWARDS=step%5!=0;server.config.default_mode=['tdm','zom','ugc','TUTORIAL'][(step//23+trace)%4];server.config.ugc_runtime=step%31==0;p.is_bot=step%17==0;p.score=(step*13)%301-70;p.kill_bonuses[victim.id]=(step%2==0,step%3==0);p.death_penalty_exempt=step%11==0;victim.died_reloading=step%4==0;mate.alive=step%9!=0;now=100.+step*.25;age=[0.,5.,5.001,-1.][(step+trace)%4];mate.damage_contributions={2:B.DamageContribution(victim,3,1,1.,now-age)} if step%6 else {};owner=(step//7+trace)%3;server.players.pop(0,None)
  if owner!=1:server.players[0]=p if owner==0 else replacement
  kind=[0,1,2,7,8,9,10,16,17][(step+trace)%9];killer=[p,victim,None,mate][(step//3+trace)%4];fail=step%29==0;result='-'
  try:
   with patch('time.monotonic',return_value=now):
    if op==0:result=str(mode.award_generic_kill_score(p,victim,kind,mode_opted_in=step%4==0))
    elif op==1:result=str(mode.apply_generic_death_penalty(p,killer,kind))
    else:result=str(mode._award_teamplay_kill_events(p,victim))
  except RuntimeError:result='RuntimeError'
  rows.append(('economy',trace,step,kind,owner,bits(age),int(fail),result,state(actors),','.join(wire)));wire.clear()
for trace in range(16):
 wire=[];fail=False;server,mode,actors=setup();server.config=Config(**vars(server.config));p,mate,victim,replacement=actors;now=100.
 for step in range(128):
  now+=[.1,.25,.5,.500001,1.,-.25][(step//3+trace)%6];mode.ended=step%37==0;mode.retiring=step%41==0;server.config.default_mode=['tdm','cctf','ugc','TUTORIAL'][trace%4] if step%19==0 else 'tdm';mode.mode_code='cctf' if (step//9+trace)%2 else 'tdm';mode.death_representation='corpse' if step%11==0 else 'grave';points=(step//7+trace)%2==0;server.config.rule_fault=False;server.config.game_rules=GameRules.server_defaults();server.config.game_rules.apply({'RULE_POINTS_FROM_TEABAGGING':points});server.config.rule_fault=step%27==0;p.alive=step%29!=0;p.is_bot=step%17==0;victim.alive=step%23==0;victim.death_time=100.+step//21;distance=[0.,1.75,math.nextafter(1.75,math.inf),2.][(step//5+trace)%4];p.position=(p.x,p.y,p.z)=(36.+distance,36.,97.);victim.position=(victim.x,victim.y,victim.z)=(36.,36.,97.+(3. if step%13 else 3.000001));fail=step%43==0;result='-'
  try:result=str(B.record_teabag_crouch(server,p,now=now))
  except RuntimeError:result='RuntimeError'
  presses=','.join(map(bits,getattr(p,'_teabag_crouch_times',()))) or '-';done=len(getattr(p,'_teabagged_deaths',()));rows.append(('teabag',trace,step,bits(now),vec(p.position),vec(victim.position),bits(victim.death_time),int(points),int(fail),result,presses,done,state(actors),','.join(wire)));wire.clear()
# Actual native VXL world, edits and complete escape-watch tracking.
for trace in range(16):
 wire=[];fail=False;server,unused,actors=setup();raw=(OUT/'world/empty.vxl').read_bytes();wm=WorldManager(N(game_rules=GameRules.server_defaults(),game_mode='tdm'));wm.map=vxl.VXL(None,raw,len(raw));server.world_manager=wm
 def mode_marks(p):
  if predicate_fault:raise RuntimeError('controlled marker observer fault')
  return marked
 def mode_objective(p):
  if objective_fault:raise RuntimeError('controlled objective observer fault')
  return objective
 modes=[N(escape_watch_enabled=True,mode_marks_player=mode_marks,escape_watch_objective_player=mode_objective) for _ in range(2)];server.mode=modes[0];p=actors[0];p.input=N(crouch=False);server.players={0:p};now=100.
 for x in range(35,40):
  for y in range(35,40):
   for z in range(95,101):
    if z in (95,100) or x in (35,39) or y in (35,39):wm.set_block(x,y,z,True,0x808080);rows.append(('edit',trace,x,y,z,1))
 for step in range(128):
  predicate_fault=step%17==0;objective_fault=step%19==0;now+=[.1,.5,1.,2.,-1.][(step+trace)%5];position=[(-.01,36.,97.),(512.,36.,97.),(36.,36.,239.),(36.,36.,238.65),(36.,36.,-1.),(37.,37.,97.),(35.,35.,97.),(50.,50.,97.),(37.,37.,95.)][(step//7+trace)%9];p.position=position;p.input.crouch=step%2==0;p.alive=step%31!=0;p.spawned=p.alive;marked=(step//11+trace)%3==0;objective=(step//13+trace)%2==0;server.config.escape_watch_enabled=step%29!=0;server.config.escape_watch_interval=[.1,1.,2.,float('nan')][trace%4];server.config.escape_watch_sky_seconds=1.+trace%4;server.config.escape_watch_embedded_seconds=trace%3;server.config.escape_watch_entomb_seconds=trace%5;force=step%7==0;action=step%23
  if action==0:server.mode=modes[(step//23)%2]
  if action==1:
   p=N(**vars(p));actors[0]=p;server.players[0]=p
  if action==2:server.players.pop(0,None)
  elif action==3:server.players[0]=p
  try:
   if action==4:E.forget(server,p)
   elif action==5:E.reveal_to(server,N(send=lambda data,**kw:wire.append('L'+data.hex())))
   else:E.tick(server,now=now,force=force)
   st=getattr(server,'_escape_watch',None);snapshot='-' if st is None else bits(st.next_check)+';'+'|'.join(f'{k},{t.condition or "-"},{bits(t.since)},{t.flagged or "-"},{t.clean_checks},{int(t.mode_marked)}' for k,t in st.tracks.items())
   condition=E.classify(wm,p,objective=objective);sealed=E.pocket_sealed(wm,position,[0,1,48,256][step%4]);embedded=E.body_embedded(wm,position)
  except (ValueError,OverflowError) as exc:raise AssertionError((trace,step)) from exc
  rows.append(('escape',trace,step,bits(now),vec(position),int(marked),int(objective),int(force),action,condition or '-',int(sealed),int(embedded),snapshot,E.is_flagged(server,p) or '-',','.join(wire)));wire.clear()
# BaseMode's real tick/countdown/end guards and SimulationRuntime's real frozen event drain.
for trace in range(16):
 wire=[];fail=False;events=[];server,mode,_=setup();server.players={};server.teams={2:N(id=2,name='Blue',score=trace%3),3:N(id=3,name='Green',score=trace%2)};server._mode_events=collections.deque();server.loop_count=0
 server.vote_manager=N(ensure_map_vote=lambda now:events.append('V:'+bits(now)),ensure_round_end_map_vote=lambda now:events.append('E:'+bits(now)))
 async def plugin(name,*args):events.append('P')
 server.plugin_manager=N(call_event=plugin);runtime=SimulationRuntime(server)
 def clock_collect(data,**kw):
  if data[0]==50:wire.append(data.hex())
  elif data[0]==26:events.append('M')
 server.broadcast=clock_collect
 for step in range(128):
  op=step%13;now=[0.,1.,5.,9.,10.,11.,29.999999,30.,60.,61.,120.,300.,301.][(step+trace)%13]+step//13*400;limit=[0.,10.,31.,61.,121.,310.][(step//13+trace)%6]
  if op==0:mode=BaseMode(server);server.mode=mode;mode.escape_watch_enabled=False;mode.start_time=now;server._mode_events.clear()
  mode.time_limit=limit;mode.retiring=step%47==0
  if op==1:remaining=[300.,299.,295.,120.,119.,60.,59.,30.,10.,9.,8.,1.,0.,float('nan'),float('inf'),-float('inf')][(step//13+trace)%16];mode._announce_countdown(remaining)
  else:
   for _ in range((step+trace)%5):server._mode_events.append(('on_round_start',()))
   with patch('time.time',return_value=now),patch('time.monotonic',return_value=now):asyncio.run(runtime._tick_mode())
   remaining=0.
  mode.score_limit=[0,1,2,10][step%4];score_win=asyncio.run(mode.check_win_condition());headline=mode.end_message_id([None,2,3,7][step%4]);snapshot=';'.join([bits(mode.elapsed_time),str(int(mode.ended)),str(int(mode._timeout_music_played)),'-' if mode._timeout_events_remaining is None else str(mode._timeout_events_remaining),bits(mode._countdown_last_remaining),','.join(bits(v) for v in sorted(mode._countdown_fired)) or '-',str(len(server._mode_events))])
  rows.append(('clock',trace,step,op,bits(now),bits(limit),bits(remaining),snapshot,','.join(wire),','.join(events),'-' if score_win is None else score_win,headline));wire.clear();events.clear()
# Actual timeout winner and headline policy with zero, one or multiple teams.
for case in range(512):
 wire=[];fail=False;server,mode,_=setup();server.players={};server.teams={i:N(id=i,name='Team'+str(i),score=(case//(i+1))%5-2) for i in range(case%5)};mode.score_limit=[0,1,2,3][case%4];score_win=asyncio.run(mode.check_win_condition());asyncio.run(mode._end_by_time());rows.append(('winner',case,mode.score_limit,','.join(f'{t.id}:{t.score}' for t in server.teams.values()) or '-', '-' if score_win is None else score_win,'-' if mode.winner is None else mode.winner,','.join(wire)))
# Independent native packet codecs and bounded HUD builders.
for case in range(512):
 p=ChangePlayer();p.player_id=[-32768,-1,0,254,32767,65535][case%6];p.type=[0,7,8,9,10,255][case//6%6];p.high_minimap_visibility=p.chase_cam=case%256;data=bytes(p.generate());decoded=ChangePlayer(ByteReader(data[1:]));rows.append(('marker',case,p.player_id,p.type,case%256,data.hex(),bytes(decoded.generate()).hex()))
 p=LocalisedMessage();p.chat_type=case%256;p.localise_parameters=case//2%256;p.override_previous_message=case//3%256;p.string_id=['COUNTDOWN_SECONDS','💠','team'][case%3];p.parameters=['p'+str(n) for n in range([0,1,3,16,255][case%5])];data=bytes(p.generate());decoded=LocalisedMessage(ByteReader(data[1:]));rows.append(('local',case,p.chat_type,p.localise_parameters,p.override_previous_message,hx(p.string_id),len(p.parameters),data.hex(),bytes(decoded.generate()).hex()))
for case in range(1024):
 text=['TEAM1_COLOR wins','XTEAM1_COLOR TEAM2_COLOR_ TEAM2_COLOR!','TEAM_NEUTRAL ZOMBIE_TEAM SURVIVOR_TEAM','💠'*256,'💠'*257,'','Blue','TEAM1_COLOR\nTEAM2_COLOR'][case%8];params=['2']*(case%18);id=['COUNTDOWN_MINUTES','', '💠'*256][case//8%3];out=[]
 for fn in [lambda:A.build_overlay_message(text).hex(),lambda:A.build_localised_overlay(id,params,localise_parameters=case%2==0,override_previous=case%3==0).hex()]:
  try:out.append(fn())
  except ValueError:out.append('ValueError')
 rows.append(('announcement',case,hx(text),hx(id),len(params),out[0],out[1],hx(A.localisation_safe_name(text))))
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buf=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buf,mtime=0) as stream:stream.write(raw)
p=OUT/'match-flow.tsv.gz';p.write_bytes(buf.getvalue());counts=collections.Counter(r[0] for r in rows);(OUT/'match-flow-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',observations=dict(counts),fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),catalog_sha256=hashlib.sha256(catalog.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ['modes/base_mode.py','server/combat_scores.py','server/escape_watch.py','server/announcements.py','server/simulation_runtime.py','shared/packet.pyx']},method_substitutions=False,scope='Actual unchanged policies, native WorldManager/VXL and packets; controlled clocks, actors/config, accepted damage inputs, mode marker/objective callbacks, vote/plugin terminal IO and send faults. Clock uses real BaseMode and SimulationRuntime; stopping is configured to suppress asynchronous end/restart IO, which is not claimed. Typed protocol-safe scalar strings and signed-64 counters; no arbitrary Python object conversion.'),indent=2)+'\n');print('Match flow',dict(counts),'bytes',p.stat().st_size)
