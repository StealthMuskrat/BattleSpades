"""Detached observations from the unchanged mode policies and bounded team memories."""
from pathlib import Path
import dataclasses,importlib.machinery,importlib.util,json,sys,random,math,base64,zlib
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
spec=importlib.machinery.ModuleSpec('server.bot_ai',loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/'server/bot_ai')];sys.modules['server.bot_ai']=importlib.util.module_from_spec(spec)
for name,folder in [('modes','modes'),('modes.unofficial','modes/unofficial')]:
 spec=importlib.machinery.ModuleSpec(name,loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/folder)];sys.modules[name]=importlib.util.module_from_spec(spec)
import shared.constants as C
from server.bot_ai import policies as P
from server.bot_ai.messages import PlayerSnapshot,PerceptionFrame,ObjectiveSnapshot,Stimulus,StimulusKind
from server.bot_ai.profiles import ProfileFactory
from server.bot_ai.fun_messages import FunState,FunPlayer,FunCargo
from server.bot_ai.team_tasks import TeamTasks,TeamProject,TaskStage
from server.bot_ai.behavior_memory import BehaviorMemory
from server.bot_ai.prefab_policy import BOT_PREFAB_BLOCK_COUNTS
from server.bot_ai.schematics.library import library as schematic_library
from modes.unofficial.catalog import CATALOG
from dataclasses import replace,asdict
constants={n.lower():int(getattr(C,n)) for n in ['CLASS_ZOMBIE','CLASS_FAST_ZOMBIE','CLASS_JUMP_ZOMBIE','BLOCK_TOOL','TEAM_NEUTRAL','DIAMOND_PICKUP','TEAM1','TEAM2','BOMB_PICKUP','SNIPER_TOOL','SNIPER2_TOOL','PREFAB_TOOL','LANDMINE_TOOL','MEDPACK_TOOL','HEALTH_CRATE','AMMO_CRATE','BLOCK_CRATE','SUPERSPADE_TOOL','ROCKET_TURRET_TOOL','RADAR_STATION_TOOL']}
constants.update(ctf_marker_delay=float(getattr(C,'INTEL_MINIMAP_EXPOSURE_TIME',30)),airstrike_radius=float(getattr(C,'AIRSTRIKE_EXPLOSION_RADIUS',6)),bomb_fuse=float(getattr(C,'BOMB_EXPLOSION_FUSE',10)),bomb_radius=float(getattr(C,'BOMB_EXPLOSION_RADIUS',7)),pickup_lock=float(getattr(C,'NO_PICKUP_AFTER_DROP_TIME',2.5)))
catalog=['# Unchanged source 546c548780c400f21d73ca6713f86194ceb3c844',*[k+' = '+str(v) for k,v in constants.items()]]
for code in [*P._MODE_STRATEGIES,*CATALOG]:
 s=P.mode_strategy_for(code);catalog+=['','[[strategies]]','code = '+json.dumps(s.code),'objective = '+json.dumps(s.objective),'posture = '+json.dumps(s.default_posture.value)]
for shape in schematic_library().values():
 catalog+=['','[[schematics]]']
 for key in ['name','layers','anchor','tags','purpose','description','ground_mode','max_fill','base_level','max_builders']:
  catalog.append(key+' = '+json.dumps(getattr(shape,key)))
 catalog+=['palette_chars = '+json.dumps(list(shape.palette)),'palette_tokens = '+json.dumps(list(shape.palette.values())),'occupant = '+json.dumps(shape.occupant or [])]
catalog+=['','[prefabs]',*[k+' = '+str(v) for k,v in BOT_PREFAB_BLOCK_COUNTS.items()]]
(ROOT/'java/src/main/resources/net/battlespades/catalog/bot-modes.toml').write_text('\n'.join(catalog)+'\n')
rng=random.Random(41);factory=ProfileFactory(37);profiles=[None,factory.create('casual'),factory.create('hard')]
def player(pid=0,team=None,at=None,**kw):
 team=int(C.TEAM1 if pid%2==0 else C.TEAM2) if team is None else team;at=at or (80.+pid*17,80.+pid*11,97.75)
 return replace(PlayerSnapshot(pid,1,team,0,True,True,at,at,(1.,0.,0.),(0.,0.,0.),100,int(C.BLOCK_TOOL),50,10,40,True,loadout=(int(C.BLOCK_TOOL),int(C.SPADE_TOOL)),life_id=1),**kw)
def objective(kind,team,at,**kw):return ObjectiveSnapshot(kind,team,at,**kw)
def reset_policies():P._POLICIES['ctf']=P.CTFBotPolicy();P._POLICIES['cctf']=P.CTFBotPolicy();P._POLICIES['oc']=P.OccupationBotPolicy()
def memory_state(memory):return [dict(id=k[0],generation=k[1],decision=v.decision,role_since=v.role_since,anchor_since=v.anchor_since,last_seen=v.last_seen) for k,v in memory._states.items()]
result=dict(source_commit='546c548780c400f21d73ca6713f86194ceb3c844',constants=constants,strategies=[dict(input=code,canonical=P.canonical_mode_id(code),strategy=P.mode_strategy_for(code)) for code in [*P._MODE_STRATEGIES,*CATALOG,*['hc_'+c for c in ['tdm','ctf','cctf','zom','vip','mh','tc','dia','dem','oc']], ' Classic CTF ','multi-hill','Hardcore TDM','unknown']])
traces=[]
for code in [*P._MODE_STRATEGIES,'hc_ctf','hc_vip','hc_zom','unknown']:
 reset_policies();memory=P.ModePolicyMemory();rows=[]
 for i in range(80):
  now=100+i*.6;phase=['waiting','building','active','airstrike'][i//20] if code=='dem' else 'active' if i>=5 else 'waiting';own=int(C.TEAM1);other=int(C.TEAM2);pid=(i//8%4)*2
  roster=[player(j,at=(60+j*31+(i%8)*1.3,80+j*7,97.75),health=35 if j==pid and i%9<3 else 100,last_damage_at=now-2 if j==pid and i%9<3 else 0.,last_damage_source_id=1,last_damage_source_position=(150.,90.,97.75) if i%2 else None) for j in range(10)]
  obs=roster[pid];base=code.removeprefix('hc_');objects=[objective('team_anchor',own,(40.,80.,97.75)),objective('team_anchor',other,(420.,80.,97.75))]
  if base in ['ctf','cctf']:
   state=(i//8)%5;carrier=1 if state in [1,2] else -1;enemy_carrier=2 if state in [2,3] else -1
   objects +=[objective('ctf_base',own,(40.,80.,97.75)),objective('ctf_base',other,(420.,80.,97.75)),objective('ctf_intel',own,((200+i*2.,80.,97.75) if carrier>=0 else (140.,80.,97.75) if state==4 else (40.,80.,97.75)),carrier_id=carrier,state=1 if state==4 else 0),objective('ctf_intel',other,(420-i*1.,80.,97.75) if enemy_carrier>=0 else (420.,80.,97.75),carrier_id=enemy_carrier,state=i%2)]
   if i%13==0:obs=replace(obs,carried_entity_id=12)
  elif base=='zom':
   obs=replace(obs,class_id=int(C.CLASS_ZOMBIE) if i%4<2 else 0);objects +=[objective('zombie_refuge',own,(130.,100.,85.75))]
   if i%7<3:objects +=[objective('last_survivor',other if i%2 else own,(190.,100.,90.),carrier_id=pid if i%2==0 else 1)]
   if i%9<6:objects +=[objective('zombie_order',own,(145.,100.,97.75),carrier_id=pid,state=i%6,attacker=1)]
  elif base=='vip':
   if i%10<8:objects +=[objective('vip',own,(130.+i,90.,97.75),carrier_id=0)]
   if i%12<10:objects +=[objective('vip',other,(220.+i*.3,95.,97.75),carrier_id=1)]
  elif base=='mh':objects +=[objective('mh_hill',own if j%2 else other,(90.+j*65,80.,97.75),state=(i//5+j)%2,expires_in=(i%20)-3.+j*8) for j in range(3)]
  elif base=='dem':objects +=[objective('dem_base',own,(80.,80.,97.75),state=(i*7)%101,repair_cells=((80,80,98),(80,81,99)) if i%3 else ()),objective('dem_base',other,(300.,80.,97.75),state=100 if phase=='airstrike' else 5,cells=((300,80,99),(301,80,99)))]
  elif base=='tc':objects +=[objective('tc_territory',own if j%2 else other,(90.+j*60,80.,97.75),state=(i//5+j)%2,attacker=other if i%3 else -1) for j in range(4)]
  elif base=='dia':
   objects +=[objective('dia_dropoff',int(C.TEAM_NEUTRAL),(300.,80.,97.75),state=i%2)]
   if i%4:objects +=[objective('dia_diamond',own,(100.,90.,97.75),carrier_id=-1 if i%4==1 else 2)]
   if i%7==0:obs=replace(obs,carried_entity_id=int(C.DIAMOND_PICKUP))
  elif base=='oc':
   objects +=[objective('oc_target',other,(300.,80.,97.75),bounds=(290,310,70,90))]
   stage=i%16
   if stage<14:objects +=[objective('oc_bomb',own if stage<9 else other,(80.+stage*17,80.,97.75),carrier_id=-1 if stage<5 or stage>=12 else 2 if stage<9 else 1,state=int(stage>=3))]
   if stage in [6,7,8,10]:obs=replace(obs,carried_entity_id=int(C.BOMB_PICKUP),team=own if stage<9 else other)
  if i==47:obs=replace(obs,life_id=2)
  roster=[obs if p.player_id==pid else p for p in roster]
  f=PerceptionFrame(i+1,7 if i<60 else 8,1 if i<50 else 2,0,pid,obs.generation,now,code,tuple(roster),profile=profiles[i%3],objectives=tuple(objects),mode_phase=phase)
  decision=P.objective_decision_for(f,obs);committed=memory.decide(f,obs);targets=[player(31,other,(obs.position[0]+g,obs.position[1],obs.position[2])) for g in [3.,20.,100.]]
  rows.append(dict(frame=f,observer=obs,raw=decision,standing=P.standing_order_for(f,obs),decision=committed,committed=P.mode_objective_committed(committed),combat=[P.mode_decision_allows_combat(committed,obs,t,now=now) for t in targets],targets=targets,memory=memory_state(memory)))
 traces.append(dict(code=code,rows=rows))
result['traces']=traces
# Community modes use only explicitly sanctioned FunState data.
fun=[]
for code in CATALOG:
 for i in range(12):
  obs=player(i%6);mine=FunPlayer(obs.player_id,i!=0,checkpoint=0,prisoner=i==1)
  state=FunState(1,int(C.TEAM1),(256.,256.,97.75),((int(C.TEAM1),(50.,50.,97.75)),(int(C.TEAM2),(450.,450.,97.75))),(mine,FunPlayer(21,True,prisoner=True)),cargo=(FunCargo((110.,100.,97.75),int(C.TEAM1),holder=obs.player_id if i in [4,5] else -1,hidden=i==3),FunCargo((200.,200.,97.75),int(C.TEAM2),value=3,ready=i!=2)),exit=(400.,400.,97.75),exit_open=i%2==0,stage=i%4,red=i%2==0,light_remaining=i*.3,checkpoints=((50.,50.,97.75),(100.,100.,97.75)),cores=((int(C.TEAM1),(50,50,97)),(int(C.TEAM2),(450,450,97))),repair_cells=((70,70,99),),paint_bounds=(10,30,10,30,100),holder=obs.player_id if i%2 else -1,clue_until=120,clues=((150.,150.,97.75),),hunt_points=() if i%2 else ((100.,110.,97.75),),stashed=() if i%2 else (obs.team,),paused=i==2)
  f=PerceptionFrame(i,7,1,0,obs.player_id,1,100+i*3.,code,(obs,player(21,obs.team)),fun=state,mode_phase='prepare' if i<4 else 'live')
  fun.append(dict(frame=f,observer=obs,decision=P.objective_decision_for(f,obs)))
result['fun']=fun
# Shared lease and mutation limits retain insertion order and conservative bucket coalescing.
tasks=TeamTasks();task_rows=[]
for i in range(80):
 p=TeamProject(i,i%2,'rampart' if i%3 else 'cover',(i%20,1,1),(float(i%10*10),float(i//10*10),97.75),(300.,80.,97.75),i*.1,10+i*.1,cells=((i%12,5,99),),last_progress_at=i*.1);p.participants[(100,1,1)]=3+i*.1
 accepted=tasks.reserve(p);now=i*.2;observed={(i%20,1):(i%4!=0,2 if i%5==0 else 1)};tasks.expire(now,observed);allowed=tasks.allow_mutation(i%2,now,1+i%5)
 if allowed:tasks.record_mutation(i%2,now,1+i%5)
 tasks.event('accepted' if accepted else 'rejected',i,'fixture',now)
 task_rows.append(dict(project=p,accepted=accepted,now=now,observed=[[*k,*v] for k,v in observed.items()],allowed=allowed,projects=list(tasks.projects),history={str(k):list(v) for k,v in tasks.mutation_times.items()},metrics=dict(tasks.metrics),events=list(tasks.events)))
result['tasks']=task_rows
memory=BehaviorMemory();memory_rows=[]
for i in range(45):
 now=100+i*.5;obs=player(0,last_damage_at=now-.1 if i%4==0 else 0.)
 stimuli=tuple(Stimulus(list(StimulusKind)[j%len(StimulusKind)],(60.+j*3,80.,97.75),now-.2,now+2+(j%7),j,int(C.TEAM2) if j%3 else int(C.TEAM1),j*.2) for j in range(32));visible=player(1) if i%3==0 else None
 f=PerceptionFrame(i,7,1,0,0,1,now,'tdm',(obs,),stimuli=stimuli);memory.observe(f,obs,visible);memory.record('cover' if i%2 else 'bridge',(60.+i%3,80.,97.75),now,i%4==0)
 memory_rows.append(dict(frame=f,observer=obs,visible=visible,record=dict(kind='cover' if i%2 else 'bridge',position=(60.+i%3,80.,97.75),now=now,success=i%4==0),contact=memory.contact(obs.position,now),penalty=memory.penalty('cover',(60.,80.,97.75),now),contacts=[dict(key=k,value=v) for k,v in memory.contacts.items()],outcomes=list(memory.outcomes),pressure=memory.pressure,updated_at=memory.updated_at))
result['behavior']=memory_rows
# Repeated immutable inputs are stored once; every output boundary is retained.
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
(ROOT/'java/src/test/resources/golden/bot-modes.json').write_text(json.dumps(dict(encoding='zlib-json-v1',data=base64.b64encode(zlib.compress(payload,9)).decode()),separators=(',',':'))+'\n')
print('Mode source:',sum(len(t['rows']) for t in traces),'role/memory boundaries,',len(fun),'community decisions,',len(task_rows),'team leases,',len(memory_rows),'sensory boundaries')
