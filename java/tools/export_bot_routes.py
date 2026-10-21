"""Unchanged-source ordinary route execution, breach queues and landing contracts.

The ordinary execution tail is isolated from _navigation_intent's earlier policy
branches using its original AST statements. World, intent emission, collision
queries and all invoked helpers remain the actual source implementation.
Scripted body positions are input observations, not native-physics acceptance.
"""
from pathlib import Path
import ast,base64,dataclasses,enum,importlib.machinery,importlib.util,json,math,sys,zlib
from dataclasses import replace
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
spec=importlib.machinery.ModuleSpec('server.bot_ai',loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/'server/bot_ai')];sys.modules['server.bot_ai']=importlib.util.module_from_spec(spec)
from server.bot_ai import simple_worker as W
from server.bot_ai.messages import *
from server.bot_ai.simple_navigation import SimpleVoxelWorld,RouteStep,BreachPlan
source=ast.parse((ROOT/'server/bot_ai/simple_worker.py').read_text())
cls=next(n for n in source.body if isinstance(n,ast.ClassDef) and n.name=='SimpleBotBrain')
method=next(n for n in cls.body if isinstance(n,ast.FunctionDef) and n.name=='_navigation_intent')
start=next(i for i,n in enumerate(method.body) if n.lineno>4400 and isinstance(n,ast.Expr) and isinstance(n.value,ast.Call) and isinstance(n.value.func,ast.Attribute) and n.value.func.attr=='_reset_breach')
fn=ast.FunctionDef(name='_source_walk',args=ast.arguments(posonlyargs=[],args=[ast.arg(arg=a) for a in ['self','frame','observer','state','active_goal','step','now','effective_wading']],kwonlyargs=[],kw_defaults=[],defaults=[]),body=method.body[start:],decorator_list=[])
exec(compile(ast.fix_missing_locations(ast.Module(body=[fn],type_ignores=[])),'<unchanged ordinary execution tail>','exec'),W.__dict__)
def clean(v):
 if dataclasses.is_dataclass(v):return {f.name:clean(getattr(v,f.name)) for f in dataclasses.fields(v)}
 if isinstance(v,enum.Enum):return v.value
 if isinstance(v,dict):return {str(k):clean(x) for k,x in v.items()}
 if isinstance(v,(tuple,list,set,frozenset)):return [clean(x) for x in v]
 if isinstance(v,float) and not math.isfinite(v):return 'infinity' if v>0 else '-infinity'
 return v
raw=bytes([2,40,40,0,1,2,3,4,0,239,239,239,1,2,3,4])*(64*64)
world=SimpleVoxelWorld();world.load(MapSnapshot(7,0,raw,'tdm',source_format='retail'))
world.apply(WorldDelta(7,1,(VoxelChange(253,250,38,True,0x123456),)))
result={'definitions':[],'source_commit':'546c548780c400f21d73ca6713f86194ceb3c844','raw_zlib':base64.b64encode(zlib.compress(raw)).decode(),'walk':[],'breach':[],'reached':[]}
def actor(pid=1,**kw):return replace(PlayerSnapshot(pid,1,2,0,True,True,(250.5,250.5,37.75),(250.5,250.5,37.75),(1.,0.,0.),(0.,0.,0.),100,6,50,10,40,True,loadout=(6,0,5),weapon_tool=6,life_id=1),**kw)
def frame(p,now,others=()):return PerceptionFrame(frame_id=int(round((now-99)*100)),map_epoch=7,mode_epoch=1,observer_id=p.player_id,observer_generation=p.generation,created_at=now,mode_id='tdm',players=(p,*others),topology_version=1)
real_clock=W.time.monotonic
try:
 for kind in ('walk','jump','drop','crouch','jetpack','stall'):
  brain=W.SimpleBotBrain(world);p=actor(jetpack_id=66,jetpack_fuel=100.)
  route=tuple(RouteStep((251.5+j,250.5,37.75),MovementAffordance.WALK) for j in range(7)) if kind in ('walk','stall') else (RouteStep((252.5,250.5,35.75 if kind=='jump' else 39.75 if kind=='drop' else 37.75),MovementAffordance(kind),entry_edge=((250,250,40),(252,250,38 if kind=='jump' else 42 if kind=='drop' else 40))),)
  state=W._BotState(7,1,1,-1,True);state.route=route;state.route_topology_version=1;state.waypoint_progress_at=100.;goal=W._Goal(('route',kind),(260.5,250.5,37.75),kind,1.,True);state.goal=goal;brain._states[(p.player_id,p.generation)]=state;rows=[]
  for i in range(28):
   now=100+i*.2;W.time.monotonic=lambda:now
   x=250.5+(0 if kind=='stall' else min(i,18)*.25)
   p=replace(p,position=(x,250.5,37.75),eye=(x,250.5,37.75),grounded=i%7!=5,velocity=(.12,0.,0.))
   f=frame(p,now);step=state.route[state.route_index] if state.route_index<len(state.route) else route[0]
   intent=W._source_walk(brain,f,p,state,goal,step,now,False)
   rows.append({'frame':f,'intent':intent,'route_index':state.route_index,'remaining':len(state.route),'note':state.step_note})
   if not state.route:break
  result['walk'].append({'route':route,'goal':goal,'rows':rows})
 breach=BreachPlan((250,250,40),(254,250,40),(253,250,38),((253,250,38),),0,False,.5,2);step=RouteStep((254.5,250.5,37.75),MovementAffordance.BREACH,breach)
 for kind in ('solo','yield','assist'):
  brain=W.SimpleBotBrain(world);state=W._BotState(7,1,1,-1,True);state.route=(step,);state.route_topology_version=1;p=actor();goal=W._Goal(('breach',kind),(260.5,250.5,37.75),kind,1.,True);state.goal=goal;brain._states[(1,1)]=state;rows=[]
  for i in range(24):
   now=100+i*.25;W.time.monotonic=lambda:now;other=actor(0,position=(251.,250.5,37.75),last_action_kind='melee',last_action_position=breach.target,last_action_accepted=True,last_action_at=now-.1)
   f=frame(p,now,() if kind=='solo' else (other,));wait=None
   if kind=='assist':
    wait=brain._waits_for_breach(f,p,state,goal,other,now);intent=brain._breach_assist_queue_intent(f,p,goal,other) if wait else None
   else:intent=brain._breach_intent(f,p,state,goal,step,now)
   rows.append({'frame':f,'intent':intent,'wait':wait,'route_index':state.route_index,'remaining':len(state.route),'note':state.step_note})
  result['breach'].append({'kind':kind,'step':step,'goal':goal,'rows':rows})
 for affordance in MovementAffordance:
  for i in range(24):
   step=RouteStep((251.5,250.5,37.75),affordance);at=((251.5,250.5,37.75),(250.7,250.5,37.75),(251.5,250.5,39.75),(252.,250.5,37.75))[i%4];previous=(250.5,250.5,37.75) if i%3 else None;water=bool(i%2);last=bool(i%3);current=bool(i%5)
   result['reached'].append({'step':step,'at':at,'previous':previous,'water':water,'last':last,'current':current,'result':W._route_step_reached(step,at,wading=water,previous_position=previous,final_step=last,require_current_cell=current)})
finally:W.time.monotonic=real_clock
result['navigation']=[];result['water']=[]
try:
 for kind in ('straight','blocked','wet'):
  brain=W.SimpleBotBrain(world);state=W._BotState(7,1,1,-1,True);brain._states[(1,1)]=state
  goal=W._Goal(('nav',kind),(262.5,249.5 if kind=='straight' else 250.5,37.75),kind,1.,True);rows=[]
  for i in range(32):
   now=200+i*.2;W.time.monotonic=lambda:now;x=250.5+min(i,14)*.65;y=249.5 if kind=='straight' else 250.5
   p=actor(position=(x,y,37.75),eye=(x,y,37.75),velocity=(.12,0.,0.));f=frame(p,now)
   intent=brain._navigation_intent(f,p,state,goal,now,water_context=True if kind=='wet' else None)
   rows.append({'frame':f,'intent':intent,'route_index':state.route_index,'remaining':len(state.route),'note':state.step_note})
  result['navigation'].append({'kind':kind,'goal':goal,'rows':rows})
 water_raw=bytes([2,236,236,0,1,2,3,4,0,239,239,239,1,2,3,4])*(64*64)
 water_world=SimpleVoxelWorld();water_world.load(MapSnapshot(7,1,water_raw,'tdm',source_format='retail'))
 result['water_raw_zlib']=base64.b64encode(zlib.compress(water_raw)).decode()
 for kind in ('plain','forced'):
  brain=W.SimpleBotBrain(water_world);state=W._BotState(7,1,1,-1,True);brain._states[(1,1)]=state;state.goal=W._Goal(('water',),(230.5,250.5,233.75),'water',1.,True);rows=[]
  for i in range(32):
   now=300+i*.2;W.time.monotonic=lambda:now;x=220.5+min(i,10)*.25;p=actor(position=(x,250.5,236.75),eye=(x,250.5,236.75),wade=True,grounded=False)
   f=frame(p,now);step=water_world.water_step(p.position,preferred_goal=state.goal.position,blocked_edges=brain._water_exclusions(state,now));force=kind=='forced' and i in (12,25)
   intent=brain._water_intent(f,p,step,now,force_block_edge=force)
   rows.append({'frame':f,'step':step,'force':force,'intent':intent})
  result['water'].append({'kind':kind,'goal':state.goal,'rows':rows})
finally:W.time.monotonic=real_clock
payload=json.dumps(clean(result),separators=(',',':'),allow_nan=False).encode()
(ROOT/'java/src/test/resources/golden/bot-routes.json').write_text(json.dumps({'encoding':'zlib-json-v1','data':base64.b64encode(zlib.compress(payload,9)).decode()},separators=(',',':'))+'\n')
print('Source route observations:',len(result['reached']),'landings,',sum(len(t['rows']) for t in result['walk']),'ordinary decisions,',sum(len(t['rows']) for t in result['breach']),'breach/queue boundaries')
