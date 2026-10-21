"""Detached unchanged-source observations for horde planning, refuges and event chat."""
from pathlib import Path
import ast,base64,dataclasses,enum,importlib.machinery,importlib.util,json,sys,zlib
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
spec=importlib.machinery.ModuleSpec('server.bot_ai',loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/'server/bot_ai')];sys.modules['server.bot_ai']=importlib.util.module_from_spec(spec)
import shared.constants as C
from server.game_constants import SPADE_TOOL_IDS,WEAPON_PROFILES
from server.projectiles import PROJECTILE_SPECS,BASE_GRAVITY
from server.bot_ai import structure_collapse as S,horde_strategy as H,zombie_refuge as R,banter as B,combat_profiles as CP
lines=['# Unchanged source 546c548780c400f21d73ca6713f86194ceb3c844','firearms = '+json.dumps(sorted(WEAPON_PROFILES)),'melee = '+json.dumps(sorted(SPADE_TOOL_IDS)),'no_sprint_uphill = '+json.dumps(sorted(int(k) for k,v in C.CLASS_CAN_SPRINT_UPHILL.items() if not v)),'climbing_packs = '+json.dumps([int(C.JETPACK_NORMAL),int(C.JETPACK_ENGINEER)]),'','[pack_flight_seconds]']
for k,v in [(C.JETPACK_NORMAL,.6),(C.JETPACK2,2.3),(C.JETPACK_ENGINEER,2.3)]:lines.append(json.dumps(str(int(k)))+' = '+repr(v))
env=dict(C=C,PROJECTILE_SPECS=PROJECTILE_SPECS)
for node in ast.parse((ROOT/'server/bot_ai/simple_worker.py').read_text()).body:
 if isinstance(node,ast.Assign) and any(isinstance(t,ast.Name) and t.id in {'_ORIENTED_ATTACK_TOOLS','_ORIENTED_SPEEDS','_ROCKET_TOOLS'} for t in node.targets):exec(compile(ast.Module(body=[node],type_ignores=[]),'<source constants>','exec'),env)
lines.insert(1,'oriented_tools = '+json.dumps(sorted(env['_ORIENTED_ATTACK_TOOLS'])))
lines.insert(1,'rocket_tools = '+json.dumps(sorted(env['_ROCKET_TOOLS'])))
lines.insert(1,'drill_tool = '+str(int(C.DRILLGUN_TOOL)))
lines.insert(1,'projectile_gravity = '+repr(BASE_GRAVITY))
lines+=['','[oriented_speeds]',*[json.dumps(str(k))+' = '+repr(v) for k,v in env['_ORIENTED_SPEEDS'].items()]]
lines+=['','[banter]',*[k+' = '+json.dumps(v) for k,v in B._LINES.items()]]
(ROOT/'java/src/main/resources/net/battlespades/catalog/bot-live.toml').write_text('\n'.join(lines)+'\n')
def clean(v):
 if dataclasses.is_dataclass(v):return {f.name:clean(getattr(v,f.name)) for f in dataclasses.fields(v)}
 if isinstance(v,enum.Enum):return v.value
 if isinstance(v,dict):return {str(k):clean(x) for k,x in v.items()}
 if isinstance(v,(tuple,list,set)):return [clean(x) for x in v]
 return v
result=dict(source_commit='546c548780c400f21d73ca6713f86194ceb3c844',definitions=[])
# All tools, including non-firearms whose doctrine must remain the default.
result['profiles']=[dict(tool=i,envelope=CP.envelope_for(i),recoil=CP.recoil_kick_for(i)) for i in range(-1,80)]
scenes=[]
for name in ['open','pillar','platform','diagonal','bridge','roof','wall','floating','water']:
 cells=set()
 if name=='pillar':cells={(24,24,z) for z in range(24,41)}
 if name=='platform':cells={(x,y,24) for x in range(21,28) for y in range(21,28)}|{(24,24,z) for z in range(25,41)}
 if name=='diagonal':cells={(24+i,24,24+i) for i in range(17)}
 if name=='bridge':cells={(x,24,24) for x in range(24,41)}|{(40,24,z) for z in range(25,41)}
 if name=='roof':cells={(x,y,24) for x in range(20,29) for y in range(20,29)}|{(x,y,z) for x in [20,28] for y in [20,28] for z in range(25,41)}
 if name=='wall':cells={(28,y,z) for y in range(10,40) for z in range(34,40)}
 if name=='floating':cells={(x,y,24) for x in range(22,27) for y in range(22,27)}
 def solid(x,y,z):return 0<=x<512 and 0<=y<512 and (z>=40 and z<=239 or (x,y,z) in cells) if name!='water' else 0<=x<512 and 0<=y<512 and z==239
 position=(24.5,24.5,21.75 if name in ['pillar','platform','diagonal','bridge','roof','floating'] else 37.75 if name!='water' else 236.75)
 hunters=((18.5,24.5,37.75),(34.5,24.5,37.75))
 support=S.support_cells(solid,position);approaches=[];cuts=[]
 for limit in [1,32,150,700]:
  a=S.approach(solid,position,limit=limit)
  approaches.append(dict(limit=limit,result=None if a is None else dict(support_z=a.support_z,closed=a.closed,frontier=a.frontier,radius=a.radius,steps=[dict(cell=c,steps=v,toward=a.toward.get(c)) for c,v in a.steps.items()]),queries=[dict(position=p,moves=None if a is None else a.moves_from(p)) for p in [position,*hunters]]))
 for limit in [16,2500]:
  for maxcost in [1,48]:
   plan=S.plan_collapse(solid,support,ground_floor_z=40,node_limit=limit,max_cost=maxcost)
   cuts.append(dict(limit=limit,max_cost=maxcost,result=plan,falls=None if plan is None else S.falls_after(solid,plan.cut,support)))
 scene=dict(name=name,cells=sorted(cells),position=position,hunters=hunters,support=support,isolations=[dict(limit=l,result=S.isolation(solid,position,hunters,limit=l)) for l in [1,40,400]],approaches=approaches,cuts=cuts,floor=S.horde_floor(solid,position,hunters))
 # Connected horde state: moving/departing targets, humans consuming allocation, working/stalled hunters.
 coord=H.HordeCoordinator();rows=[]
 a=S.approach(solid,position,limit=700);cut=S.plan_collapse(solid,support,ground_floor_z=40)
 for i in range(36):
  now=100+i*1.;zombies=tuple(H.HordeMember(j,(18.5+j%4,20.5+j//4,37.75),is_bot=j!=9,can_dig=j%3!=0,can_build=j%4==0,working=i%12<4) for j in range(10 if i<28 else 6));targets=(H.SurvivorTarget(20,position),H.SurvivorTarget(21,(40.5+i%3,32.5,37.75))) if i<32 else (H.SurvivorTarget(21,(40.5,32.5,37.75)),)
  siege={20:H.SiegeInfo(a.closed if a else False,40,cut,now,a,position)} if i>=4 else {}
  orders=coord.plan(zombies,targets,siege,now,solid=solid)
  rows.append(dict(now=now,zombies=zombies,targets=targets,siege=bool(siege),orders=list(orders.values()),metrics=clean(coord.metrics)))
 scene['horde']=rows;scenes.append(scene)
result['scenes']=scenes
refuges=[]
for kind in ['flat','hill','shore','region']:
 def surface(x,y):return 30 if (x-256)**2+(y-256)**2<36 else 239 if kind=='shore' and x<248 else 40 if kind!='flat' else 40
 def region(x,y):return 1 if x<256 else 2
 for empty in [False,True]:
  for limit in [1,90,1200]:
   survivors=() if empty else ((250.,250.,37.75),(252.,252.,37.75));zombies=((220.,245.,37.75),);exclude=((256.5,256.5,27.75),) if limit==90 else ()
   answer=R.elect_refuge(surface,survivors,zombies,exclude=exclude,region_of=region if kind=='region' else None,max_candidates=limit)
   refuges.append(dict(kind=kind,survivors=survivors,zombies=zombies,exclude=exclude,limit=limit,result=answer))
result['refuges']=refuges
banter=[]
for seed in [0,7,-42]:
 b=B.BotBanter(seed);events=[]
 for i in range(24):b.register(i,'Bot'+str(i))
 for i in range(160):
  now=i*1.7;k=i%24;v=(i+7)%24
  if i%11==0:b.on_phase(now,'start' if i%22==0 else 'end');op='phase'
  elif i==99:b.reset();op='reset'
  elif i==90:b.forget(5);op='forget'
  else:b.on_kill(now,killer_id=k,victim_id=v,killer_name='Bot'+str(k),victim_name='Bot'+str(v),streak=i%9,distance=i,melee=i%4==0,explosive=i%5==0);op='kill'
  busy=set(range(0,24,2)) if i%3 else set();out=b.due(now,busy=frozenset(busy))
  events.append(dict(i=i,now=now,op=op,busy=sorted(busy),result=out,queue=clean(b._queue)))
 banter.append(dict(seed=seed,events=events))
result['banter']=banter
# Exercise the actual main brain's retained targeting, goal and stocked launch choices.
from dataclasses import replace
from server.bot_ai import simple_worker as W
from server.bot_ai.messages import *
from server.bot_ai.profiles import ProfileFactory
from server.bot_ai.policies import ModeBotDecision,ModeBotPosture
from server.bot_ai.simple_navigation import SimpleVoxelWorld
raw=bytes([2,40,40,0,1,2,3,4,0,239,239,239,1,2,3,4])*(64*64)
result['raw_zlib']=base64.b64encode(zlib.compress(raw)).decode()
world=SimpleVoxelWorld();world.load(MapSnapshot(7,0,raw,'tdm',source_format='retail'))
brain=W.SimpleBotBrain(world);factory=ProfileFactory(19)
def player(pid=0,team=2,at=(245.5,245.5,37.75),**kw):return replace(PlayerSnapshot(pid,1,team,0,True,True,at,at,(1.,0.,0.),(0.,0.,0.),100,6,50,10,40,True,loadout=(6,0),weapon_tool=6,life_id=1),**kw)
rows=[];state=W._BotState(7,1,1,-1,True)
for i in range(120):
 now=100+i*.2;profile=factory.create(['casual','normal','hard'][i%3]);tools=[(6,0,11),(9,17,12),(13,14,0),(35,17,57),(6,20,54)][i//24]
 actor=player(class_id=[0,5,15][i%3],loadout=tools,weapon_tool=tools[0],ammo_clip=i%6,ammo_reserve=20,weapon_ammo=tuple((t,i%6 if j==0 else 5,20) for j,t in enumerate(tools)),oriented_stock=tuple((t,1) for t in tools),jetpack_id=[0,66,67,68][i%4],jetpack_fuel=i%8*15,can_shoot=i%19!=0,can_dig=i%7!=0,reloading=i%11==0,last_damage_at=now-.1 if i%9==0 else 0,last_damage_source_id=1,position=(245.5+i%9*.2,245.5,37.75))
 enemies=(player(1,3,(260.5+i%15,245.5,37.75),spawn_protected=i%8==0),player(2,3,(242.5,245.5,37.75),alive=i%5==0),player(3,3,(220.,245.5,37.75)),player(4,2,(251.5,245.5+(0 if i%4 else 5),37.75)))
 decision=ModeBotDecision((280.,245.5,37.75),['team_assault_enemy_side','ctf_attack_intel','tdm_squad_support','vip_escape'][i//30],objective_priority=.9 if i%3 else .5,posture=ModeBotPosture.ASSAULT)
 frame=PerceptionFrame(i,7,1,0,0,1,now,['tdm','vip','ctf'][i%3],(actor,*enemies),profile,objectives=(ObjectiveSnapshot('team_anchor',2,(40.,245.5,37.75)),ObjectiveSnapshot('team_anchor',3,(440.,245.5,37.75)),ObjectiveSnapshot('vip',3,enemies[0].position,carrier_id=1)))
 visible=brain._visible_target(frame,actor,state,decision);goal=brain._select_goal(frame,actor,state,now,decision=decision)
 choice=brain._oriented_attack_choice(frame,actor,enemies[0],state,profile,now)
 W.SimpleBotBrain._update_combat_progress(state,actor.position,now)
 inventory=dict(weapon=W._weapon_tool(actor,10+i%30),wallet=W._weapon_wallet(actor,tools[0]),melee=W._melee_tool(actor),swing=W._melee_swing_interval(W._melee_tool(actor)),sprint_room=W._sprint_room(actor),uphill=W._sprints_uphill(actor),abilities=sorted(a.value for a in W._movement_abilities(actor)))
 rows.append(dict(frame=frame,observer=actor,decision=decision,visible=visible,goal=goal,choice=choice,inventory=inventory,contact=[state.contact_id,state.contact_generation,state.contact_position,state.contact_until,state.acquired_at],next_oriented=state.next_oriented_at,progress=[state.combat_progress_position,state.combat_progress_at,state.combat_last_at,state.combat_stall_stage],lane=brain._friendly_launch_lane_clear(frame,actor,enemies[0].eye),safe=brain._explosive_target_safe(frame,actor,enemies[0].position,tools[-1],ignore_observer=False)))
result['brain']=rows
combat=[];original_clock=W.time.monotonic
try:
 for weapon in WEAPON_PROFILES:
  brain=W.SimpleBotBrain(world);state=W._BotState(7,1,1,-1,True);brain._states[(0,1)]=state;events=[];envelope=CP.envelope_for(weapon);gap=(envelope.ideal_min+envelope.ideal_max)/2
  for i in range(24):
   now=100+i*.25;W.time.monotonic=lambda:now;profile=factory.create(['casual','normal','hard'][i%3]);actor=player(loadout=(weapon,0,11),weapon_tool=weapon,ammo_clip=0 if i%8==4 else 6,weapon_ammo=((weapon,0 if i%8==4 else 6,20),),oriented_stock=((11,1),),health=55 if i%8==6 else 100,reloading=i%8==5,last_action_kind='fire',last_action_accepted=True,last_action_at=now,last_damage_at=now-.1 if i%8==6 else 0,position=(245.5+i%4*.25,245.5,37.75))
   target=player(1,3,(245.5+gap,245.5,37.75));frame=PerceptionFrame(i,7,1,0,0,1,now,'tdm',(actor,target),profile)
   state.acquired_at=98;state.combat_stall_stage=i%4
   intent=brain._combat_intent(frame,actor,target,state,profile,now)
   events.append(dict(frame=frame,observer=actor,target=target,stage=i%4,intent=intent))
  combat.append(dict(weapon=weapon,events=events))
finally:W.time.monotonic=original_clock
result['combat']=combat
# Plain route ownership, bounded rejected edges and actual jetpack motor proposals.
routing=[];state=W._BotState(7,1,1,-1,True)
for i in range(42):
 now=100+i*.5;goal=W._Goal(('objective','post' if i%7 else 'contact'),(250.+i%9,245.5,37.75),'investigate_sound' if i%3 else 'chase_last_seen',2.,True)
 if i%8==0:goal=None
 if i%6==1:state.route=(W.RouteStep((252.,245.5,37.75),MovementAffordance.WALK),)
 W.SimpleBotBrain._set_goal(state,goal,(245.5,245.5,37.75),now)
 edge=((240+i,245,40),(241+i,245,40));W.SimpleBotBrain._remember_blocked_edge(state,edge,now,lifetime=i%9-1)
 W.SimpleBotBrain._prune_blocked_edges(state,now)
 routing.append(dict(i=i,now=now,goal=goal,edge=edge,state=dict(goal=state.goal,route=state.route,blocked=[dict(source=k[0],target=k[1],until=v,since=state.blocked_edge_since[k]) for k,v in state.blocked_edges.items()],retry=state.corridor_retry_at,best=None if state.goal_best_distance==float('inf') else state.goal_best_distance,progress=state.goal_progress_at)))
result['routing']=routing
flights=[]
try:
 for pack in [66,67,68]:
  for climb in [0,6]:
   brain=W.SimpleBotBrain(world);state=W._BotState(7,1,1,-1,True);state.flight_source=(245.5,245.5,37.75);state.flight_step=W.RouteStep((252.5,245.5,37.75-climb),MovementAffordance.JETPACK);state.flight_started_at=100;state.flight_watch=(270.,245.,37.75)
   events=[]
   for i in range(18):
    now=100+i*.2;W.time.monotonic=lambda:now;actor=player(jetpack_id=pack,jetpack_fuel=90,grounded=i==0 or i>=14,wade=i==16,position=(245.5+i*.4,245.5,37.75-min(climb+1,i*.6)),velocity=(.1,0.,-.15 if i<7 else .1))
    frame=PerceptionFrame(i,7,1,0,0,1,now,'tdm',(actor,));intent=brain._flight_intent(frame,actor,state,now)
    events.append(dict(frame=frame,observer=actor,intent=intent,departed=state.flight_departed,next_flight=state.next_flight_at,remaining=state.flight_step is not None,blocked=[dict(source=k[0],target=k[1],until=v) for k,v in state.blocked_edges.items()]))
   flights.append(dict(pack=pack,climb=climb,events=events))
finally:W.time.monotonic=original_clock
result['flights']=flights
payload=json.dumps(clean(result),separators=(',',':'),allow_nan=False).encode()
(ROOT/'java/src/test/resources/golden/bot-live.json').write_text(json.dumps(dict(encoding='zlib-json-v1',data=base64.b64encode(zlib.compress(payload,9)).decode()),separators=(',',':'))+'\n')
