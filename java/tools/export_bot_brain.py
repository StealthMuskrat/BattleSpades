"""Unchanged source observations for the complete worker decision loop and class work.

Scripted body snapshots exercise policy/lifecycle boundaries; they do not assert
native movement acceptance or a live director integration.
"""
from pathlib import Path
import base64,dataclasses,enum,importlib.machinery,importlib.util,json,math,sys,zlib
from dataclasses import replace
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
for name,folder in [('server.bot_ai','server/bot_ai'),('modes','modes'),('modes.unofficial','modes/unofficial')]:
 spec=importlib.machinery.ModuleSpec(name,loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/folder)];sys.modules[name]=importlib.util.module_from_spec(spec)
from server.bot_ai import simple_worker as W,unofficial as U
from server.bot_ai.messages import *
from server.bot_ai.simple_navigation import SimpleVoxelWorld
from server.bot_ai.fun_messages import FunState,FunPlayer,FunCargo
from server.bot_ai.policies import ModeBotDecision,ModeBotPosture
from modes.unofficial.catalog import CATALOG
catalog=['# Unchanged source 546c548780c400f21d73ca6713f86194ceb3c844']
for code,spec in CATALOG.items():catalog+=['','['+code+']','building = '+str(spec.building).lower(),'digging = '+str(spec.digging).lower()]
(ROOT/'java/src/main/resources/net/battlespades/catalog/bot-community.toml').write_text('\n'.join(catalog)+'\n')
def clean(v):
 if dataclasses.is_dataclass(v):return {f.name:clean(getattr(v,f.name)) for f in dataclasses.fields(v)}
 if isinstance(v,enum.Enum):return v.value
 if isinstance(v,dict):return {str(k):clean(x) for k,x in v.items()}
 if isinstance(v,(tuple,list,set,frozenset)):return [clean(x) for x in v]
 if isinstance(v,float) and not math.isfinite(v):return 'infinity' if v>0 else '-infinity'
 return v
raw=bytes([2,40,40,0,1,2,3,4,0,239,239,239,1,2,3,4])*(64*64)
wet=bytes([2,236,236,0,1,2,3,4,0,239,239,239,1,2,3,4])*(64*64)
def world(water=False):
 w=SimpleVoxelWorld();w.load(MapSnapshot(7,0,wet if water else raw,'tdm',source_format='retail'));return w
def actor(pid=1,**kw):return replace(PlayerSnapshot(pid,1,2,0,True,True,(250.5,250.5,37.75),(250.5,250.5,37.75),(1.,0.,0.),(0.,0.,0.),100,6,50,10,40,True,loadout=(6,0,5),weapon_tool=6,life_id=1),**kw)
def frame(p,now,others=(),**kw):return PerceptionFrame(frame_id=int(round(now*100)),map_epoch=7,mode_epoch=1,observer_id=p.player_id,observer_generation=p.generation,created_at=now,mode_id=kw.pop('mode_id','tdm'),players=(p,*others),topology_version=0,**kw)
result=dict(definitions=[],source_commit='546c548780c400f21d73ca6713f86194ceb3c844',raw_zlib=base64.b64encode(zlib.compress(raw)).decode(),water_raw_zlib=base64.b64encode(zlib.compress(wet)).decode(),core=[],community=[],work=[])
real_clock=W.time.monotonic
try:
 for kind in ['patrol','visible','reload','carrier','water','lifecycle','cooperative','medic','guard']:
  w=world(kind=='water');brain=W.SimpleBotBrain(w);rows=[]
  for i in range(24):
   now=100+i*.2;W.time.monotonic=lambda:now;p=actor();others=();kwargs={}
   if kind=='visible':others=(actor(8,team=3,position=(261.5,250.5,37.75),eye=(261.5,250.5,37.75)),)
   if kind=='reload':p=replace(p,ammo_clip=2,ammo_reserve=40)
   if kind=='carrier':p=replace(p,carried_entity_id=3 if i<12 else -1,can_shoot=i>=12);kwargs=dict(mode_id='ctf',objectives=(ObjectiveSnapshot('intel',3,(270.5,250.5,37.75),carrier_id=1),ObjectiveSnapshot('intel',2,(240.5,250.5,37.75))))
   if kind=='water':p=replace(p,position=(220.5+i*.2,250.5,236.75),eye=(220.5+i*.2,250.5,236.75),wade=True,grounded=i%6==0)
   if kind=='lifecycle':p=replace(p,life_id=1+i//8,alive=i%8!=3,spawned=i%8!=4)
   if kind=='cooperative':kwargs=dict(behavior_version='cooperative')
   if kind=='medic':p=replace(p,loadout=(6,0,51));others=(actor(2,health=45,position=(253.5,250.5,37.75)),)
   if kind=='guard':kwargs=dict(mode_id='dia',objectives=(ObjectiveSnapshot('diamond_dropoff',2,(270.5,250.5,37.75)),))
   f=frame(p,now,others,**kwargs);intent=brain.decide(f);rows.append(dict(frame=f,intent=intent,metrics=W.behavior_metrics_snapshot(brain)))
  result['core'].append(dict(kind=kind,rows=rows));print('core',kind,flush=True)
 for code in CATALOG:
  for variant in range(2):
   w=world();brain=W.SimpleBotBrain(w);rows=[]
   for i in range(10):
    now=200+i*.2;W.time.monotonic=lambda:now;p=actor(loadout=(6,0,2,5,11,64),ammo_clip=0 if variant else 10);other=actor(8,team=3,position=(254.5,250.5,37.75),eye=(254.5,250.5,37.75));fun=FunState(1,3,(250.5,250.5,37.75),((2,(250.5,250.5,37.75)),(3,(270.5,250.5,37.75))),(FunPlayer(1,True,variant==1,checkpoint=i//5),FunPlayer(8,True,variant==1)),cargo=(FunCargo((252.5,250.5,37.75),3,holder=1 if variant else -1),),exit=(260.5,250.5,37.75),exit_open=variant==1,red=variant==1,light_remaining=2,checkpoints=((250.5,250.5,37.75),(257.5,250.5,37.75),(270.5,250.5,37.75)),route=tuple((250.5+j,250.5,37.75) for j in range(21)),cores=((3,(253,250,38)),),repair_cells=((252,250,39),),paint_bounds=(240,270,240,270,40),paint=(((252,250,39),3),),holder=1 if variant else 8,clue_until=210,hunt_points=((250.5,250.5,37.75),),stage=1)
    f=frame(p,now,(other,),mode_id=code,mode_phase='prepare' if i<3 else 'live',fun=fun);view,observer=U.combat_view(f,p);rows.append(dict(frame=f,intent=brain.decide(f),view=view,observer=observer))
   result['community'].append(dict(kind=code,variant=variant,rows=rows));print('community',code,variant,flush=True)
 for kind in ['medic','turret','mine','radar','disguise','cannon','dynamite','c4','owned_charge','objective_mine','repair','demolish']:
  w=world();edits=(VoxelChange(252,250,38,True,0x123456),) if kind in ('dynamite','c4','demolish') else ();w.apply(WorldDelta(7,0,edits));brain=W.SimpleBotBrain(w);state=W._BotState(7,1,1,-1,True);brain._map_epoch=7;brain._states[(1,1)]=state;rows=[]
  for i in range(12):
   now=300+i*.5;W.time.monotonic=lambda:now;tool={'medic':51,'turret':16,'mine':20,'radar':56,'disguise':64,'cannon':29,'dynamite':21,'c4':59}.get(kind,6);p=actor(loadout=(6,0,5,tool),deployable_stock=((tool,3),));others=(actor(2,health=40,position=(253.5,250.5,37.75)),) if kind=='medic' else ();decision=ModeBotDecision((253.5,250.5,37.75),'post',arrival_radius=4,posture=ModeBotPosture.DEFEND,objective_priority=.5,watch_position=(270.5,250.5,37.75));entities=()
   if kind in ('dynamite','c4','demolish'):decision=replace(decision,directive='demolish')
   if kind=='repair':decision=replace(decision,directive='repair')
   if kind=='owned_charge':entities=(EntitySnapshot(11,0,2,1,(252.5,250.5,37.75),True,tool_id=21,blast_radius=8,hazardous=True),)
   f=frame(p,now,others,entities=entities,objectives=(ObjectiveSnapshot('dem_base',2 if kind=='repair' else 3,(252.5,250.5,37.75),cells=((252,250,38),),repair_cells=((252,250,39),)),))
   if kind=='medic':intent=brain._medic_support_intent(f,p,state,now)
   elif kind in ('turret','mine','radar','disguise','cannon'):intent=brain._class_deploy_intent(f,p,state,now,decision)
   elif kind in ('dynamite','c4'):intent=brain._plant_charge_intent(f,p,state,now,decision)
   elif kind=='owned_charge':intent=brain._own_charge_intent(f,p,state,now,fighting=i%2==0)
   elif kind=='objective_mine':intent=brain._objective_mine_intent(f,p,state,now)
   else:intent=brain._objective_block_work_intent(f,p,state,now,decision)
   rows.append(dict(frame=f,decision=decision,intent=intent))
  result['work'].append(dict(kind=kind,edits=edits,rows=rows));print('work',kind,flush=True)
finally:W.time.monotonic=real_clock
payload=json.dumps(clean(result),sort_keys=True,separators=(',',':')).encode()
(ROOT/'java/src/test/resources/golden/bot-brain.json').write_text(json.dumps(dict(encoding='zlib-json-v1',data=base64.b64encode(zlib.compress(payload,9)).decode()),separators=(',',':'))+'\n')
print(json.dumps({key:sum(len(trace['rows']) for trace in result[key]) for key in ['core','community','work']}))
