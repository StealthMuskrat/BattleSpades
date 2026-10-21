"""Unchanged director population/class and staged perception policies on detached inputs."""
from pathlib import Path
import ast,asyncio,base64,dataclasses,enum,importlib.machinery,importlib.util,json,math,random,sys,time,types,zlib,logging
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
spec=importlib.machinery.ModuleSpec('server.bot_ai',loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/'server/bot_ai')];sys.modules['server.bot_ai']=importlib.util.module_from_spec(spec)
import shared.constants as C
from server.bot_ai.messages import *
from server.bot_ai.profiles import BotProfile
source=ast.parse((ROOT/'server/bot_ai/director.py').read_text());node=next(n for n in source.body if isinstance(n,ast.ClassDef) and n.name=='BotDirector')
names={'_maintain_population','make_room_for_human','_safe_to_retire','_mode_retire_rank','_players_for_observer','_select_perception_entities','_publish_due_perception'}
methods=[n for n in node.body if isinstance(n,(ast.FunctionDef,ast.AsyncFunctionDef)) and n.name in names];assert {n.name for n in methods}==names
choose=next(n for n in node.body if isinstance(n,ast.FunctionDef) and n.name=='_choose_class');first=next(i for i,n in enumerate(choose.body) if isinstance(n,ast.If) and ast.unparse(n.test)=='not candidates');tail=ast.FunctionDef(name='_class_tail',args=ast.arguments(posonlyargs=[],args=[ast.arg(arg=n) for n in ['self','team','profile','mode','hardcore','candidates','requested_class']],kwonlyargs=[],kw_defaults=[],defaults=[]),body=choose.body[first:],decorator_list=[]);methods.append(tail)
env=dict(math=math,time=time,C=C,TEAM1=2,TEAM2=3,_PLAYABLE_TEAMS=(2,3),_MAX_PERCEPTION_PLAYERS=32,_MAX_PERCEPTION_ENTITIES=192,_ENTITY_RANK_CACHE_SECONDS=.5,_PERCEPTION_PUBLISH_BUDGET_SHARE=.55,_PERCEPTION_SNAPSHOT_BATCH=12,PerceptionFrame=PerceptionFrame,logger=logging.getLogger('owner-fixture'))
module=ast.Module(body=[ast.ImportFrom(module='__future__',names=[ast.alias(name='annotations')],level=0),ast.ClassDef(name='BotDirector',bases=[],keywords=[],body=methods,decorator_list=[])],type_ignores=[]);exec(compile(ast.fix_missing_locations(module),'<unchanged director owner methods>','exec'),env);D=env['BotDirector']
def clean(v):
 if dataclasses.is_dataclass(v):return {f.name:clean(getattr(v,f.name)) for f in dataclasses.fields(v)}
 if isinstance(v,enum.Enum):return v.value
 if isinstance(v,dict):return {str(k):clean(x) for k,x in v.items()}
 if isinstance(v,(tuple,list,set,frozenset)):return [clean(x) for x in v]
 return v
profile=BotProfile('Bot','normal',.55,.55,.5,.55,.5,.32,.12,3.8,13,.6,.6,24,.055,())
def snapshot(pid,**kw):
 from dataclasses import replace
 return replace(PlayerSnapshot(pid,1,2+pid%2,0,True,True,(240.+pid,250.5,37.75),(240.+pid,250.5,37.75),(1.,0.,0.),(0.,0.,0.),100,6,50,10,40,pid<6,loadout=(6,0,5),weapon_tool=6,life_id=1),**kw)
result=dict(definitions=[],source_commit='546c548780c400f21d73ca6713f86194ceb3c844',population=[],classes=[],cohorts=[],entities=[],perception=[])
def row(p):return dict(id=p.id,generation=1,team=p.team,class_id=p.class_id,bot=p.is_bot,alive=p.alive,carries=p.pickup_id is not None,visible_combat=p.visible,vip=p.vip,sole_patient_zero=p.zero,unsafe=p.unsafe)
def native(pid,bot=False,**kw):return types.SimpleNamespace(id=pid,name='Bot'+str(pid),team=kw.get('team',2+pid%2),class_id=kw.get('class_id',pid%4),is_bot=bot,alive=kw.get('alive',True),pickup_id=1 if kw.get('carries',False) else None,visible=kw.get('visible',False),vip=kw.get('vip',False),zero=kw.get('zero',False),unsafe=kw.get('unsafe',False))
for mode in ['backfill','fixed','admin']:
 d=D();config=types.SimpleNamespace(population_mode=mode,fill_target=5,max_bots=6,reserve_human_slots=2);d.server=types.SimpleNamespace(config=types.SimpleNamespace(bots=config,max_players=10),players={},reserved_player_ids=set(),mode=types.SimpleNamespace(owns_bot_population=False,vips={},patient_zero_ids=set(),bot_retire_safe=lambda p:not p.unsafe,bot_retire_rank=lambda p:p.class_id%3));d._config=config;d._reconnect_count=None;d._next_population_at=0.;d.bots=[];d._runtime={};events=[]
 async def add():
  pid=next((i for i in range(10) if i not in d.server.players and i not in d.server.reserved_player_ids),-1)
  if pid<0:return None
  p=native(pid,True,alive=pid%3!=0,carries=pid%5==0);d.server.players[pid]=p;d.bots.append(p);events.append(['add',pid]);return p
 async def remove(p,force=False):
  if not force and not d._safe_to_retire(p):return False
  d.bots.remove(p);d.server.players.pop(p.id,None);events.append(['remove',p.id,force]);return True
 d.add_bot=add;d.remove_bot=remove;rows=[]
 for i in range(20):
  now=100+i*.4;humans=[native(8,team=0 if i%3==0 else 3)] if i<12 else [native(7),native(8),native(9)];d.server.players={p.id:p for p in [*d.bots,*humans]};d.server.reserved_player_ids={6} if i%4==0 else set();d.server.mode.owns_bot_population=i==9;d._reconnect_count=0 if i==10 else None;d._runtime={p.id:types.SimpleNamespace(intent=types.SimpleNamespace(look=types.SimpleNamespace(visible=p.visible))) for p in d.bots};before=[row(p) for p in d.server.players.values()];events.clear();asyncio.run(d._maintain_population(now));room=asyncio.run(d.make_room_for_human()) if i%7==6 else None;rows.append(dict(now=now,players=before,loading=len(d.server.reserved_player_ids),owns=d.server.mode.owns_bot_population,reconnecting=d._reconnect_count is not None,room=room,make_room=i%7==6,events=list(events),result=[row(p) for p in d.server.players.values()]))
 result['population'].append(dict(mode=mode,rows=rows))
for mode in ['tdm','ctf','cctf','arena','zom','tc']:
 for hardcore in [False,True]:
  d=D();d._rng=random.Random(71);members=[native(i,class_id=[0,1,12,3,2,16,17][i%7]) for i in range(14)];d.server=types.SimpleNamespace(teams={2:types.SimpleNamespace(players=members)});choices=[];candidates=[0,1,12,3,2,16,17];preferred=(12,17)
  for i in range(64):
   requested=candidates[i%7] if i%13==0 else None;answer=d._class_tail(2,types.SimpleNamespace(class_preferences=preferred),mode,hardcore,candidates,requested);choices.append(dict(requested=requested,result=answer))
  result['classes'].append(dict(mode=mode,hardcore=hardcore,candidates=candidates,counts={cid:sum(p.class_id==cid for p in members) for cid in candidates},preferred=preferred,rows=choices))
players=tuple(snapshot(i,alive=i%7!=0,spawned=i%9!=0,carried_entity_id=220 if i==1 else -1) for i in range(96));objectives=(ObjectiveSnapshot('vip',2,(1.,1.,1.),carrier_id=70),ObjectiveSnapshot('ctf_intel',3,(1.,1.,1.),carrier_id=80))
for observer in range(0,96,3):result['cohorts'].append(dict(players=players,objectives=objectives,observer=observer,result=D._players_for_observer(players,observer_id=observer,objectives=objectives)))
d=D();d.server=types.SimpleNamespace(metrics=None);live={p.player_id:types.SimpleNamespace(position=p.position,alive=p.alive,spawned=p.spawned,pickup_id=220 if p.player_id==1 else None) for p in players};real_clock=time.monotonic
try:
 for i in range(24):
  now=200+i*.1;time.monotonic=lambda:now;entities=tuple(EntitySnapshot(j,0,-1,-1,(float((j+i)%300),250.,38.),True,kind=('objective','projectile','pickup','blockfire','crate')[j%5],hazardous=j%17==0) for j in range(240+i%3));answer=d._select_perception_entities(list(entities),live);result['entities'].append(dict(now=now,players=players,entities=entities,result=answer))
finally:time.monotonic=real_clock
actors=[types.SimpleNamespace(id=p.player_id,position=p.position,team=p.team,snapshot=p) for p in players];d=D();d.server=types.SimpleNamespace(mode=types.SimpleNamespace(),players={p.id:p for p in actors},config=types.SimpleNamespace(bots=types.SimpleNamespace(perception_hz=10,main_thread_budget_ms=.75,behavior_version='classic',friendly_mischief=True)),bot_stimuli=None);d._config=d.server.config.bots;d.bots=actors[:6];d._runtime={p.id:types.SimpleNamespace(next_perception_at=0.,generation=1,profile=profile) for p in d.bots};d._perception_cache_until=0.;d._perception_build_players=();d._perception_build_snapshots=[];d._perception_build_index=0;d._perception_players=();d._perception_entities=();d._perception_objectives=();d._perception_fun=None;d._frame_id=0;d._map_epoch=7;d._mode_epoch=1;d._topology_version=0;d._snapshot_player=lambda p:p.snapshot;d._snapshot_entities=lambda:();d._snapshot_objectives=lambda:objectives;d._mode_id=lambda:'tdm';d._mode_phase=lambda:'';frames=[];d.supervisor=types.SimpleNamespace(submit_frame=lambda f:frames.append(f));real_perf=time.perf_counter
try:
 for i in range(72):
  now=300+i/60;work=[0.]
  def clock():work[0]+=.0002;return work[0]
  time.perf_counter=clock;frames.clear();answer=d._publish_due_perception(now);result['perception'].append(dict(now=now,result=answer,frames=list(frames),next=[r.next_perception_at for r in d._runtime.values()]))
finally:time.perf_counter=real_perf
# Execute the two unchanged module-level policies. CPython 3.12 makes None's
# hash constant; normalize that one runtime primitive when exporting on 3.11.
from server.game_constants import WEAPON_PROFILES
from server.bot_ai.prefab_policy import bot_prefab_is_suitable,is_zombie_prefab,BOT_PREFAB_BLOCK_COUNTS
class HashNone:
 def __hash__(self):return 0xFCA86420
none=HashNone()
def hash312(state):return hash((state[0],state[1],none if state[2] is None else state[2]))
policy_env=dict(random=random,C=C,WEAPON_PROFILES=WEAPON_PROFILES,bot_prefab_is_suitable=bot_prefab_is_suitable,is_zombie_prefab=is_zombie_prefab,hash=hash312)
body=[ast.ImportFrom(module='__future__',names=[ast.alias(name='annotations')],level=0)]
for n in source.body:
 if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id in {'_BOT_TRAVERSAL_PREFAB_TOKENS','_BOT_COVER_PREFAB_TOKENS'} for t in n.targets):body.append(n)
 if isinstance(n,ast.FunctionDef) and n.name in {'_choose_bot_prefabs','_choose_bot_loadout'}:body.append(n)
exec(compile(ast.fix_missing_locations(ast.Module(body=body,type_ignores=[])),'<unchanged item policies; CPython 3.12 None hash>', 'exec'),policy_env)
result['selection']=[]
for cid in C.CLASS_ITEMS:
 rng=random.Random(71+int(cid));rows=[]
 for i in range(48):
  disabled=(5,7,60) if i%3==0 else ();available=sorted(BOT_PREFAB_BLOCK_COUNTS) if i%5 else sorted(n for n in BOT_PREFAB_BLOCK_COUNTS if is_zombie_prefab(n));limit=i%5;gaussian=rng.gauss(0,.1) if i%7==0 else None;before_hash=hash312(rng.getstate());prefabs=policy_env['_choose_bot_prefabs'](available,rng,limit=limit);loadout=policy_env['_choose_bot_loadout'](cid,rng,disabled_tools=frozenset(disabled));after_hash=hash312(rng.getstate());seed=rng.randrange(0,2**31);next_draw=rng.random();rows.append(dict(disabled=disabled,available=available,limit=limit,gaussian=gaussian,before_hash=before_hash,prefabs=prefabs,loadout=loadout,after_hash=after_hash,seed=seed,next=next_draw))
 result['selection'].append(dict(class_id=cid,rows=rows))
definitions=[];interned={}
def intern(value):
 if isinstance(value,dict):value={k:intern(v) for k,v in value.items()}
 elif isinstance(value,list):value=[intern(v) for v in value]
 else:return value
 key=json.dumps(value,sort_keys=True,separators=(',',':'))
 if len(key)<64:return value
 if key not in interned:interned[key]=len(definitions);definitions.append(value)
 return {'$ref':interned[key]}
data=clean(result);data.pop('definitions');data={k:intern(v) for k,v in data.items()};data['definitions']=definitions
payload=json.dumps(data,sort_keys=True,separators=(',',':')).encode();(ROOT/'java/src/test/resources/golden/bot-owner.json').write_text(json.dumps(dict(encoding='zlib-json-v1',data=base64.b64encode(zlib.compress(payload,9)).decode()),separators=(',',':'))+'\n');print('Observed 60 population, 768 class, 32 cohort, 24 entity overflow, 72 publication boundaries and',sum(len(x['rows']) for x in result['selection']),'item selections')
