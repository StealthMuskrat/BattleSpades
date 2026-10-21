"""Unchanged recovery planner/executor/driver observations with controlled input clocks."""
from pathlib import Path
import dataclasses,importlib.machinery,importlib.util,json,sys,base64,zlib,itertools,math
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
spec=importlib.machinery.ModuleSpec('server.bot_ai',loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/'server/bot_ai')];sys.modules['server.bot_ai']=importlib.util.module_from_spec(spec)
import shared.constants as C
from server.bot_ai import recovery_skills as rs,skill_driver as sd
from server.bot_ai.messages import MapSnapshot,VoxelChange,WorldDelta,PlayerSnapshot,BotProfile
from server.bot_ai.simple_navigation import SimpleVoxelWorld
from server.bot_ai.compact_vxl import CompactVoxelMap
from server.bot_ai.profiles import ProfileFactory
from server.dig_profiles import navigation_dig_profile
from dataclasses import asdict,replace
(ROOT/'java/src/main/resources/net/battlespades/catalog/bot-recovery.toml').write_text('# Unchanged source 546c548780c400f21d73ca6713f86194ceb3c844\nblock_tool = '+str(int(C.BLOCK_TOOL))+'\n')
result=dict(source_commit='546c548780c400f21d73ca6713f86194ceb3c844',scope='Actual compact VXL, canonical terrain edits, controlled monotonic search clocks and detached player feedback. Unchanged recovery planning, skill execution and driver methods; no production brain/director/host claim.')
# The supplied clock, not a policy function, makes retained slice boundaries reproducible.
original_clock=rs.time.perf_counter;counter=itertools.count();rs.time.perf_counter=lambda:next(counter)*.02
raw=bytes((0,100,100,239,1,2,3,4))*(64*64);base=CompactVoxelMap(raw,source_format='retail');world=SimpleVoxelWorld();world.load(MapSnapshot(7,0,raw,'tdm',source_format='retail'));result['raw_zlib']=base64.b64encode(zlib.compress(raw)).decode();version=0;previous=set()

def delta(cells):
 global version
 version+=1;world.apply(WorldDelta(7,version,tuple(VoxelChange(*c) for c in cells)));return dict(op='delta',version=version,cells=cells)
def scene(name,edits):
 global previous
 changes=[[*c,base.get_solid(*c),0,0.] for c in sorted(previous-set(edits))]+[[*c,solid,0,health] for c,(solid,health) in sorted(edits.items())];previous=set(edits)
 return delta(changes)|dict(name=name)
def a(tool,blocks=12,build=True):return rs.ClimbAbilities(None if tool is None else navigation_dig_profile(tool),blocks,build)
def actor(at=(245.5,245.5,97.75),**changes):return replace(PlayerSnapshot(3,1,2,0,True,True,at,at,(1.,0.,0.),(0.,0.,0.),100,int(C.BLOCK_TOOL),24,0,0,True,loadout=(int(C.BLOCK_TOOL),int(C.SPADE_TOOL)),life_id=1),**changes)
factory=ProfileFactory(31);profiles=[factory.create('casual'),factory.create('hard')];result['profiles']=profiles
result['nodes']=[dict(position=p,node=rs.node_of(p)) for p in [(10.5,11.5,z) for z in [97.75,98.65,236.75,236.,239.,-4.0]]]
result['mix']=[dict(values=v,result=rs._mix(*v)) for v in [[],[0],[3,1,7,0,0],[-1,2**40],[2**63-1,-2**63],*[[i,i+1,i+2] for i in range(20)]]]
geometry=[]
for name,edits in [('wall',{(x,y,z):(True,9.) for x in range(246,254) for y in range(242,250) for z in range(90,100)}),('overhang',{(245,245,z):(True,5.) for z in [95,96]}),('gap',{(x,y,z):(False,0.) for x in range(248,254) for y in range(243,248) for z in range(100,239)})]:
 edit=scene(name,edits);queries=[]
 for tool in [None,0,2,3,24,45,50]:
  ability=a(tool);p=ability.dig
  required=[(246,245,z) for z in [96,97,98]];protected=[(245,245,100),(246,245,99)]
  queries.append(dict(tool=tool,abilities=ability,required=required,protected=protected,aims=None if p is None else rs.dig_aims(world,required,protected,p),swings=rs.estimate_swings(world,required,protected,p),typical=rs.typical_level_cost(ability),moves=[dict(kind=kind.value,dx=dx,dy=dy,result=rs._move(world,ability,(245,245,100),kind,dx,dy,ability.blocks)) for kind,dx,dy in [(rs.StepKind.PILLAR,0,0),(rs.StepKind.STAIR,1,0),(rs.StepKind.WALK,1,0),(rs.StepKind.WALK,-1,0)]],pillar=rs.plan_pillar_up(world,(245,245,100),4,ability),stairs=rs.plan_staircase_up(world,(245,245,100),(1.,0.,0.),4,ability),bridge=rs.plan_fast_bridge(world,(245,245,100),(1.,0.,0.),12,ability),gap=rs.find_gap_bridge(world,(245.5,245.5,97.75),(260.5,245.5,97.75),ability),quick=rs.quick_climb_estimate(world,(245,245,100),ability,radius=12)))
 geometry.append(dict(scene=edit,queries=queries))
result['geometry']=geometry
searches=[]
for label,edits,target in [('cliff',{(x,y,z):(True,5.) for x in range(246,259) for y in range(240,252) for z in range(90,100)},(255.5,245.5,87.75)),('unreachable',{},(260.5,245.5,70.75)),('gap',{(x,y,z):(False,0.) for x in range(248,254) for y in range(243,248) for z in range(100,239)},(258.5,245.5,97.75))]:
 edit=scene(label,edits);queries=[]
 for tool,wallet in [(None,0),(None,6),(2,0),(2,12),(3,12),(24,12),(45,12),(50,2)]:
  ability=a(tool,wallet);goal=rs.AscentGoal.toward(target,radius=1.5);counter=itertools.count();search=rs.AscentSearch(world,(245,245,100),goal,ability,radius=18,max_expansions=350,allow_swim=False);slices=[]
  while search.status=='pending':
   status=search.advance(.012);slices.append(dict(status=status,expansions=search.expansions))
   if len(slices)>100:raise RuntimeError('Unbounded fixture search')
  queries.append(dict(tool=tool,blocks=wallet,target=target,slices=slices,plan=search.plan))
 searches.append(dict(scene=edit,queries=queries))
result['searches']=searches

def skill_state(skill):
 names=['index','step_started_at','phase','next_swing_at','next_build_at','pause_until','build_attempts','jumped_at','swings','builds','reason','backwards','feedback_at','align_since','blocked_cell']
 return {k:getattr(skill,k) for k in names}|dict(attempts=[dict(cell=c,count=n) for c,n in sorted(skill.attempts.items())])
executors=[]
for kind,edits,tool,backwards in [('pillar',{},2,True),('stair',{(x,245,z):(True,9.) for x in range(246,251) for z in range(94,100)},2,True),('cube stair',{(x,245,z):(True,5.) for x in range(246,251) for z in range(94,100)},3,True),('bridge',{(x,y,z):(False,0.) for x in range(246,254) for y in range(244,247) for z in range(100,239)},2,True),('forward bridge',{(x,y,z):(False,0.) for x in range(246,254) for y in range(244,247) for z in range(100,239)},2,False)]:
 edit=scene(kind,edits);ability=a(tool,20);plan=(rs.plan_pillar_up(world,(245,245,100),3,ability) if kind=='pillar' else rs.plan_staircase_up(world,(245,245,100),(1.,0.,0.),3,ability) if 'stair' in kind else rs.plan_fast_bridge(world,(245,245,100),(1.,0.,0.),5,ability));assert plan
 skill=rs.LocomotionSkill(plan,'fixture',100.,identity=23758,skill=.7,reaction=.2,backwards=backwards,step_started_at=100.);events=[]
 for i in range(24):
  now=100+i*.125;step=skill.current
  p=actor()
  if step is not None:
   source=step.source;at=rs.node_position(source);p=actor(at)
   if kind=='pillar' and i%6 in [1,2]:p=replace(p,position=(at[0],at[1],at[2]-2.1),eye=(at[0],at[1],at[2]-2.1),grounded=False,velocity=(0.,0.,-.08))
   if i%6==3:
    cells=[[*c,False,0,0.] for c in step.dig_cells if world.solid(*c)]+[[*c,True,0,9.] for c in step.build_cells if not world.solid(*c)]
    # Controlled accepted edit feedback; world publication remains the unchanged source method.
    if cells:events.append(delta(cells));previous.update(tuple(c[:3]) for c in cells)
   if i%6==4:p=actor(rs.node_position(step.destination))
   if i in [9,10]:p=replace(p,velocity=(.2,0.,0.))
  command=skill.update(p,world,now,ability);events.append(dict(op='update',observer=p,now=now,result=command,state=skill_state(skill)))
 executors.append(dict(scene=edit,plan=plan,tool=tool,abilities=ability,backwards=backwards,events=events))
# Rejection, timeout, no-tool and off-plan answers retain their exact failure reason.
edit=scene('failure feedback',{});plan=rs.plan_pillar_up(world,(245,245,100),2,a(2));failures=[]
for label in ['rejected','timeout','off_plan','no_blocks']:
 skill=rs.LocomotionSkill(plan,'failure',100.,step_started_at=100.);events=[]
 for i in range(10):
  now=100+i*.1;p=actor()
  if label=='rejected':p=replace(p,last_action_kind='build',last_action_at=now,last_action_accepted=False)
  elif label=='timeout':now=110.+i*.1
  elif label=='off_plan':p=actor((260.5,260.5,97.75))
  ability=a(2,0 if label=='no_blocks' else 12);command=skill.update(p,world,now,ability);events.append(dict(op='update',observer=p,now=now,abilities=ability,result=command,state=skill_state(skill)))
 failures.append(dict(name=label,events=events))
result['executors']=executors;result['failure_executors']=dict(scene=edit,plan=plan,cases=failures)

def driver_state(driver,p):
 slot=driver._slots.get((p.player_id,p.generation))
 if slot is None:return None
 return dict(life_id=slot.life_id,active=slot.skill is not None,pending='' if slot.pending is None else slot.pending.purpose,failures=slot.failures,retry_at=slot.retry_at,water_since=slot.water_since,dry_since=slot.dry_since,anchor=slot.anchor,anchor_at=slot.anchor_at,low_since=slot.low_since,completed=slot.completed,last_reason=slot.last_reason,avoid_count=len(slot.avoid),progress_index=slot.progress_index,progress_position=slot.progress_position,progress_at=slot.progress_at)
def event_objects(events):
 return [dict(zip(['at','player_id','purpose','reason','index','steps','kind','source','destination','position','grounded','wade'],e)) for e in events]
drivers=[]
for mode in ['request','stuck','gap','capacity']:
 edits={(x,y,z):(False,0.) for x in range(248,254) for y in range(243,248) for z in range(100,239)} if mode=='gap' else {}
 edit=scene(mode,edits);driver=sd.LocomotionSkillDriver(world);rows=[];counter=itertools.count()
 for i in range(275 if mode=='capacity' else 45):
  now=100+i*.25;p=actor();profile=profiles[0];op='step';req=None;goal=(255.5,245.5,87.75);answer=None
  if mode=='capacity':p=replace(p,player_id=i);op='cancel';driver.cancel(p)
  elif mode=='request':
   req=sd.SkillRequest('pillar_up',levels=3);op='request' if i<24 else 'step'
   if i in [5,6,11,12]:p=replace(p,position=(245.5,245.5,95.65),eye=(245.5,245.5,95.65),grounded=False,velocity=(0.,0.,-.08))
   if i==8:
    cells=[[245,245,99,True,0,9.]];rows.append(delta(cells));previous.add((245,245,99))
   if i>=9:p=actor((245.5,245.5,96.75))
   if i>=25:p=replace(p,life_id=2)
   if op=='request':answer=driver.request(p,profile,req,now)
   else:answer=driver.step(p,profile,now)
  elif mode=='stuck':
   op='stuck';climb=i>=28;answer=driver.consider_stuck(p,profile,now,goal,climb_to_goal=climb)
  else:op='gap';answer=driver.consider_gap(p,profile,now,(260.5,245.5,97.75))
  rows.append(dict(op=op,observer=p,profile=profile,now=now,request=req,goal=goal,climb=i>=28,result=answer,state=driver_state(driver,p),active=driver.active(p),description=driver.describe(p),awaits=driver.climb_awaits_walk_answer(p,now,goal),metrics=driver.metrics.copy(),events=event_objects(driver.events),slots=len(driver._slots)))
 drivers.append(dict(scene=edit,events=rows))
result['drivers']=drivers
# A real water/cliff atlas exercises main-ground goals, quick estimates and wet/dry episode clocks.
water_raw=bytes((0,239,239,239,1,2,3,4))*(32*32);initial=[VoxelChange(x,y,z,True,0,5.) for x in range(246,258) for y in range(242,258) for z in range(234,239)]
world.load(MapSnapshot(8,0,water_raw,'tdm',changed_cells=tuple(initial),source_format='retail'));driver=sd.LocomotionSkillDriver(world);counter=itertools.count();rows=[]
for i in range(40):
 now=200+i*.25;p=actor((245.5,250.5,236.75),wade=True,grounded=False);profile=profiles[1];goal=(253.5,250.5,231.75);op='water';answer=None
 if 15<=i<30:op='left';driver.left_water(p,now)
 else:answer=driver.consider_water(p,profile,now,goal=goal,swim_seconds=math.inf,recovering=True)
 rows.append(dict(op=op,observer=p,profile=profile,now=now,goal=goal,result=answer,state=driver_state(driver,p),active=driver.active(p),description=driver.describe(p),awaits=driver.climb_awaits_walk_answer(p,now,goal),metrics=driver.metrics.copy(),events=event_objects(driver.events),slots=len(driver._slots),quick=rs.quick_climb_estimate(world,rs.node_of(p.position),rs.ClimbAbilities.from_observer(p),radius=28),swim=sd.swim_seconds_to_shore(world,p,goal)))
result['water_driver']=dict(raw_zlib=base64.b64encode(zlib.compress(water_raw)).decode(),initial=[asdict(c) for c in initial],events=rows)
rs.time.perf_counter=original_clock
# Intern repeated immutable inputs and plans; no observation is dropped.
definitions=[];interned={}
def normalize(v):
 if dataclasses.is_dataclass(v):
  row={f.name:normalize(getattr(v,f.name)) for f in dataclasses.fields(v)}
  if isinstance(v,(PlayerSnapshot,BotProfile,rs.AscentPlan,rs.ClimbAbilities)):
   key=(type(v).__name__,json.dumps(row,separators=(',',':'),sort_keys=True))
   if key not in interned:interned[key]=len(definitions);definitions.append(row)
   return {'$ref':interned[key]}
  return row
 if isinstance(v,dict):return {str(k):normalize(x) for k,x in v.items()}
 if isinstance(v,(tuple,list)):return [normalize(x) for x in v]
 if isinstance(v,float) and not math.isfinite(v):return 'infinity' if v>0 else '-infinity'
 return v
packed=normalize(result);packed['definitions']=definitions
(ROOT/'java/src/test/resources/golden/bot-recovery.json').write_text(json.dumps(packed,separators=(',',':'),allow_nan=False)+'\n')
print('Recovery source:',len(geometry),'geometry scenes,',sum(len(x['queries']) for x in geometry),'geometry/profile queries,',sum(len(x['queries']) for x in searches),'retained searches,',sum(len(x['events']) for x in executors)+sum(len(x['events']) for x in failures),'executor boundaries,',sum(len(x['events']) for x in drivers)+len(rows),'driver boundaries,',len(definitions),'interned inputs')
