"""Execute original WorldManager/spawn_selection/RoundLifecycle on compiled VXL.

Only input maps, clocks, seeds and mode proposals are controlled. No source
policy body is extracted, replaced or reimplemented by this generator.
"""
import gzip,hashlib,io,json,logging,math,pathlib,random,struct,sys
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from aoslib import vxl,world as native_world
from shared import constants as C
from server.world_manager import WorldManager
from server.map_metadata import MapZone,MapMetadata
from server import spawn_selection as S
from server.round_lifecycle import resolve_player_spawn
from server.combat_runtime import segment_clear
OUT=ROOT/'java/src/test/resources/golden'
assert sys.version_info[:2]==(3,12), 'Reference spawn arithmetic/selection requires Python 3.12'
logging.disable(logging.CRITICAL)  # Controlled raising mode proposals are expected oracle inputs.
def emit(name,rows):
 raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();out=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=out,mtime=0) as stream:stream.write(raw)
 (OUT/(name+'.tsv.gz')).write_bytes(out.getvalue());print(name,len(rows))
def nums(items):return ','.join(float(v).hex() for v in items)
def pos(p):return 'none' if p is None else nums(p)
def cols(candidates):return ';'.join(','.join(map(str,c)) for c in candidates) or '-'
def box(x0,x1,y0,y1,z0,z1,solid=1):return (x0,x1,y0,y1,z0,z1,solid)
def zone(team,x=36,y=36,z=94,z0=-12,z1=145,item='team_spawn_area',kind='spawn',size=8):return MapZone(kind,team,x,y,z,(-size,size,-size,size,z0,z1),item)
ground=[box(24,48,24,48,100,238),box(84,108,24,48,100,238),box(248,264,248,264,120,238)]
cases=[
 ('flat',ground,[zone(2),zone(3,96)],[]),
 ('interior',ground+[box(24,48,24,48,70,78)],[zone(2),zone(3,96)],[zone(2,36,36,100,-8,8,'base','base',4)]),
 ('storeys',ground+[box(24,48,24,48,60,68),box(24,48,24,48,80,88)],[zone(2,z=94,z0=-50),zone(3,96)],[]),
 ('sealed',[box(24,52,24,52,80,238),box(34,38,34,38,94,96,0),box(43,45,43,45,94,96,0)],[zone(2,z=96,z0=-4),zone(3,96)],[]),
 ('sea',[box(44,52,24,48,100,238)],[zone(3,z=230,z0=-4,z1=9,item='zombie_spawn_area'),zone(3,48,36,94,-12,145),zone(2,96)],[]),
 ('slopes',[box(x,x,24,48,100+(x%3)*3,238) for x in range(24,49)]+ground[1:],[],[]),
 ('edges',[box(1,8,1,8,0,238),box(12,20,1,8,1,238),box(24,32,1,8,238,238)],[zone(2,4,4,2,-4,238,size=3)],[]),
 ('empty',[],[],[]),
]
layout=[];rows=[];events=[];settle=[];rng=random.Random(168)
clock=[100.0];original_clock=S.time.monotonic;S.time.monotonic=lambda:clock[0]
try:
 for name,boxes,spawns,bases in cases:
  world=WorldManager(NS());world.map=vxl.VXL(-1,b'',0,2)
  for b in boxes:
   layout.append((name,'box',','.join(map(str,b))))
   x0,x1,y0,y1,z0,z1,solid=b
   for x in range(x0,x1+1):
    for y in range(y0,y1+1):
     for z in range(z0,z1+1):
      if solid:world.map.set_point(x,y,z,True,0x7f123456)
      else:world.map.remove_point(x,y,z)
  regions={2:(12,12,60,60),3:(72,12,120,60)}
  if name=='edges':regions={2:(1,1,36,12),3:(72,12,120,60)}
  world.map_metadata=MapMetadata(spawn_zones={t:[z for z in spawns if z.team==t] for t in [2,3]},base_zones={t:[z for z in bases if z.team==t] for t in [2,3]},fallback_spawn_regions=regions)
  zones=spawns+bases
  for z in zones:layout.append((name,'zone',','.join([z.kind,str(z.team),*map(str,(z.x,z.y,z.z,*z.extents)),z.item])))
  for t,r in regions.items():layout.append((name,'region',','.join(map(str,(t,*r)))))
  points=[(36.5,36.5,z) for z in [-240,-2.75,-0.75,57.25,67.25,77.25,91.25,94.25,97.25,100,236.25,237.25,239.9,240]]
  points += [(x,y,z) for x,y,z in [(0,0,100),(511.9,511.9,236.25),(512,32,100),(-0.1,32,100),(math.nan,32,100),(32,math.inf,100),(32,32,math.nan)]]
  points += [(rng.uniform(20,112),rng.uniform(20,52),rng.uniform(50,240)) for _ in range(180)]
  for p in points:
   for op,result in [('safe',world.spawn_position_is_safe(p)),('safe2',world.spawn_position_is_safe(p,team=2)),('safe3',world.spawn_position_is_safe(p,team=3))]:rows.append((name,op,nums(p),int(result)))
   if all(math.isfinite(v) for v in p):
    rows.append((name,'clear',nums(p),int(world._player_body_is_clear(*p))))
    rows.append((name,'support',nums(p),int(world._player_has_support(*p))))
  columns=[(x,y) for x in range(20,113,4) for y in range(20,53,4)]+[(0,0),(1,1),(4,4),(16,4),(28,4),(510,510),(511,511)]
  for x,y in columns:
   rows.append((name,'surface',f'{x},{y}',world._get_surface_z(x,y)))
   rows.append((name,'column',f'{x},{y},-1,1',int(world._safe_spawn_column(x,y))))
   rows.append((name,'candidate',f'{x},{y}',pos(world.spawn_candidate_position((x,y),spawns))))
   for index,z in enumerate(zones):
    rows.append((name,'column',f'{x},{y},{index},0',int(world._safe_spawn_column(x,y,authored_zone=z,reject_roofs=False))))
  for index,z in enumerate(zones):
   rows.append((name,'zone',str(index),cols(world._zone_candidates(z))))
   for x,y in [(36,36),(44,44),(4,4),(96,36)]:
    rows.append((name,'floors',f'{x},{y},{index}',','.join(map(str,world._zone_floor_levels(x,y,z))) or '-'))
    for floor in [0,1,60,70,80,97,100,110,238,239]:rows.append((name,'floor',f'{x},{y},{floor},{index}',int(world._safe_spawn_floor(x,y,floor,z))))
  for x,y,z in [(36,36,95),(44,44,95),(36,36,79),(0,0,0),(36,36,100)]:
   for limit in [0,16,64,128]:rows.append((name,'pocket',f'{x},{y},{z},{limit}',int(world._sealed_air_pocket(x,y,z,limit))))
  for team in [2,3,0]:
   rows.append((name,'fallback',str(team),cols(world._fallback_spawn_candidates(team))))
   rows.append((name,'authored',str(team),cols(world._zone_spawn_candidates(team))))
   rows.append((name,'candidates',str(team),cols(world._get_spawn_candidates(team))))
   rows.append((name,'anchor',str(team),pos(world.team_base_anchor(team))))
  for x,y in [(36.3,36.9),(44.1,44.9),(4,4),(60,60),(500,500)]:
   rows.append((name,'nearest',nums([x,y,6]),pos(world._nearest_safe_spawn_point(x,y,search=6))))
   rows.append((name,'dry',nums([x,y,24]),pos(world.dry_ground_anchor(x,y,24))))
  for _ in range(120):
   a=tuple(rng.uniform(20,110) for _ in range(2))+(rng.uniform(50,110),);b=tuple(rng.uniform(20,110) for _ in range(2))+(rng.uniform(50,110),)
   rows.append((name,'segment',nums([*a,*b]),int(segment_clear(world,a,b))))
  server=NS(world_manager=world,mode=NS(),players={});S.random.seed(1000);selector_rng=random.Random(168);score_rng=random.Random(376);clock[0]=100
  def event(op,args,expected):events.append((name,op,args,expected))
  for i in range(24):
   team=2+i%2;clock[0]=100+i*0.75
   player=NS(id=0,team=team,alive=False,spawned=False,position=(36.5,36.5,97.25))
   others=[NS(id=j+1,team=(2 if j%2 else 3),alive=j%7!=0,spawned=j%5!=0,position=(float(28+j*3),float(28+j%5*2),97.25),eye=(float(28+j*3),float(28+j%5*2),96.75)) for j in range(12)]
   server.players={p.id:p for p in [player,*others]}
   # Stable, explicit player input; source reads eye only for live/spawned rows.
   event('players',';'.join(','.join(map(str,[p.id,p.team,int(p.alive),int(p.spawned),*p.position,*getattr(p,'eye',p.position)])) for p in server.players.values()),'-')
   if i%4==0:
    dead=NS(team=team,position=(36.5+i%3,36.5,97.25));S.record_death(server,dead);event('death',nums([clock[0],team,*dead.position]),'-')
   chosen=S.choose_team_spawn(server,player,rng=selector_rng);event('choose',nums([clock[0],0]),pos(chosen))
   if chosen is not None:
    live=[p for p in others if p.alive and p.spawned and p.team in (2,3)]
    enemies=[p.eye for p in live if p.team!=team];allies=[p.eye for p in live if p.team==team]
    score=S._score(world,chosen,team,enemies,allies,S._intel(server),clock[0],score_rng)
    event('score',nums([clock[0],team,*chosen]),score.hex())
   if i%3==0:
    candidate=[(36.5,36.5,97.25),(36.5,36.5,236.25),(math.nan,0,0),None][(i//3)%4]
    event('rescue',str(team)+','+(nums(candidate) if candidate else 'none'),pos(S.rescue_spawn(server,player,candidate)))
    event('emergency',nums([clock[0],team]),pos(S.emergency_spawn(server,team)))
  # Per-mode identity resets memories and grid even on the same terrain.
  server.mode=NS();event('mode','new','-');clock[0]=130
  player=NS(id=0,team=2,alive=False,spawned=False,position=(36.5,36.5,97.25));server.players={0:player}
  event('players','0,2,0,0,36.5,36.5,97.25,36.5,36.5,97.25','-')
  placement=S.choose_team_spawn(server,player,rng=selector_rng);event('choose',nums([clock[0],0]),pos(placement))
  for cid in range(18):
   simulation=native_world.World(world.map);mover=native_world.Player(simulation)
   setters={'accel_multiplier':'ACCEL_MULTIPLIER','sprint_multiplier':'SPRINT_MULTIPLIER','crouch_sneak_multiplier':'CROUCH_SNEAK_MULTIPLIER','jump_multiplier':'JUMP_MULTIPLIER','water_friction':'WATER_FRICTION','falling_damage_min_distance':'FALLING_DAMAGE_MIN_DISTANCE','falling_damage_max_distance':'FALLING_DAMAGE_MAX_DISTANCE','falling_damage_max_damage':'FALLING_DAMAGE_MAX_DAMAGE','fall_on_water_damage_multiplier':'FALL_ON_WATER_DAMAGE_MULTIPLIER','can_sprint_uphill':'CAN_SPRINT_UPHILL'}
   for setter,table in setters.items():getattr(mover,'set_class_'+setter)(getattr(C,'CLASS_'+table)[cid])
   mover.set_position(*placement)
   for tick in range(60):
    result=mover.update(1/60,());p,v=mover.position,mover.velocity
    settle.append((name,cid,pos(placement),tick,result,nums([p.x,p.y,p.z,v.x,v.y,v.z]),','.join(str(int(v)) for v in [mover.airborne,mover.wade,mover.crouch,mover.jump,mover.jump_this_frame])))
  if name=='empty':
   clock[0]=140;event('emergency',nums([clock[0],2]),pos(S.emergency_spawn(server,2)))
   for x in range(116,129):
    for y in range(116,129):
     for z in range(100,239):world.set_block(x,y,z,True,0x123456)
   event('boxedit','116,128,116,128,100,238,1','-')
   for now in [141,145,155,156]:clock[0]=now;event('emergency',nums([clock[0],2]),pos(S.emergency_spawn(server,2,near=(120,120,100))));events[-1]=(name,'nearEmergency',nums([now,2,120,120,100]),events[-1][3])
  # Round resolver, including a raising proposal and team-aware water rescue.
  for candidate in [(36.5,36.5,97.25),(36.5,36.5,236.25),(math.inf,0,0),(1e308,1e308,0),None]:
   def proposal(p):
    if candidate is None:raise ValueError('controlled mode resolver failure')
    return candidate
   server.mode=NS(get_spawn_point=proposal)
   expected=resolve_player_spawn(server,player);event('resolve',str(player.team)+','+(nums(candidate) if candidate else 'raise'),pos(expected))
finally:S.time.monotonic=original_clock
emit('spawn-layouts',layout);emit('spawn-geometry',rows);emit('spawn-events',events);emit('spawn-settle',settle)
rows=[]
for seed in [0,1,-1,168,2**32,2**63-1,-2**63,2**128+168]:
 r=random.Random(seed)
 for i in range(2000):
  kind=i%6
  if kind==0:rows.append((seed,'random','-',r.random().hex()))
  elif kind==1:
   bits=i%33;rows.append((seed,'bits',bits,r.getrandbits(bits)))
  elif kind==2:
   n=[1,2,3,64,85,256,10000][i%7];rows.append((seed,'below',n,r.randrange(n)))
  elif kind==3:
   n=[1,8,21,22,85,86,500][i%7];k=min(n,[0,1,5,6,20][i%5]);rows.append((seed,'sample',f'{n},{k}',','.join(map(str,r.sample(list(range(n)),k))) or '-'))
  elif kind==4:
   a=list(range(i%31));r.shuffle(a);rows.append((seed,'shuffle',len(a),','.join(map(str,a)) or '-'))
  else:
   weights=[0.5,1.1,0.5+i%17];rows.append((seed,'weighted',nums(weights),r.choices([0,1,2],weights=weights)[0]))
emit('spawn-random',rows)
rows=[]
for _ in range(3000):
 a,b=rng.uniform(-1024,1024),rng.uniform(-1024,1024);rows.append((nums([a,b]),math.hypot(a,b).hex()))
for a in [0,1,math.inf,math.nan,sys.float_info.max,sys.float_info.min,5e-324,1e-320]:
 for b in [0,1,math.inf,math.nan,sys.float_info.max,sys.float_info.min,5e-324,1e-320]:rows.append((nums([a,b]),math.hypot(a,b).hex()))
emit('spawn-hypot',rows)
rows=[]
for _ in range(3000):
 values=[rng.uniform(-1024,1024),rng.uniform(-1024,1024),rng.uniform(-1024,1024)]
 rows.append((nums(values),float(sum(v*v for v in values)).hex()))
emit('spawn-sums',rows)
manifest={'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','cases':len(cases),'geometry_vectors':len((gzip.decompress((OUT/'spawn-geometry.tsv.gz').read_bytes())).splitlines()),'event_vectors':len(events),'random_vectors':16000,'hypot_vectors':3064,'sum_vectors':len(rows),'spawn_settle_ticks':len(settle),'policy_substitutions':False,'inputs':'compiled original VXL/world; explicit synthetic cuboids/layout; controlled monotonic clock, seeds and mode proposal callbacks','source_files':{p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/world_manager.py','server/spawn_selection.py','server/round_lifecycle.py','server/map_metadata.py','server/combat_runtime.py','aoslib/vxl.pyx','aoslib/world.pyx']},'fixtures':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(OUT.glob('spawn-*.tsv.gz'))}}
(OUT/'spawn-provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
