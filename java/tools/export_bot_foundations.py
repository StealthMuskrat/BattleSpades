"""Source data and behavioral observations for the pure Java bot foundations."""
from pathlib import Path
import dataclasses,importlib.machinery,importlib.util,json,sys
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
spec=importlib.machinery.ModuleSpec('server.bot_ai',loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/'server/bot_ai')];sys.modules['server.bot_ai']=importlib.util.module_from_spec(spec)
from server.bot_ai import profiles,stimuli,planning_budget,skill_balance
from server.dig_profiles import MAP_BLOCK_HEALTH
lines=['# Unchanged source 546c548780c400f21d73ca6713f86194ceb3c844']
for key,attr in [('handles','_HANDLES'),('titles','_TITLES'),('adjectives','_ADJECTIVES'),('nouns','_NOUNS'),('legacy_callsigns','_LEGACY_CALLSIGNS'),('legacy_adjectives','_LEGACY_ADJECTIVES'),('legacy_nouns','_LEGACY_NOUNS'),('classes','_CLASS_PREFERENCES')]:lines.append(key+' = '+json.dumps(list(getattr(profiles,attr))))
lines.append('suffixes = '+json.dumps(profiles._number_suffixes()));lines.append('hearing_distance = '+repr(stimuli.HEARING_DISTANCE));lines.append('map_block_health = '+repr(MAP_BLOCK_HEALTH))
for name,band in profiles._BANDS.items():
 lines.append('\n[bands.'+name+']')
 for field,value in dataclasses.asdict(band).items():lines.append(field+' = '+json.dumps(value))
(ROOT/'java/src/main/resources/net/battlespades/catalog/bot-foundations.toml').write_text('\n'.join(lines)+'\n')

# All observations below invoke unchanged source policies. Inputs/clocks are controlled, not implementations.
import base64,hashlib,math,random,zlib
from types import SimpleNamespace as NS
from dataclasses import asdict,replace
from server.bot_ai.messages import StimulusKind,MapSnapshot,WorldDelta,VoxelChange,MovementAffordance
from server.bot_ai.compact_vxl import CompactVoxelMap
from server.bot_ai.navigation_atlas import NavigationAtlas,source_digest
from server.bot_ai.simple_navigation import SimpleVoxelWorld,RouteStep,BreachPlan
from server.bot_ai.surface_corridor import SurfaceCorridorSearch
from server.bot_ai import path_following
result=dict(source_commit='546c548780c400f21d73ca6713f86194ceb3c844',scope='Unchanged profile/name streams, planning fairness, sensory uncertainty, adaptive difficulty, compact collision, atlas/cache, snapshot/delta queries, corridor guidance and path following. Controlled clocks, input geometry and roster; no full brain/director/action-authority claim.')
profile_cases=[]
for seed,length in [(0,15),(27,15),(-7,10),(2**65+17,15),(0,3)]:
 factory=profiles.ProfileFactory(seed,length);commands=[];names=[]
 for i in range(24):
  if i in (7,14,21):
   name=names[i//7-1];factory.release_name(name);commands.append(dict(op='release',name=name))
  difficulty=['mixed','casual','normal','hard','INVALID'][i%5];profile=factory.create(difficulty);names.append(profile.name);commands.append(dict(op='create',difficulty=difficulty,result=asdict(profile)))
 profile_cases.append(dict(seed=str(seed),length=length,commands=commands))
result['profiles']=profile_cases

budget_cases=[]
for rate in (1.,24.,96.):
 rng=random.Random(781);budget=planning_budget.PlanningBudget(rate,decision_hz=8);commands=[];jobs={};next_job=0
 def record(command,answer=None):
  commands.append(command|dict(answer=answer,denial=budget.last_denial,snapshot=budget.snapshot()))
 for tick in range(18):
  now=tick/8
  if tick==8:budget.scale_for(12);record(dict(op='scale',bots=12))
  if tick==12:budget.refresh_waiters(((i,1) for i in range(8)),now+2);record(dict(op='refresh',keys=[[i,1] for i in range(8)],now=now+2))
  for bot in range(8):
   key=(bot,1 if tick<15 else 2);preview=[budget.would_grant(key,now),budget.spare(key,now)];job=budget.try_acquire(key,now);ident=next_job;next_job+=1
   if job is not None:jobs[ident]=job
   record(dict(op='acquire',key=key,now=now,job=ident,preview=preview),job is not None)
   if job is not None:
    maxima=[rng.choice((0,32,256,900)),rng.choice((1,512)),-1];grants=[];used=[]
    for maximum in maxima:
     granted=job.begin_search(maximum);grants.append(granted);amount=min(granted,rng.choice((0,4,128,512)));used.append(amount);job.expansions+=amount
    seconds=rng.choice((-.1,.001,.008));budget.finish(job,seconds);record(dict(op='finish',job=ident,maxima=maxima,grants=grants,used=used,expansions=job.expansions,seconds=seconds))
  if tick==10:budget.cancel((7,1));record(dict(op='cancel',key=[7,1]))
 budget.reset();record(dict(op='reset'))
 for index in range(150):budget.try_acquire((index,9),100)
 record(dict(op='flood',count=150,now=100))
 for duration in range(300):budget.finish(planning_budget.PlanningJob(searches=1,expansions=7),duration/1000)
 record(dict(op='raw_samples',count=300))
 budget_cases.append(dict(rate=rate,frequency=8,commands=commands))
result['budgets']=budget_cases

# Sound publication order, rate boundaries, lifetime behind a longer event, own/squad filtering and RNG consumption.
bus=stimuli.BotStimulusBus(4);sounds=[];rng=random.Random(123)
def sound(command,answer):sounds.append(command|dict(result=answer))
for i,(kind,at,source,team,radius,lifetime,now) in enumerate([
 (StimulusKind.SHOT,(0,0,0),1,2,500,4,0), (StimulusKind.SHOT,(1,0,0),1,2,50,1,.049),
 (StimulusKind.SHOT,(2,0,0),1,2,50,1,.05), (StimulusKind.EXPLOSION,(12,4,1),2,3,80,5,.1),
 (StimulusKind.FOOTSTEP,(3,0,0),3,2,50,.05,.1),(StimulusKind.OBJECTIVE,(80,0,0),4,3,120,10,.1),
 (StimulusKind.SHOT,(50,0,0),5,3,100,1,.1),(StimulusKind.SHOT,(50.001,0,0),6,3,100,1,.1)]):
 sound(dict(op='publish',kind=kind.value,at=at,source=source,team=team,radius=radius,lifetime=lifetime,now=now),bus.publish(kind,at,source_id=source,team=team,radius=radius,lifetime=lifetime,now=now))
sound(dict(op='bad_position'),bus.publish(StimulusKind.SHOT,(math.nan,0,0),now=2))
for now,limit,team in [(.12,24,None),(.2,24,2),(.3,2,3),(1.1,24,None),(5.1,24,2),(10.1,24,None)]:
 found=bus.perceive((0,0,0),now=now,rng=rng,limit=limit,observer_id=1,team=team)
 sound(dict(op='perceive',at=[0,0,0],now=now,limit=limit,observer=1,team=team),[asdict(s)|dict(kind=s.kind.value) for s in found])
players=[dict(id=i,team=2 if i%2==0 else 3,alive=True,spawned=True,grounded=i!=1,crouch=i==2,sneak=i==3,sprint=i==4,velocity=[.1,0,0],position=[i,0,0]) for i in range(6)]
for now in (11.,11.1,11.386,11.512,12.):
 live=[NS(id=p['id'],team=p['team'],alive=p['alive'],spawned=p['spawned'],grounded=p['grounded'],input=NS(crouch=p['crouch'],sneak=p['sneak'],sprint=p['sprint']),vx=p['velocity'][0],vy=p['velocity'][1],position=p['position']) for p in players]
 sound(dict(op='footsteps',players=players,now=now),bus.note_footsteps(live,now))
for i in range(40):bus.publish(StimulusKind.OBJECTIVE,(i,0,0),source_id=i+100,radius=256,now=20+i*.1)
sound(dict(op='capacity'),[asdict(s)|dict(kind=s.kind.value) for s in bus.perceive((0,0,0),now=24,rng=rng,limit=50)])
result['sounds']=sounds

skill_cases=[]
for bands in [['normal'],['normal','hard'],[],['bad']]:
 profile=profiles.ProfileFactory(4).create('normal');limits=skill_balance.band_limits(bands)
 for strength in [-.9,-.35,-1e-10,0.,.35,.9]:skill_cases.append(dict(profile=asdict(profile),bands=bands,strength=strength,result=asdict(skill_balance.scale_profile(profile,strength,limits))))
result['scales']=skill_cases
server=NS(config=NS(bots=NS(skill_balance=True,difficulty='normal',skill_balance_rate=.03,skill_balance_max_shift=.35,skill_balance_min_events=6,skill_balance_deadband=.15)),players={},teams={2:NS(score=0),3:NS(score=0)},mode=NS(ended=False),bots=NS(_runtime={}))
balancer=skill_balance.BotSkillBalancer(server);factory=profiles.ProfileFactory(24)
for i in range(3):server.bots._runtime[i]=NS(generation=1,player=NS(team=2 if i==0 else 3),profile=factory.create('normal'))
initial=[dict(id=i,generation=r.generation,team=r.player.team,profile=asdict(r.profile)) for i,r in server.bots._runtime.items()];steps=[]
for step,now in enumerate([0.,.1,1.,2.,12.,11.,13.,15.,17.,22.,23.,24.]):
 humans=[dict(team=2,kills=20 if step<6 else 1,deaths=1 if step<6 else 20,bot=False),dict(team=3,kills=1,deaths=4,bot=step%2==0)]
 server.players={i:NS(team=p['team'],kills=p['kills'],deaths=p['deaths'],is_bot=p['bot']) for i,p in enumerate(humans)};server.teams[2].score=8 if step<6 else 0;server.teams[3].score=1 if step<6 else 8;server.mode.ended=step==10;server.config.bots.skill_balance=step!=9
 if step==5:server.bots._runtime[0].player.team=3
 if step==7:server.bots._runtime.pop(1)
 if step==8:server.bots._runtime[1]=NS(generation=2,player=NS(team=2),profile=factory.create('hard'))
 before=[dict(id=i,generation=r.generation,team=r.player.team,profile=asdict(r.profile)) for i,r in server.bots._runtime.items()];balancer.update(now)
 steps.append(dict(now=now,humans=humans,scores={str(k):v.score for k,v in server.teams.items()},ended=server.mode.ended,enabled=server.config.bots.skill_balance,before=before,factors=balancer.factors.copy(),targets=balancer.targets.copy(),after={str(i):asdict(r.profile) for i,r in server.bots._runtime.items()}))
result['balancing']=dict(initial=initial,steps=steps)

# Small complete atlas planes, source-compressed cache envelopes, layers/flows and BLAKE2 block boundaries.
atlases=[];rng=random.Random(54)
for case in range(12):
 width,height=7+case%3,4+case%2;masks=[]
 for index in range(width*height):
  values=rng.choice([(239,),(238,),(226,),(20,),(20,23),(10,12),(10,13),(0,1),(2,),(10,11,12),(5,12),(0,)])
  masks.append(sum(1<<z for z in values))
 atlas=NavigationAtlas.from_masks(masks,width=width,height=height);atlas.validate();raw=('atlas-'+str(case)).encode();digest=source_digest(raw)
 atlases.append(dict(width=width,height=height,water=239,masks=[hex(m) for m in masks],planes={key:list(getattr(atlas,key)) for key in ['primary_support','layer_count','flags','clearance','regions','water_next','water_goal','water_distance']},stats=asdict(atlas.stats()),contexts=[asdict(atlas.context(i%width,i//width,30)) for i in range(atlas.area)],routes=[None if atlas.water_route(i%width,i//width) is None else asdict(atlas.water_route(i%width,i//width)) for i in range(atlas.area)],digest_input=raw.hex(),digest=digest.hex(),cache=base64.b64encode(atlas.to_cache_bytes(digest)).decode()))
result['atlases']=atlases
# Keep corrupt cache observations inside the real source decoder's bounded validation boundary.
from server.bot_ai.navigation_atlas import _CACHE_HEADER
bad_caches=[]
base_case=atlases[0];encoded=base64.b64decode(base_case['cache']);digest=bytes.fromhex(base_case['digest']);payload=zlib.decompress(encoded[_CACHE_HEADER.size:]);area=base_case['width']*base_case['height']
def corrupted(body):
 header=list(_CACHE_HEADER.unpack_from(encoded));compressed=zlib.compress(body);header[-1]=len(compressed);return _CACHE_HEADER.pack(*header)+compressed
unknown=bytearray(payload);unknown[area*2]|=128
for label,bad in [('unknown_flags',corrupted(unknown)),('oversized_decoded_body',corrupted(b'x'*100000)),('trailing_compressed_stream',encoded+b'junk')]:
 if label=='trailing_compressed_stream':
  header=list(_CACHE_HEADER.unpack_from(bad));header[-1]+=4;bad=_CACHE_HEADER.pack(*header)+bad[_CACHE_HEADER.size:]
 try:NavigationAtlas.from_cache_bytes(bad,expected_digest=digest)
 except ValueError:pass
 else:raise AssertionError('Source accepted malformed atlas '+label)
 bad_caches.append(dict(label=label,digest=digest.hex(),cache=base64.b64encode(bad).decode()))
for case in atlases:
 distances=case['planes']['water_distance'];nexts=case['planes']['water_next'];index=next((i for i,d in enumerate(distances) if d>1),None)
 if index is None:continue
 encoded_case=base64.b64decode(case['cache']);header=list(_CACHE_HEADER.unpack_from(encoded_case));body=bytearray(zlib.decompress(encoded_case[_CACHE_HEADER.size:]));a=case['width']*case['height'];parent=nexts[index];import struct
 struct.pack_into('<H',body,16*a+parent*2,distances[index]);compressed=zlib.compress(body);header[-1]=len(compressed);bad=_CACHE_HEADER.pack(*header)+compressed;d=bytes.fromhex(case['digest'])
 try:NavigationAtlas.from_cache_bytes(bad,expected_digest=d)
 except ValueError:pass
 else:raise AssertionError('Source accepted cyclic flow')
 bad_caches.append(dict(label='non_decreasing_water_flow',digest=d.hex(),cache=base64.b64encode(bad).decode()));break
result['bad_caches']=bad_caches

result['digests']=[dict(raw=bytes(i%251 for i in range(size)).hex(),digest=source_digest(bytes(i%251 for i in range(size))).hex()) for size in [0,1,3,15,16,31,63,64,65,127,128,129,255,1024]]

# Decode the actual compact implementation, including classic height and exposed marker snapshot removal.
compact=[]
for name,raw,fmt in [
 ('implicit',bytes((0,62,62,239))+(0x80706050).to_bytes(4,'little'),'retail'),
 ('shifted',bytes((0,62,62,0))+(0x80706050).to_bytes(4,'little'),'retail'),
 ('marker',bytes((0,62,64,239))+b''.join(v.to_bytes(4,'little') for v in [0x800000f0,0x800000f0,0x80706050]),'retail'),
 ('classic', (bytes((0,63,63,0))+(0x800000f0).to_bytes(4,'little'))*(512*512),'classic64'),
 ('auto', (bytes((0,63,63,0))+(0x80706050).to_bytes(4,'little'))*(512*512),'auto')]:
 vxl=CompactVoxelMap(raw,source_format=fmt);coordinates=[(x,y,z) for x,y in [(0,0),(254,255),(255,255),(256,255),(511,511)] for z in [0,1,61,62,63,64,100,176,238,239]]
 compact.append(dict(name=name,raw_zlib=base64.b64encode(zlib.compress(raw)).decode(),format=fmt,shift=vxl.source_z_shift,probes=[[*at,vxl.get_solid(*at)] for at in coordinates],columns=[dict(x=x,y=y,surface=vxl.surface_z(x,y),mask=hex(vxl.column_mask(x,y))) for x,y in [(0,0),(255,255),(256,255),(511,511)]]))
result['compact']=compact

# Use one real immutable VXL base, then real deltas on that private worker collision map.
column=bytes([2,100,100,0,1,2,3,4,0,239,239,239,1,2,3,4]);raw=column*(32*32);world=SimpleVoxelWorld();world.load(MapSnapshot(7,10,raw,'tdm',source_format='retail'));world_cases=[]
commands=[dict(op='query',at=[245.5,245.5,97.75],goal=[260.5,245.5,100]),dict(op='delta',version=11,cells=[[250,245,z,True,0,9.] for z in range(94,100)]),dict(op='query',at=[245.5,245.5,97.75],goal=[260.5,245.5,100]),dict(op='delta',version=12,cells=[[250,245,z,False,0,0.] for z in range(94,100)]),dict(op='query',at=[245.5,245.5,97.75],goal=[260.5,245.5,100]),dict(op='delta',version=13,cells=[[243,247,100,False,0,0.],[243,247,101,False,0,0.],[243,247,102,False,0,0.]]),dict(op='query',at=[243.5,247.5,97.75],goal=[260.5,245.5,97.75])]
commands.extend([dict(op='delta',version=14,cells=[[x,246,90,True,0,5.] for x in range(244,263)]),dict(op='query',at=[245.5,246.5,97.75],goal=[260.5,246.5,100]),dict(op='query',at=[245.5,245.5,97.75],goal=[500.5,500.5,50])])
route=tuple(RouteStep((x+.5,245.5,97.75),MovementAffordance.WALK) for x in range(246,263))
for command in commands:
 if command['op']=='delta':world.apply(WorldDelta(7,command['version'],tuple(VoxelChange(*c) for c in command['cells'])));answer=dict(version=world.topology_version,health=world.block_health(250,245,97))
 else:
  at=command['at'];goal=command['goal'];surface=world.surface(int(at[0]),int(at[1]),at[2]);named=world.goal_surface(goal);search=world.begin_corridor(at,goal,blocked_edges=frozenset());slices=[]
  if search:
   while not search.done:search.advance();slices.append(search.expansions)
  answer=dict(surface=None if surface is None else asdict(surface),goal=None if named is None else asdict(named),sight=world.has_line_of_sight(at,goal),supports=world.standable_supports(int(at[0]),int(at[1])),path=[] if search is None else search.path,slices=slices,replan=[world.route_needs_replan(v,at,route,stuck=s) for v,s in [(10,False),(world.topology_version,False),(10,True)]],walk=path_following.straight_walkable(world,at,goal),lookahead=path_following.lookahead(world,route,0,at),run_end=path_following.run_end(route,0),gaze=path_following.gaze_waypoint(route,0,at),remaining=path_following.remaining_distance(route,0,at))
 world_cases.append(command|dict(result=answer))
result['world']=dict(raw_zlib=base64.b64encode(zlib.compress(raw)).decode(),commands=world_cases,route=[dict(waypoint=s.waypoint,affordance=s.affordance.value) for s in route])
# Exact edge/gaze/index helpers need no terrain substitution.
following=[]
for axis in [(1,0),(0,1),(-1,0),(0,-1),(1,1),(0,0)]:
 edge=((10,10,100),(10+axis[0],10+axis[1],99));step=RouteStep((edge[1][0]+.5,edge[1][1]+.5,96.75),MovementAffordance.JUMP,entry_edge=edge)
 for position in [(10.5,10.5,97.75),(10.3,11.3,97.75),(11.,11.,97.75),(13.5,13.5,97.75)]:following.append(dict(edge=edge,position=position,axis=path_following.edge_axis(step),alignment=path_following.takeoff_alignment(step,position)))
result['following']=following

print('Bot source observations:',len(profile_cases),'profile streams,',sum(len(x['commands']) for x in budget_cases),'budget boundaries,',len(sounds),'sensory actions,',len(steps),'adaptive updates,',len(atlases),'complete atlases,',len(compact),'compact snapshots,',len(world_cases),'world/corridor transitions')

# Retained bidirectional search through concave and unreachable dry supports, with late exclusions.
corridors=[]
for case in range(4):
 width=height=64;supports=bytearray([100])*(width*height)
 for x in range(width):supports[x]=supports[(height-1)*width+x]=239
 for y in range(height):supports[y*width]=supports[y*width+width-1]=239
 if case:
  for i,x in enumerate(range(8,60,8)):
   door=1 if i%2 else 62
   for y in range(1,height-1):
    if case==2 or y!=door:supports[y*width+x]=239
 search=SurfaceCorridorSearch(bytes(supports),width,height,width+1,(height-2)*width+width-2);slices=[];late=None
 while not search.done:
  search.advance();slices.append(search.expansions)
  if case==3 and len(slices)==1:
   late=((1,1,100),(2,1,100));search.exclude_edge(*late)
 corridors.append(dict(width=width,height=height,supports=base64.b64encode(supports).decode(),start=width+1,target=(height-2)*width+width-2,late_edge=late,slices=slices,path=search.path))
result['corridors']=corridors
(ROOT/'java/src/test/resources/golden/bot-foundations.json').write_text(json.dumps(result,indent=2,allow_nan=False)+'\n')
print(len(corridors),'retained bidirectional corridor searches')
