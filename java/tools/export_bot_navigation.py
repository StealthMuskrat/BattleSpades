"""Reproduce active local route, excavation and water policies from unchanged Python."""
from pathlib import Path
import dataclasses,importlib.machinery,importlib.util,json,sys,base64,zlib
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
spec=importlib.machinery.ModuleSpec('server.bot_ai',loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/'server/bot_ai')];sys.modules['server.bot_ai']=importlib.util.module_from_spec(spec)
from server.dig_profiles import PRIMARY_DIG_PROFILES,navigation_dig_profile,best_navigation_dig_profile,melee_dig_positions
from server.bot_ai.messages import MapSnapshot,VoxelChange,WorldDelta,MovementAffordance as A
from server.bot_ai.simple_navigation import SimpleVoxelWorld
from dataclasses import asdict

def profile(p):return None if p is None else asdict(p)|dict(random_extra=p.random_extra)
lines=['# Unchanged source 546c548780c400f21d73ca6713f86194ceb3c844']
for table,profiles in [('primary',PRIMARY_DIG_PROFILES.values()),('navigation',[navigation_dig_profile(i) for i in PRIMARY_DIG_PROFILES])]:
 for p in profiles:
  if p is None:continue
  lines.append('\n[['+table+']]')
  for k,v in profile(p).items():lines.append(k+' = '+json.dumps(v))
(ROOT/'java/src/main/resources/net/battlespades/catalog/bot-navigation.toml').write_text('\n'.join(lines)+'\n')
result=dict(source_commit='546c548780c400f21d73ca6713f86194ceb3c844',scope='Active unchanged local surface A*, tool footprints/health costs, water shore recovery and construction proposals. Actual compact VXL and canonical deltas; no controller or motor substitution.')
result['profiles']=[dict(profile=profile(p),footprint=melee_dig_positions((10,11,12),p.pattern),swings=[[h,p.swings_for_health(h),p.swings_for_health(h,worst_case=True)] for h in [0,5,9,17,100]]) for p in [*PRIMARY_DIG_PROFILES.values(),navigation_dig_profile(45)]]
result['selection']=[dict(tools=ids,profile=profile(best_navigation_dig_profile(ids))) for ids in [[],[99],[0,1],[1,2],[2,3,24],[45,3],[50,34,49],[52,44],[24],[45],[4,2]]]
raw=bytes((0,239,239,239,1,2,3,4));world=SimpleVoxelWorld();world.load(MapSnapshot(7,0,raw,'tdm',source_format='retail'))
result['raw_zlib']=base64.b64encode(zlib.compress(raw)).decode();commands=[];previous={};version=0

def scene(name,solids,health=5.):
 global previous,version
 now={tuple(c):health for c in solids};changes=[[*c,False,0,0.] for c in sorted(previous.keys()-now.keys())]+[[*c,True,0,h] for c,h in sorted(now.items())]
 version+=1;world.apply(WorldDelta(7,version,tuple(VoxelChange(*c) for c in changes)));commands.append(dict(op='scene',name=name,version=version,cells=changes));previous=now

def plan(name,start,goal,abilities=('jump',),tool=None,water=False,blocked=()):
 p=navigation_dig_profile(tool) if tool is not None else None;abilities=frozenset(A(a) for a in abilities);blocked=frozenset((tuple(a),tuple(b)) for a,b in blocked)
 query=dict(name=name,start=start,goal=goal,abilities=sorted(a.value for a in abilities),tool=tool,water=water,blocked=sorted(blocked))
 answer=world.plan(start,goal,abilities=abilities,dig_profile=p,allow_water=water,blocked_edges=blocked)
 node=world.surface(int(start[0]//1),int(start[1]//1),start[2],vertical_span=8,allow_water=water)
 neighbors=[] if node is None else [dict(neighbor=n,affordance=a.value,cost=c,breach=None if b is None else asdict(b)) for n,a,c,b in world._neighbors((node.x,node.y,node.support_z),abilities=abilities,dig_profile=p,allow_water=water)]
 commands.append(dict(op='plan',**query,result=asdict(answer),neighbors=neighbors))

def floor(xs=range(10,21),ys=range(9,12),z=20):return {(x,y,z) for x in xs for y in ys}
scene('flat',floor(range(8,30),range(8,14)))
plan('straight',(10.5,10.5,17.75),(25.5,10.5,17.75));plan('turn',(10.5,10.5,17.75),(25.5,12.5,17.75));plan('far segment',(10.5,10.5,17.75),(490.5,490.5,30));plan('same cell',(10.5,10.5,17.75),(10.5,10.5,20));plan('empty start',(50.5,50.5,17.75),(25.5,12.5,17.75))
scene('two block lip',floor(ys=[10])|{(12,10,18),(12,10,19)})
plan('jump',(10.5,10.5,17.75),(15.5,10.5,17.75));plan('blocked lip digs',(11.5,10.5,17.75),(15.5,10.5,17.75),('jump','breach'),2,blocked=[((11,10,20),(12,10,18))])
scene('source ceiling',{(10,10,20),(10,10,17),(11,10,18),(12,10,18)})
for tool in [2,3,24,45,50]:plan('ceiling '+str(tool),(10.5,10.5,17.75),(12.5,10.5,15.75),('jump','breach'),tool)
scene('sealed built wall',floor(ys=range(9,12))|{(12,y,z) for y in range(8,13) for z in range(10,20)},9.)
for tool in [0,1,2,3,4,24,34,44,45,49,50,52]:
 plan('approach '+str(tool),(10.5,10.5,17.75),(16.5,10.5,17.75),('jump','breach'),tool)
 plan('breach '+str(tool),(11.5,10.5,17.75),(16.5,10.5,17.75),('jump','breach'),tool)
scene('low passage',floor(ys=[10])|{(x,10,17) for x in range(12,16)})
for abilities,tool in [(('jump',),None),(('crouch',),None),(('jump','breach'),2),(('jump','breach'),50)]:plan('overhang '+str(abilities),(11.5,10.5,17.75),(18.5,10.5,17.75),abilities,tool)
scene('parallel wall',floor(range(8,25),range(8,13))|{(x,9,z) for x in range(8,25) for z in [18,19]})
plan('wall bias',(10.5,10.5,17.75),(20.5,10.5,17.75))
scene('wall overlapping capsule',floor(range(2,8),[10])|{(4,10,z) for z in range(10,20)})
plan('recover wall',(4.04,10.5,17.75),(2.5,10.5,17.75),('jump','breach'),2)
plan('recover local minimum',(4.04,10.5,17.75),(5.5,10.5,17.75),('jump',))
scene('stairs',{(x,10,30-x) for x in range(10,17)})
plan('stair compaction',(10.5,10.5,17.75),(16.5,10.5,11.75))
scene('drop',{(10,10,20),(11,10,24),(12,10,24)})
plan('safe drop',(10.5,10.5,17.75),(12.5,10.5,21.75),('drop',))
scene('covered drop',{(10,10,20),(11,10,24),(11,10,21),(12,10,24)})
plan('roof refuses drop',(10.5,10.5,17.75),(12.5,10.5,21.75),('drop',))
for rise in [3,8,9]:
 scene('flight climb '+str(rise),{(10,10,30)}|{(x,10,z) for x in range(11,15) for z in range(30-rise,31)})
 plan('climb '+str(rise),(10.5,10.5,27.75),(13.5,10.5,27.75-rise),('jump','jetpack_climb'))
for gap in [1,2,3,4]:
 scene('gap '+str(gap),{(10,10,30),(11+gap,10,30),(12+gap,10,30)})
 plan('jump gap '+str(gap),(10.5,10.5,27.75),(12.5+gap,10.5,27.75),('jump',))
 plan('flight gap '+str(gap),(10.5,10.5,27.75),(12.5+gap,10.5,27.75),('jetpack',))
scene('covered gap',{(10,10,30),(12,10,30),(11,10,27)})
plan('arc ceiling',(10.5,10.5,27.75),(12.5,10.5,27.75),('jump','jetpack'))
result['commands']=commands

# Connected live water edits retain their immutable atlas, then use live geometry on every edge.
water_cases=[]
def water_observe(name,at,goal=None,blocked=(),direction=(1.,0.,0.)):
 blocked=frozenset((tuple(a),tuple(b)) for a,b in blocked)
 answer=dict(step=world.water_step(at,preferred_goal=goal,blocked_edges=blocked),assisted=world.assisted_water_step(at,preferred_goal=goal,blocked_edges=blocked),shore_edges=sorted(world.water_shore_edges(at,radius=2)),roam=world.water_roam_step(at,heading=direction,blocked_edges=blocked),build=world.jump_build_cell(at),breaches={str(t):world.water_bank_breach(at,navigation_dig_profile(t),preferred_goal=goal,blocked_edges=blocked) for t in [0,2,3,24,45,50]},bridge=world.water_bridge_line(at,direction,max_cells=6),landing_bridge=world.water_bridge_line(at,direction,max_cells=6,require_landing_within=12),shoulder=world.narrow_bridge_shoulder_line(at,direction,max_cells=6))
 water_cases.append(dict(op='query',name=name,at=at,goal=goal,blocked=sorted(blocked),direction=direction,result=answer))
water_column=bytes((0,239,239,239,1,2,3,4));water_raw=water_column*(32*32)
initial=[VoxelChange(x,y,z,True,0,9.) for x in [248,261] for y in range(240,272) for z in range(234 if x==248 else 238,239)]
world.load(MapSnapshot(8,0,water_raw,'tdm',changed_cells=tuple(initial),source_format='retail'))
result['water_world']=dict(raw_zlib=base64.b64encode(zlib.compress(water_raw)).decode(),initial=[asdict(c) for c in initial])
water_observe('toward opposite low bank',(254.5,250.5,236.75),(265.5,250.5,235.75))
water_observe('touch high bank',(249.5,250.5,236.75),(245.5,250.5,231.75))
water_observe('low bob delays build',(254.5,250.5,236.75),(265.5,250.5,235.75));water_observe('high bob builds',(254.5,250.5,235.7),(265.5,250.5,235.75))
water_observe('adjacent low bank',(260.5,250.5,236.75),(265.5,250.5,235.75))
water_observe('failed excavation cannot reenter high floor',(249.5,250.5,236.75),(245.5,250.5,231.75),[((249,250,239),(248,250,238))])
water_observe('failed atlas edge later in lookahead',(254.5,250.5,236.75),None,[((252,250,239),(251,250,239))])
rejected=world.water_shore_edges((249.5,250.5,236.75),radius=2)
water_observe('escape rejected shore',(249.5,250.5,236.75),None,rejected)
changes=[VoxelChange(248,250,z,False,0,0.) for z in range(234,238)]
world.apply(WorldDelta(8,1,tuple(changes)));water_cases.append(dict(op='delta',version=1,cells=[asdict(c) for c in changes]))
water_observe('cached high bank lowered',(249.5,250.5,236.75),(245.5,250.5,235.75))
changes=[VoxelChange(248,250,238,False,0,0.)];world.apply(WorldDelta(8,2,tuple(changes)));water_cases.append(dict(op='delta',version=2,cells=[asdict(c) for c in changes]))
water_observe('cached bank removed',(249.5,250.5,236.75),None)
# A partially constructed bridge, landing inventory, shoulder choice and body obstruction.
changes=[VoxelChange(x,250,237,True,0,9.) for x in [250,251,252,257]]
world.apply(WorldDelta(8,3,tuple(changes)));water_cases.append(dict(op='delta',version=3,cells=[asdict(c) for c in changes]))
for at,direction in [((252.5,250.2,234.75),(1.,0.,0.)),((252.5,250.8,234.75),(1.,0.,0.)),((252.5,250.5,234.75),(-1.,0.,0.)),((252.5,250.5,234.75),(0.,0.,0.))]:water_observe('bridge proposal',at,(257.5,250.5,234.75),direction=direction)
result['water']=water_cases

def normalize(value):
 if dataclasses.is_dataclass(value):return normalize(asdict(value))
 if isinstance(value,dict):return {k:normalize(v) for k,v in value.items()}
 if isinstance(value,(list,tuple)):return [normalize(v) for v in value]
 return value
out=ROOT/'java/src/test/resources/golden/bot-navigation.json';out.write_text(json.dumps(normalize(result),indent=2,allow_nan=False)+'\n')
print('Bot navigation observations:',sum(c['op']=='plan' for c in commands),'route/neighbor queries,',sum(c['op']=='scene' for c in commands),'terrain scenes,',sum(c['op']=='query' for c in water_cases),'water/construction queries')
