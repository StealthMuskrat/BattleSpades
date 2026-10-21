"""Observe unchanged director scalar controllers without loading the native server."""
from pathlib import Path
import ast,json,math,random,types
ROOT=Path(__file__).resolve().parents[2]
source=ast.parse((ROOT/'server/bot_ai/director.py').read_text())
names={'_wrap','_hold_aim','_update_aim','_update_purposeful_gaze','_smooth_gaze_axis','_second_order_axis','_movement_keys'}
node=next(n for n in source.body if isinstance(n,ast.ClassDef) and n.name=='BotDirector')
methods=[n for n in node.body if isinstance(n,ast.FunctionDef) and n.name in names]
assert {n.name for n in methods}==names
module=ast.Module(body=[ast.ImportFrom(module='__future__',names=[ast.alias(name='annotations')],level=0),ast.ClassDef(name='Director',bases=[],keywords=[],body=methods,decorator_list=[])],type_ignores=[])
env={'math':math};exec(compile(ast.fix_missing_locations(module),'<unchanged director methods>','exec'),env)
director=env['Director']()
result={'definitions':[],'source_commit':'546c548780c400f21d73ca6713f86194ceb3c844','aim':[],'keys':[]}
for skill in (.25,.85):
 for purpose in ('travel','focus','traverse','combat'):
  profile=types.SimpleNamespace(skill=skill,caution=.5,turn_speed=3.8,turn_acceleration=13.,aim_noise=.055)
  motor=types.SimpleNamespace(yaw=2.9,pitch=0.,yaw_velocity=0.,pitch_velocity=0.,yaw_noise=0.,pitch_noise=0.,yaw_acceleration=0.,pitch_acceleration=0.,gaze_purpose='',gaze_yaw=None,gaze_pitch=None,gaze_age=0.)
  orientations=[]
  player=types.SimpleNamespace(eye_x=10.,eye_y=10.,eye_z=30.,set_orientation_vector=lambda *v:orientations.append(v))
  runtime=types.SimpleNamespace(player=player,motor=motor,profile=profile,rng=random.Random(421),key_residual=(0.,0.))
  rows=[]
  for tick in range(96):
   target=((4.,12.,30.) if tick<24 else (15.,20.,28.) if tick<48 else (10.,10.,5.) if tick<72 else (10.,10.,30.))
   dt=(1/60,.1,.25,.033)[tick%4]
   director._update_aim(runtime,target,dt,noise_factor=.7,purpose=purpose)
   rows.append({'target':target,'dt':dt,'state':[motor.yaw,motor.pitch,motor.yaw_velocity,motor.pitch_velocity,motor.yaw_acceleration,motor.pitch_acceleration,motor.yaw_noise,motor.pitch_noise,motor.gaze_purpose,motor.gaze_yaw,motor.gaze_pitch,motor.gaze_age]})
  result['aim'].append({'skill':skill,'purpose':purpose,'rows':rows})
runtime=types.SimpleNamespace(key_residual=(0.,0.))
for tick in range(600):
 angle=math.radians((20,67,-135,178)[tick//150]);length=0 if tick%43==0 else 1
 forward,side=math.cos(angle)*length,math.sin(angle)*length
 keys=director._movement_keys(runtime,forward,side)
 result['keys'].append({'forward':forward,'side':side,'keys':keys,'residual':runtime.key_residual})
(ROOT/'java/src/test/resources/golden/bot-gateway.json').write_text(json.dumps(result,separators=(',',':'),allow_nan=False)+'\n')
print('Observed 768 aim frames and 600 feathered key frames from unchanged director methods')
