"""Unchanged cooperative geometry, schematic plans and live-feedback task observations."""
from pathlib import Path
import dataclasses,importlib.machinery,importlib.util,json,sys,os,math,base64,zlib
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
spec=importlib.machinery.ModuleSpec('server.bot_ai',loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/'server/bot_ai')];sys.modules['server.bot_ai']=importlib.util.module_from_spec(spec)
for name,folder in [('modes','modes'),('modes.unofficial','modes/unofficial')]:
 spec=importlib.machinery.ModuleSpec(name,loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/folder)];sys.modules[name]=importlib.util.module_from_spec(spec)
if os.environ.get('BATTLESPADES_REFERENCE_PYTHON'):
 import aoslib
 aoslib.__path__.append(str(Path(os.environ['BATTLESPADES_REFERENCE_PYTHON'])/'aoslib'))
import shared.constants as C
from dataclasses import replace,asdict
from server.bot_ai.messages import MapSnapshot,WorldDelta,VoxelChange,PlayerSnapshot,PerceptionFrame,EntitySnapshot,ObjectiveSnapshot
from server.bot_ai.simple_navigation import SimpleVoxelWorld
from server.bot_ai.compact_vxl import CompactVoxelMap
from server.bot_ai.prefab_policy import load_bot_prefab_geometry,BOT_PREFAB_BLOCK_COUNTS
from server.bot_ai import project_sites as PS
from server.bot_ai.cooperative_behavior import CooperativeBehavior
from server.bot_ai.policies import ModeBotDecision,ModeBotPosture
from server.bot_ai.profiles import ProfileFactory
from server.bot_ai.schematics import library,fit,plan_build,validate_plan,structure_placement
from server.bot_ai.schematics.sites import SchematicSites
raw=bytes((0,100,100,239,1,2,3,4))*(64*64)
geometry=load_bot_prefab_geometry(BOT_PREFAB_BLOCK_COUNTS)
assert geometry,'Build the development-only aoslib.kv6 oracle first'
result=dict(source_commit='546c548780c400f21d73ca6713f86194ceb3c844',raw_zlib=base64.b64encode(zlib.compress(raw)).decode(),geometry=geometry)
world=SimpleVoxelWorld();version=0;previous=set();base=CompactVoxelMap(raw,source_format='retail')
def reset():
 global world,version,previous
 world=SimpleVoxelWorld();world.load(MapSnapshot(7,0,raw,'tdm',source_format='retail',prefab_geometry=geometry));version=0;previous=set()
def edit(cells):
 global version
 version+=1;world.apply(WorldDelta(7,version,tuple(VoxelChange(*c) for c in cells)));previous.update(tuple(c[:3]) for c in cells);return dict(op='delta',version=version,cells=cells)
def scene(name,edits):
 cells=[[*c,base.get_solid(*c),0,0.] for c in sorted(previous-set(edits))]+[[*c,solid,0,health] for c,(solid,health) in sorted(edits.items())]
 row=edit(cells);previous.clear();previous.update(edits);return row|dict(name=name)
def player(**kw):
 p=PlayerSnapshot(3,1,int(C.TEAM1),int(C.CLASS_SCOUT),True,True,(245.5,245.5,97.75),(245.5,245.5,97.75),(1.,0.,0.),(0.,0.,0.),100,int(C.SNIPER_TOOL),100,5,20,True,weapon_tool=int(C.SNIPER_TOOL),loadout=(int(C.SNIPER_TOOL),int(C.PREFAB_TOOL),int(C.BLOCK_TOOL),int(C.LANDMINE_TOOL),int(C.SUPERSPADE_TOOL)),prefabs=('prefab_small_wall',),deployable_stock=((int(C.LANDMINE_TOOL),1),),life_id=1)
 return replace(p,**kw)
reset();queries=[]
for name,edits in [('flat',{}),('wall',{(x,y,z):(True,9.) for x in range(248,251) for y in range(242,249) for z in range(97,100)}),('gap',{(x,y,z):(False,0.) for x in range(246,250) for y in range(242,249) for z in range(100,239)}),('raised',{(x,y,z):(True,5.) for x in range(242,245) for y in range(241,250) for z in range(98,100)})]:
 s=scene(name,edits);rows=[]
 for i in range(16):
  p=player(blocks=[0,6,30,100][i%4],loadout=(int(C.SNIPER_TOOL),int(C.PREFAB_TOOL),int(C.BLOCK_TOOL),int(C.LANDMINE_TOOL),int(C.SUPERSPADE_TOOL)) if i<12 else (int(C.BLOCK_TOOL),),wade=i==14,grounded=i!=15)
  friends=() if i%3==0 else ((249.5,249.5,97.75),) if i%3==1 else ((250.,250.,97.75),)*33;reserved=frozenset() if i%2==0 else frozenset({(248,245,99),(245,249,99)})
  lane=(265.5,245.5,97.75)
  rows.append(dict(observer=p,lane=lane,friends=friends,reserved=sorted(reserved),sniper=PS.find_sniper_outpost(world,p,lane,friendly_positions=friends,reserved_cells=reserved),cover=PS.find_prefab_cover(world,p,lane,friendly_positions=friends,reserved_cells=reserved),rear=PS.find_prefab_cover(world,p,lane,rear=True,friendly_positions=friends,reserved_cells=reserved),bridge=PS.find_bridge_project(world,p,lane,reserved_cells=reserved),mine=PS.find_mine_approach(world,p,lane,friendly_positions=friends,reserved_cells=reserved),breach=PS.find_breach_project(world,p,lane,reserved_cells=reserved),decoration=PS.find_decorative_site(world,p,friendly_positions=friends,reserved_cells=reserved),rampart=PS.find_rampart_segment(world,p,(245.5,245.5,97.75),friendly_positions=friends,reserved_cells=reserved)))
 queries.append(dict(scene=s,rows=rows))
result['projects']=queries

def placement_row(p):
 return None if p is None else dict(name=p.name,anchor=p.anchor,rotation=p.rotation,cells=[dict(cell=c,color=t) for c,t in p.cells.items()],foundation=sorted(p.foundation),clear=sorted(p.clear),columns=sorted(p.columns),occupant=p.occupant)
def plan_row(p):return dict(steps=p.steps,unbuildable=p.unbuildable,region=p.region,estimated_seconds=p.estimated_seconds,walk_cells=p.walk_cells,cell_step=[dict(cell=c,index=i) for c,i in p.cell_step.items()],feasible=p.feasible,cost=p.cost,lines=p.lines,singles=p.singles,line_cells=p.line_cells)
reset();shapes=[]
for name,shape in library().items():
 for rotation in range(4):
  placement,why=fit(shape,world.solid,(245,245),100,rotation)
  plan=None if placement is None else plan_build(world.solid,placement,seed=3)
  shapes.append(dict(name=name,rotation=rotation,placement=placement_row(placement),reason=why,plan=None if plan is None else plan_row(plan),problems=None if plan is None else validate_plan(world.solid,plan)))
result['schematics']=shapes

def cooperative_state(coop,p):
 life=coop.lives.get((p.player_id,p.generation));task=life.task if life else None
 return dict(lives=len(coop.lives),task=None if task is None else {k:getattr(task,k) for k in ['task_id','kind','goal','lane','started_at','expires_at','score','stage','phase','patient','patient_health','pending','sent_at','confirmed','progress_at','best_distance','remaining_cells','next_action','built_cells','source_entity_id','starting_supplies','schematic_id','step_index','stand','ready_at','waiting_since']},partner=None if life is None else life.partner,next_evaluate=0 if life is None else life.next_evaluate,next_project=0 if life is None else life.next_project,metrics=dict(coop.teams.metrics),events=list(coop.teams.events),site_metrics=dict(coop.sites.metrics),sites=[s.status(world.solid) for s in coop.sites.sites.values()])
traces=[];profile=ProfileFactory(17).create('hard')
for mode in ['outpost_mine','outpost_radar','cover','rampart','breach','resupply','heal_self','heal_ally','seek_medic','partner','sabotage','requested','vip_shelter','rejection','timeout','interrupt']:
 reset();rows=[];coop=CooperativeBehavior(world);p=player();entities=[];others=[];goal=(265.5,245.5,97.75);strategic=ModeBotDecision(goal,'known_lane');visible=None
 if mode in ['cover','rampart','requested','vip_shelter','rejection','timeout']:p=replace(p,loadout=(int(C.BLOCK_TOOL),int(C.PREFAB_TOOL),int(C.SMG_TOOL)),weapon_tool=int(C.SMG_TOOL),tool=int(C.SMG_TOOL))
 if mode=='outpost_radar':p=replace(p,loadout=(int(C.SNIPER_TOOL),int(C.PREFAB_TOOL),int(C.BLOCK_TOOL),int(C.RADAR_STATION_TOOL)),deployable_stock=((int(C.RADAR_STATION_TOOL),1),))
 if mode=='breach':
  p=replace(p,loadout=(int(C.SUPERSPADE_TOOL),int(C.BLOCK_TOOL)),tool=int(C.SUPERSPADE_TOOL),weapon_tool=-1)
  rows.append(edit([[x,y,z,True,0,9.] for x in range(248,251) for y in range(242,249) for z in range(97,100)]))
 if mode=='resupply':p=replace(p,ammo_clip=0,ammo_reserve=0);entities=[EntitySnapshot(51,int(C.AMMO_CRATE),-1,-1,(252.5,245.5,97.75))]
 if mode in ['heal_self','heal_ally']:p=replace(p,health=30 if mode=='heal_self' else 100,loadout=(*p.loadout,int(C.MEDPACK_TOOL)),deployable_stock=((int(C.MEDPACK_TOOL),2),))
 if mode=='heal_ally':others=[player(player_id=4,health=30,position=(247.5,245.5,97.75),eye=(247.5,245.5,97.75))]
 if mode=='seek_medic':p=replace(p,health=30);others=[player(player_id=4,position=(250.5,245.5,97.75),eye=(250.5,245.5,97.75),loadout=(int(C.MEDPACK_TOOL),),deployable_stock=((int(C.MEDPACK_TOOL),1),))]
 if mode=='partner':p=replace(p,loadout=(int(C.BLOCK_TOOL),));others=[player(player_id=4,is_bot=False,position=(249.5,245.5,97.75),eye=(249.5,245.5,97.75))]
 if mode=='sabotage':entities=[EntitySnapshot(52,0,int(C.TEAM2),9,(260.5,245.5,97.75),tool_id=int(C.RADAR_STATION_TOOL),hit_position=(260.5,245.5,97.75),hit_radius=1.)]
 if mode=='rampart':strategic=ModeBotDecision(p.position,'zombie_survivor_refuge',directive='fortify',posture=ModeBotPosture.BUILD,objective_priority=.86);objectives=(ObjectiveSnapshot('zombie_refuge',p.team,p.position),)
 elif mode=='vip_shelter':strategic=ModeBotDecision(p.position,'vip_rally',directive='vip_shelter',posture=ModeBotPosture.DEFEND,objective_priority=1.);objectives=(ObjectiveSnapshot('vip',p.team,p.position,carrier_id=p.player_id),ObjectiveSnapshot('team_anchor',p.team,(225.5,245.5,97.75)));others=[player(player_id=4,position=(250.5,249.5,97.75),eye=(250.5,249.5,97.75))]
 else:objectives=()
 if mode in ['requested','rejection','timeout']:
  anchor=(248.5,245.5,97.75);site=coop.request_schematic(p.team,'sandbag_wall',anchor,99.,rotation=1);rows.append(dict(op='request',team=p.team,shape='sandbag_wall',anchor=anchor,now=99.,rotation=1,result=None if site is None else site.status(world.solid)))
 for i in range(90):
  now=100+i*.35
  if i==70:p=replace(p,life_id=2)
  if mode=='interrupt' and i>=5:visible=player(player_id=9,team=int(C.TEAM2),position=(250.5,245.5,97.75),eye=(250.5,245.5,97.75))
  frame=PerceptionFrame(i,7,1,version,p.player_id,p.generation,now,'vip' if mode=='vip_shelter' else 'zom' if mode=='rampart' else 'tdm',(p,*others),profile=profile,entities=tuple(entities),objectives=objectives,mode_phase='active',behavior_version='cooperative',friendly_mischief=False)
  order=coop.decide(frame,p,visible,strategic);rows.append(dict(op='decide',frame=frame,observer=p,visible=visible,strategic=strategic,result=order,state=cooperative_state(coop,p)))
  if order is None:continue
  if order.action.kind.value=='none':
   if not order.hold:
    at=order.goal;p=replace(p,position=at,eye=at)
   if mode=='resupply' and math.dist(p.position,(252.5,245.5,97.75))<2:p=replace(p,ammo_clip=20,ammo_reserve=80)
   if mode in ['heal_self','seek_medic'] and any(math.dist(p.position,e.position)<2 and e.tool_id==int(C.MEDPACK_TOOL) for e in entities):p=replace(p,health=90)
   continue
  if mode=='timeout':continue
  accepted=mode!='rejection';p=replace(p,last_action_request_id=order.action.request_id,last_task_accepted=accepted,last_action_accepted=accepted,last_action_reason='' if accepted else 'fixture_rejection')
  if not accepted:continue
  task=coop.lives[(p.player_id,p.generation)].task
  if task and task.action_site and task.action_site.cells:
   rows.append(edit([[*c,True,0,9.] for c in task.action_site.cells]));p=replace(p,blocks=max(0,p.blocks-len(task.action_site.cells)))
  elif order.action.kind.value=='deploy':
   entities.append(EntitySnapshot(100+i,0,p.team,p.player_id,order.action.position,tool_id=order.action.tool_id,uses_remaining=2));p=replace(p,deployable_stock=tuple((t,max(0,n-1)) if t==order.action.tool_id else (t,n) for t,n in p.deployable_stock))
   if mode=='heal_ally':others=[replace(q,health=90) for q in others]
 traces.append(dict(name=mode,events=rows))
result['cooperative']=traces
# Intern immutable snapshots; snapshot every mutable observation at its boundary.
definitions=[];interned={}
def normalize(v):
 if dataclasses.is_dataclass(v):
  row={f.name:normalize(getattr(v,f.name)) for f in dataclasses.fields(v)}
  if isinstance(v,(PlayerSnapshot,PerceptionFrame)):
   key=(type(v).__name__,json.dumps(row,separators=(',',':'),sort_keys=True))
   if key not in interned:interned[key]=len(definitions);definitions.append(row)
   return {'$ref':interned[key]}
  return row
 if isinstance(v,dict):return {str(k):normalize(x) for k,x in v.items()}
 if isinstance(v,(tuple,list)):return [normalize(x) for x in v]
 if isinstance(v,(set,frozenset)):return [normalize(x) for x in sorted(v)]
 if isinstance(v,float) and not math.isfinite(v):return 'infinity' if v>0 else '-infinity'
 return v
packed=normalize(result);packed['definitions']=definitions
payload=json.dumps(packed,separators=(',',':'),allow_nan=False).encode()
(ROOT/'java/src/test/resources/golden/bot-cooperative.json').write_text(json.dumps(dict(encoding='zlib-json-v1',data=base64.b64encode(zlib.compress(payload,9)).decode()),separators=(',',':'))+'\n')
print('Cooperative source:',sum(len(t['rows']) for t in queries),'geometry queries,',len(shapes),'schematic fits/plans,',sum(len(t['events']) for t in traces),'cooperative boundaries,',len(geometry),'authored prefab geometries')
