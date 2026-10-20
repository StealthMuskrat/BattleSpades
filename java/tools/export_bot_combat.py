"""Source data and connected observations for worker combat and awareness policies."""
from pathlib import Path
import dataclasses,importlib.machinery,importlib.util,json,sys,base64,zlib,random
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
spec=importlib.machinery.ModuleSpec('server.bot_ai',loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/'server/bot_ai')];sys.modules['server.bot_ai']=importlib.util.module_from_spec(spec)
import shared.constants as C
from server.bot_ai import combat_profiles as cp,combat_tactics as ct,awareness as aw
from server.bot_ai.locomotion import CombatFootwork
from server.bot_ai.messages import *
from server.bot_ai.policies import ModeBotDecision,ModeBotPosture
from server.bot_ai.profiles import ProfileFactory
from server.bot_ai.simple_navigation import SimpleVoxelWorld
from server.game_constants import WEAPON_PROFILES
from dataclasses import asdict,replace
lines=['# Unchanged source 546c548780c400f21d73ca6713f86194ceb3c844']
for k,v in dict(zombie_classes=sorted(aw._ZOMBIE_CLASSES),undirected_damage=sorted(aw._UNDIRECTED_DAMAGE),fire_reach=aw._FIRE_REACH,fire_spread=aw._FIRE_SPREAD,fire_seconds=aw._FIRE_SECONDS,molotov_speed=aw._MOLOTOV_SPEED,molotov_tool=aw._MOLOTOV_TOOL,hearing_distance=aw.HEARING_DISTANCE).items():lines.append(k+' = '+json.dumps(v))
for category,envelope in [*cp._BY_CATEGORY.items(),('default',cp._DEFAULT)]:
 lines.append('\n[[envelope]]');lines.append('category = '+json.dumps(category))
 for k,v in asdict(envelope).items():lines.append(k+' = '+json.dumps(v))
 lines.append('recoil = '+json.dumps(cp.RECOIL_KICKS.get(category,0.)))
(ROOT/'java/src/main/resources/net/battlespades/catalog/bot-combat.toml').write_text('\n'.join(lines)+'\n')
result=dict(source_commit='546c548780c400f21d73ca6713f86194ceb3c844',scope='Unchanged weapon doctrine, encounter footwork, situational tactics and complete per-life awareness boundary. Actual compact VXL, canonical terrain edits, detached perception and controlled clocks; production brain/director/motor remain pending.')
result['envelopes']=[dict(tool=t,envelope=cp.envelope_for(t),recoil=cp.recoil_kick_for(t)) for t in [-1,*WEAPON_PROFILES,999]]
result['mix']=[dict(values=v,result=ct.mix(*v)) for v in [[],[0],[1,2,3],[-1,0,2**40],[2**63-1,-2**63],*[list(range(i,i+5)) for i in range(32)]]]
factory=ProfileFactory(123);profiles=[factory.create(band) for band in ['casual','normal','hard']];result['profiles']=profiles

def player(i,team=2,at=(245.5,245.5,37.75),**changes):
 return replace(PlayerSnapshot(i,1,team,0,True,True,at,at,(1.,0.,0.),(0.,0.,0.),100,6,32,10,30,i==1,life_id=3),**changes)
actor=player(1);enemy=player(2,3,(265.5,245.5,37.75))
footwork=[]
for p in profiles:
 for ident in [1,2,255]:
  f=CombatFootwork();steps=[]
  for index in range(55):
   now=100+index*.125;at=(245.5+(.7 if index<20 else 1.4)*(index%9),245.5,37.75)
   observer=replace(actor,player_id=ident,position=at,health=40 if 12<=index<23 else 100,last_damage_at=101.4 if index>=12 else 0.,reloading=25<=index<30)
   target=replace(enemy,life_id=4 if index>=40 else 3,position=(265.5,245.5+index%5*.3,37.75));blocked=1 if index in [8,9,35] else 0
   direction=f.direction(observer,target,p,now,blocked_stage=blocked)
   spacing=f.spacing([41.,39.8,38.,34.,6.,6.6,8.,10.][index%8],10.,40.)
   steps.append(dict(observer=observer,target=target,now=now,blocked=blocked,direction=direction,spacing_distance=[41.,39.8,38.,34.,6.,6.6,8.,10.][index%8],spacing=spacing,state=asdict(f)))
  footwork.append(dict(profile=p,steps=steps))
result['footwork']=footwork

column=bytes([2,40,40,0,1,2,3,4,0,239,239,239,1,2,3,4]);raw=column*(64*64);world=SimpleVoxelWorld();world.load(MapSnapshot(7,0,raw,'tdm',source_format='retail'));result['raw_zlib']=base64.b64encode(zlib.compress(raw)).decode();commands=[];previous=set();version=0

def scene(name,cells):
 global previous,version
 cells=set(cells);changes=[VoxelChange(*c,False) for c in sorted(previous-cells)]+[VoxelChange(*c,True) for c in sorted(cells)];version+=1;world.apply(WorldDelta(7,version,tuple(changes)));commands.append(dict(op='scene',name=name,version=version,cells=changes));previous=cells

def wall(x,ys,zs):return {(x,y,z) for y in ys for z in zs}
pillar=wall(247,range(247,253),range(36,40))|wall(248,range(247,253),range(36,40))
tactical=[]
for name,cells in [('open',()),('torso lip',wall(263,range(243,248),[38,39])),('pillar',pillar),('corridor',wall(244,range(230,270),range(36,40))|wall(246,range(230,270),range(36,40)))]:
 scene(name,cells);engagement=ct.Engagement();queries=[]
 for index in range(18):
  now=100+index*.75;observer=replace(actor,last_damage_at=now if index in [3,10] else 0.,reloading=index in [5,6],last_action_kind='fire' if index in [7,8] else '',last_action_at=now,last_action_accepted=index%2==0)
  engagement.retarget(replace(enemy,life_id=4 if index>=12 else 3));line=ct.firing_line(observer.position,enemy.eye)
  entities=(EntitySnapshot(100+index,0,3,-1,(248.5,245.5,37.75),True,'projectile',velocity=(-2.,0.,0.),blast_radius=6.,detonate_at=now+.4,hazardous=True),EntitySnapshot(200,0,3,-1,(246.5,245.5,37.75),True,'mine',blast_radius=6.,detonate_at=now+.2,hazardous=True),EntitySnapshot(300,0,2,-1,(246.5,245.5,37.75),True,'projectile',velocity=(90.,0.,0.),blast_radius=8.,detonate_at=now+.1,hazardous=True))
  frame=PerceptionFrame(index,7,2,version,observer.player_id,1,now,'tdm',(observer,enemy),profile=profiles[index%3],entities=entities)
  values=dict(aim=ct.aim_offset(world,observer,enemy,frame.profile,engagement,now),blocked=ct.fire_blocked(engagement,observer,index%4!=0,now,cadence=.5),cover=ct.find_cover(world,observer,enemy.eye,engagement,now),relocation=ct.stationary_relocation(world,observer,enemy,frame.profile,engagement,now),hazard=ct.hazard_escape(world,frame,observer,frame.profile,engagement,now),line=line,shelter=ct.find_shelter(world,observer,line,side=-1 if index%2 else 1),dead_ground=ct.find_dead_ground(world,observer,line,side=-1 if index%2 else 1),walkable=ct.walkable_heading(world,observer,(0.,1.,0.),reach=3.))
  queries.append(dict(observer=observer,target=replace(enemy,life_id=4 if index>=12 else 3),frame=frame,side=-1 if index%2 else 1,wants_fire=index%4!=0,result=values,state=asdict(engagement)))
 tactical.append(dict(scene=commands[-1],queries=queries))
result['tactical']=tactical

# Independent per-life traces, all run on the real collision map; no patched policy methods.
awareness=[]
def trace(name,cells,events):
 scene(name,cells);brain=aw.Awareness(world);rows=[]
 for event in events:
  op=event.get('op','react')
  if op=='reset':brain.reset();rows.append(event|dict(lives=len(brain._lives)));continue
  if op=='forget':brain.forget(event['id'],event['generation']);rows.append(event|dict(lives=len(brain._lives)));continue
  observer=event.get('observer',actor);now=event['now'];p=event.get('profile',profiles[2])
  if op=='threw':brain.threw(observer,event['tool'],event['at'],now);rows.append(event|dict(observer=observer,lives=len(brain._lives)));continue
  frame=PerceptionFrame(len(rows)+200,7,2,version,observer.player_id,observer.generation,now,'tdm',(observer,*event.get('others',())),p,entities=tuple(event.get('entities',())),objectives=tuple(event.get('objectives',())),stimuli=tuple(event.get('stimuli',())))
  intent=BotIntent(observer.player_id,observer.generation,frame.frame_id,7,2,version,now,now+.4,event.get('movement',MovementIntent(direction=(1.,0.,0.),sprint=True,travel_source=observer.position,travel_waypoint=(275.5,245.5,37.75),gaze_waypoint=(280.5,245.5,37.75))),look=event.get('look'),action=event.get('action',BotAction()),priority=event.get('priority',BotIntentPriority.ROUTINE),debug_role='assault',life_id=observer.life_id,debug_goal=(280.,250.,37.75),debug_path=((260.,245.,37.75),),debug_navigation=(('retained',7),))
  reaction=brain.react(frame,observer,p,event.get('visible'),event.get('decision'),now);overlay=brain.overlay(frame,intent)
  rows.append(dict(op='react',frame=frame,observer=observer,visible=event.get('visible'),decision=event.get('decision'),intent=intent,reaction=reaction,overlay=overlay,lives=len(brain._lives)))
 awareness.append(dict(name=name,scene=commands[-1],events=rows))

def hit(at,**changes):return replace(actor,last_damage_at=at,last_damage_source_id=2,last_damage_source_position=enemy.position,last_damage_kind=int(C.WEAPON_KILL),**changes)
for label,cells in [('shelter',pillar),('open',())]:
 for p in profiles:
  events=[dict(now=100.,profile=p)]
  for index in range(1,50):
   now=100+index*.125;observer=hit(100.1,health=55) if index<20 else hit(102.4,health=40)
   if index>=35:observer=replace(observer,life_id=4)
   if index==17:observer=replace(observer,grounded=False)
   events.append(dict(now=now,observer=observer,profile=p))
  trace(label+' '+p.difficulty,cells,events)
# Arrival, new hit in shelter, and enemy entering cover.
events=[dict(now=100.)]
for i in range(1,16):events.append(dict(now=100+i*.15,observer=hit(100.1),others=(enemy,) if i>=10 else (),visible=enemy if i>=10 else None))
trace('visible duel',pillar,events)
for kind in [int(C.BLOCKFIRE_KILL),int(C.ROCKET_TURRET_KILL),int(C.WEAPON_KILL)]:
 trace('damage kind '+str(kind),pillar,[dict(now=100.),*[dict(now=100.2+i*.125,observer=replace(hit(100.1),last_damage_kind=kind)) for i in range(20)]])
foes=tuple(player(i+2,3,(265.5,242.5+i*3,37.75)) for i in range(3))
for decision in [None,ModeBotDecision(actor.position,'defend',objective_priority=.95,posture=ModeBotPosture.DEFEND),ModeBotDecision((280.,245.,37.75),'bodyguard',objective_priority=.95,posture=ModeBotPosture.ESCORT),ModeBotDecision((280.,245.,37.75),'escape',objective_priority=.95,posture=ModeBotPosture.EVASIVE)]:
 events=[dict(now=100.)]
 for i in range(1,32):
  now=100+i*.125;observer=hit(100.1,health=40 if i<20 else 20)
  if i>=20:observer=replace(observer,position=(243.5,250.5,37.75),eye=(243.5,250.5,37.75))
  events.append(dict(now=now,observer=observer,others=foes,visible=foes[0],decision=decision,objectives=(ObjectiveSnapshot('team_anchor',2,(225.5,250.5,37.75)),)))
 trace('odds '+str(None if decision is None else decision.role),pillar,events)
# Sounds, checks, look ownership and nearby death with heard killer.
events=[dict(now=100.)]
for i in range(1,36):
 now=100+i*.25;kind=[StimulusKind.SHOT,StimulusKind.FOOTSTEP,StimulusKind.EXPLOSION,StimulusKind.BLOCK_DESTROYED,StimulusKind.DEATH][i%5]
 sound=Stimulus(kind,(245.5,260.5 if i<20 else 253.5,37.75),now,now+1,2,2 if kind is StimulusKind.DEATH else 3)
 extra={}
 if i%7==0:extra['movement']=MovementIntent(direction=(1.,0.,0.),jump=True,affordance=MovementAffordance.JUMP)
 if i%9==0:extra['action']=BotAction(BotActionKind.FIRE,tool_id=6)
 if i%11==0:extra['look']=LookIntent(enemy.eye,visible=True,target_player_id=2,target_generation=1)
 events.append(dict(now=now,stimuli=(sound,),objectives=(ObjectiveSnapshot('team_anchor',3,(280.,250.,37.75)),),**extra))
trace('sounds and exact work',(),events)
# Fire grouping, steering, exact-step wait, own throw prediction and expiry.
def fires(at=100.1):return tuple(EntitySnapshot(30+i,0,0,-1,(255.5+i,245.5,39.),True,'blockfire',blast_radius=3.,detonate_at=at+4,hazardous=True) for i in range(3))
events=[dict(now=100.)]
for i in range(1,24):
 now=100+i*.25;observer=replace(actor,velocity=(5.6/32,0.,0.))
 if i in [8,9]:observer=replace(observer,position=(254.5,245.5,37.75),eye=(254.5,245.5,37.75),class_id=int(C.CLASS_ZOMBIE) if i==9 else 0)
 extra={}
 if i==5:extra['movement']=MovementIntent(direction=(1.,0.,0.),jump=True,affordance=MovementAffordance.JUMP)
 events.append(dict(now=now,observer=observer,entities=fires(),**extra))
events.extend([dict(op='threw',observer=actor,now=107.,tool=int(C.MOLOTOV_TOOL),at=(257.5,245.5,37.75)),dict(now=107.2,observer=replace(actor,velocity=(.175,0.,0.))),dict(now=115.),dict(op='forget',id=1,generation=1),dict(now=116.),dict(op='reset')])
trace('fire and predicted throw',(),events)
trace('fire hidden behind wall',wall(250,range(235,258),range(30,40)),[dict(now=100.),*[dict(now=100.1+i*.125,entities=fires()) for i in range(15)]])
# Bounded life replacement, explicit forget and respawn generation.
trace('life capacity',(),[dict(now=200+i*.1,observer=player(i,team=2)) for i in range(140)]+[dict(op='forget',id=139,generation=1),dict(op='reset')])
result['awareness']=awareness

definitions=[];interned={}
def normalize(v):
 if dataclasses.is_dataclass(v):
  row={f.name:normalize(getattr(v,f.name)) for f in dataclasses.fields(v)}
  if isinstance(v,(PlayerSnapshot,BotProfile,EntitySnapshot,ObjectiveSnapshot)):
   key=(type(v).__name__,json.dumps(row,separators=(',',':'),sort_keys=True))
   if key not in interned:interned[key]=len(definitions);definitions.append(row)
   return {'$ref':interned[key]}
  return row
 if isinstance(v,dict):return {str(k):normalize(x) for k,x in v.items()}
 if isinstance(v,(tuple,list)):return [normalize(x) for x in v]
 return v
packed=normalize(result);packed['definitions']=definitions
out=ROOT/'java/src/test/resources/golden/bot-combat.json';out.write_text(json.dumps(packed,separators=(',',':'),allow_nan=False)+'\n')
print('Bot combat observations:',len(result['envelopes']),'weapon envelopes,',sum(len(t['steps']) for t in footwork),'footwork transitions,',sum(len(t['queries']) for t in tactical),'tactical queries,',len(awareness),'awareness traces,',sum(len(t['events']) for t in awareness),'awareness boundaries;',len(definitions),'deduplicated input records')
