"""Unchanged director methods on scripted live snapshots and primitive voxel worlds.

This isolates owner scheduling/input contracts without asserting native movement
acceptance. Every selected director method is executed from its original AST.
"""
from pathlib import Path
import ast,base64,dataclasses,enum,importlib.machinery,importlib.util,json,math,random,struct,sys,time,types,zlib,logging
from dataclasses import dataclass,field,replace
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
spec=importlib.machinery.ModuleSpec('server.bot_ai',loader=None,is_package=True);spec.submodule_search_locations=[str(ROOT/'server/bot_ai')];sys.modules['server.bot_ai']=importlib.util.module_from_spec(spec)
import shared.constants as C
from server.game_constants import WEAPON_PROFILES,SPADE_TOOL_IDS,MELEE_RANGE
from server.bot_ai.messages import *
from server.bot_ai.profiles import BotProfile
from server.bot_ai.combat_profiles import recoil_kick_for
source=ast.parse((ROOT/'server/bot_ai/director.py').read_text())
names={'_wrap','_hold_aim','_update_aim','_update_purposeful_gaze','_smooth_gaze_axis','_second_order_axis','_movement_keys','_drain_intents','_apply_motor','_recover_buried_bot','observe_player_physics','_brake_completed_water_movement','_clear_pending_action','_enforce_weapon_capability','_latch_action','_pending_look_goal','_lock_target','_live_aim_point','_fire_lane_clear','_try_pending_action','_breach_ray_hits_target','_facing_threshold','_execute_pending','drain_actions','_commit_pending_action','_execute_task_action','_record_action_result','_apply_recoil','_set_movement_state','_set_action_state','_waypoint_is_live','_step_under_low_roof','_has_travel_target','_travel_gaze_purpose','_travel_movement_direction','_live_movement_direction','_probe_surface_is_live','_can_shoot','_runtime_is_live','_player_generation_readonly'}
node=next(n for n in source.body if isinstance(n,ast.ClassDef) and n.name=='BotDirector');methods=[n for n in node.body if isinstance(n,ast.FunctionDef) and n.name in names];assert {n.name for n in methods}==names
constants={'_ACTION_CONVERGENCE_GRACE','_SWING_CADENCE_GRACE','_SWING_CADENCE_MAX_HOLD','_PLAYER_MELEE_ALIGNMENT','_LOCK_LEASE','_LOCK_SETTLE_TIME','_JUMP_PULSE_TICKS','_STEERING_PROBE_INTERVAL','_STEERING_PROBE_MAX_TRAVEL','_STEERING_PROBE_DEFER_TRAVEL','_MOVEMENT_PROBE_TICK_BUDGET','_BODY_RADIUS','_FLOAT32','_WALK_STEER_ANGLES','_WATER_STEER_ANGLES','_WALL_PROBE_INTERVAL','_WALL_PROBE_TICK_BUDGET'}
body=[ast.ImportFrom(module='__future__',names=[ast.alias(name='annotations')],level=0)]
for n in source.body:
 if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id in constants for t in n.targets):body.append(n)
 if isinstance(n,ast.FunctionDef) and n.name=='_player_life_id':body.append(n)
 if isinstance(n,ast.ClassDef) and n.name in {'_AimMotor','_RuntimeBot'}:body.append(n)
body.append(ast.ClassDef(name='BotDirector',bases=[],keywords=[],body=methods,decorator_list=[]))
module=types.ModuleType('_source_motor_fixture');sys.modules[module.__name__]=module;env=module.__dict__;env.update(math=math,random=random,struct=struct,time=time,dataclass=dataclass,field=field,replace=replace,C=C,WEAPON_PROFILES=WEAPON_PROFILES,SPADE_TOOL_IDS=SPADE_TOOL_IDS,MELEE_RANGE=MELEE_RANGE,BotAction=BotAction,BotActionKind=BotActionKind,BotIntentPriority=BotIntentPriority,MovementAffordance=MovementAffordance,recoil_kick_for=recoil_kick_for,logger=logging.getLogger('motor-fixture'))
exec(compile(ast.fix_missing_locations(ast.Module(body=body,type_ignores=[])),'<unchanged director motor methods>','exec'),env);Director=env['BotDirector']
class World:
 topology_version=0
 def __init__(self,kind='flat'):self.kind=kind
 def get_solid(self,x,y,z):
  if not(0<=x<512 and 0<=y<512 and 0<=z<240):return False
  if z==239:return True
  if self.kind=='water':return False
  if self.kind=='hole' and 252<=x<=254:return False
  if self.kind=='wall' and x==252 and 246<=y<=254 and 36<=z<=39:return True
  if self.kind=='roof' and x>=251 and z in (39,36):return True
  return z==40
 def get_height(self,x,y):return next((z for z in range(240) if self.get_solid(x,y,z)),239)
 def is_water_column(self,x,y):return self.get_height(x,y)>=239
 def clipbox(self,x,y,z):
  if x<0 or x>=512 or y<0 or y>=512:return True
  if z<0:return False
  h=math.floor(z)
  if h==239:h=238
  elif h>=240:return True
  return self.get_solid(math.floor(x),math.floor(y),h)
 def spawn_position_is_safe(self,p):return not self.is_water_column(math.floor(p[0]),math.floor(p[1])) and not any(self.clipbox(p[0],p[1],p[2]+dz) for dz in (0,1))
 def raycast(self,x,y,z,dx,dy,dz,distance):
  for i in range(1,int(distance/.05)+1):
   cell=(math.floor(x+dx*i*.05),math.floor(y+dy*i*.05),math.floor(z+dz*i*.05))
   if self.get_solid(*cell):return cell
  return None
profile=BotProfile('Bot','normal',.55,.55,.5,.55,.5,.32,.12,3.8,13,.6,.6,24,.055,())
def clean(v):
 if dataclasses.is_dataclass(v):return {f.name:clean(getattr(v,f.name)) for f in dataclasses.fields(v)}
 if isinstance(v,enum.Enum):return v.value
 if isinstance(v,dict):return {str(k):clean(x) for k,x in v.items()}
 if isinstance(v,(tuple,list,set,frozenset)):return [clean(x) for x in v]
 if isinstance(v,float) and not math.isfinite(v):return '-infinity' if v<0 else 'infinity'
 return v
class Player:
 def __init__(self,world):
  self.id=1;self.bot_generation=2;self.is_bot=True;self.alive=True;self.spawned=True;self.replication_generation=1;self.tool=6;self.x=250.5;self.y=250.5;self.z=37.75;self.eye_x=self.x;self.eye_y=self.y;self.eye_z=self.z;self.vx=self.vy=self.vz=0.;self.o_x=1.;self.o_y=self.o_z=0.;self.wade=False;self.grounded=True;self.jetpack_id=0;self.jetpack_fuel=100.;self.next_shot_time=0.;self.pickup_burdensome=False;self.input=types.SimpleNamespace(crouch=False,can_display_weapon=False);self.movement=(False,)*8;self.actions=(False,)*4;self.position=(self.x,self.y,self.z);self.connection=types.SimpleNamespace(server=None)
 def set_orientation_vector(self,*v):self.o_x,self.o_y,self.o_z=v
 def update_input(self,*v):self.movement=v;self.input.crouch=v[5]
 def update_action_input(self,primary,secondary,*,zoom,can_display_weapon,hover):self.actions=(primary,secondary,zoom,hover);self.input.can_display_weapon=can_display_weapon
 def is_weapon_tool(self):return self.tool in WEAPON_PROFILES
 def is_spade_tool(self):return self.tool in SPADE_TOOL_IDS
 def die(self,**kw):self.alive=False;self.spawned=False
 def snapshot(self):return PlayerSnapshot(self.id,self.bot_generation,2,0,self.alive,self.spawned,(self.x,self.y,self.z),(self.eye_x,self.eye_y,self.eye_z),(self.o_x,self.o_y,self.o_z),(self.vx,self.vy,self.vz),100,self.tool,50,10,40,True,loadout=(6,0,5),weapon_tool=6,life_id=self.replication_generation,can_shoot=not self.pickup_burdensome,grounded=self.grounded,wade=self.wade,jetpack_id=self.jetpack_id,jetpack_fuel=self.jetpack_fuel)
result=dict(definitions=[],source_commit='546c548780c400f21d73ca6713f86194ceb3c844',motor=[],steering=[])
real_clock=time.monotonic
try:
 for kind in ['travel','jump','water','flight','melee','visible','capability','expiry','buried','task']:
  world=World('water' if kind=='water' else 'flat');p=Player(world);d=Director();d.server=types.SimpleNamespace(players={1:p},world_manager=world,loop_count=0,mode=types.SimpleNamespace(shoot_with_intel=False),metrics=types.SimpleNamespace(record_subsystem=lambda *a:None));p.connection.server=d.server;runtime=env['_RuntimeBot'](p,2,profile,env['_AimMotor'](0.),random.Random(421));d._runtime={1:runtime};d._map_epoch=7;d._mode_epoch=1;d._topology_version=0;d._started=True;d._reconnect_count=None;d._pending_gateway_actions={};d.terrain_recoveries=0;executions=[]
  def execute(actor,action):executions.append(clean(action));return True
  def select(actor,tool):actor.tool=tool;return True
  d.gateway=types.SimpleNamespace(execute=execute,select_tool=select);d._player_generation_readonly=lambda target:target.bot_generation
  target=Player(world);target.id=8;target.bot_generation=3;target.x=260.5;target.eye_x=260.5;d.server.players[8]=target;rows=[]
  for i in range(80):
   dt=.1 if kind=='buried' else 1/30;now=100+i*dt;time.monotonic=lambda:now;d.server.loop_count=i*(6 if kind=='buried' else 2);d._movement_probes_this_tick=d._wall_probes_this_tick=0;d.server.bots=d
   p.x=250.5+min(i,15)*.04 if kind=='travel' else 250.5;p.position=(p.x,p.y,p.z)
   if kind=='water':world.kind='water' if i<36 else 'flat';p.wade=i<36;p.z=236.75 if i<36 else 37.75;p.position=(p.x,p.y,p.z)
   if kind=='flight':p.jetpack_id=66 if i<40 else 69
   if kind=='buried':p.z=238.;p.position=(p.x,p.y,p.z)
   if kind=='capability':p.pickup_burdensome=i>=20
   p.eye_x,p.eye_y,p.eye_z=p.x,p.y,p.z
   input_snapshot=p.snapshot();accepted=None;intent=None
   if i%4==0 and (kind!='expiry' or i<8):
    movement=MovementIntent(direction=(1.,0.,0.),jump=kind=='jump',affordance=MovementAffordance.JETPACK if kind=='flight' else MovementAffordance.SWIM if kind=='water' else MovementAffordance.JUMP if kind=='jump' else MovementAffordance.BREACH if kind=='melee' else MovementAffordance.WALK,travel_source=p.position if kind=='travel' else None,travel_waypoint=(253.5,250.5,37.75) if kind=='travel' else None)
    action=BotAction(BotActionKind.MELEE,tool_id=0,position=(252.5,250.5,38.5)) if kind=='melee' else BotAction(BotActionKind.FIRE,tool_id=6,burst=3,burst_pause=.1) if kind in ('visible','capability','expiry') else BotAction(BotActionKind.BUILD,tool_id=5,position=(252,250,39),request_id=7+i//16) if kind=='task' else BotAction()
    look=LookIntent((260.5,250.5,37.75),visible=True,target_player_id=8,target_generation=3) if kind in ('visible','capability','expiry') else LookIntent((256.5,250.5,p.z))
    intent=BotIntent(1,2,i+1,7,1,0,now,now+.4,movement,look,6,action,life_id=1)
    class Supervisor:
     def drain_intents(self,limit):return (intent,)
    d.supervisor=Supervisor();d._drain_intents(now)
   d._apply_motor(runtime,now,dt);d.drain_actions(1)
   rows.append(dict(now=now,dt=dt,world=world.kind,loop=d.server.loop_count,player=input_snapshot,intent=intent,movement=sum((1<<j) for j,v in enumerate(p.movement) if v),actions=p.actions,orientation=(p.o_x,p.o_y,p.o_z),tool=p.tool,pending=runtime.pending_action,feedback=(runtime.feedback_action_kind,runtime.feedback_action_accepted,runtime.feedback_action_position,runtime.feedback_action_frame,runtime.feedback_action_at,runtime.feedback_request_id,runtime.feedback_reason,runtime.feedback_task_accepted,runtime.feedback_task_at),executions=list(executions),wade_until=runtime.wade_observed_until,landed=runtime.water_landed_at))
  result['motor'].append(dict(kind=kind,rows=rows))
 for shape in ['flat','water','hole','wall','roof']:
  world=World(shape);p=Player(world);d=Director();d._movement_probes_this_tick=0;p.connection.server=types.SimpleNamespace(world_manager=world,bots=d);r=env['_RuntimeBot'](p,2,profile,env['_AimMotor'](0.),random.Random(421));rows=[]
  for i in range(128):
   p.x=250.4+(i%8)*.2;p.y=250.4+(i%3)*.1;p.z=236.75 if shape=='water' else 37.75;p.vx=(i%4)*.12;p.wade=shape=='water';p.input.crouch=i%7==0;p.position=(p.x,p.y,p.z);direction=(math.cos(i*.3),math.sin(i*.3),0.);affordance=list(MovementAffordance)[i%5];r.walk_drop_allowance=1+i%4;d._movement_probes_this_tick=0;now=300+i*.02
   result_direction=d._live_movement_direction(r,direction,affordance,now=now)
   rows.append(dict(player=p.snapshot(),crouch=p.input.crouch,direction=direction,affordance=affordance,drop=r.walk_drop_allowance,now=now,result=result_direction))
  result['steering'].append(dict(kind=shape,rows=rows))
finally:time.monotonic=real_clock
payload=json.dumps(clean(result),sort_keys=True,separators=(',',':')).encode();(ROOT/'java/src/test/resources/golden/bot-motor.json').write_text(json.dumps(dict(encoding='zlib-json-v1',data=base64.b64encode(zlib.compress(payload,9)).decode()),separators=(',',':'))+'\n');print('Observed 800 motor and 640 live steering frames')
