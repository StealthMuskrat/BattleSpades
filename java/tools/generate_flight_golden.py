"""Original connected Player resource/canopy methods and native mover, with explicit owner-row ports.

No production method is replaced. The existing infantry harness observes original
SimulationRuntime calls; owner-row notifications are controlled inputs, not a replication oracle.
"""
import gzip,hashlib,io,json,random,sys,time
from types import SimpleNamespace as NS
from generate_simulation_golden import Fixture,ROOT,OUT,clock,original,bits,num,scalar
from server.class_selection import ClassSelection
from server.flight_profile import profile_for_capability,profile_for

class FlightFixture(Fixture):
 def __init__(self,cid):
  super().__init__(cid);self.p.connection.flight_profile_capable=False;self.p.connection.flight_profile=profile_for_capability(0)
 def capture(self,lane,data):
  if bytes(data)[0] in (5,46,69):self.wire.append(lane+':'+bytes(data).hex())
 def flight(self):
  p=self.p
  values=[bits(p.jetpack_fuel),p.jetpack_active,p._jetpack_physics_active,p._jetpack_activation_defer_remaining,p._jetpack_exhaustion_tail_remaining,p._jetpack_requires_release,bits(p._hover_since),bits(p._jetpack_idle_seconds),p.parachute_active,p._parachute_physics_active,p._parachute_owner_state,p._parachute_unsent_frames,p._parachute_deploy_last_held,p._parachute_jump_last_held,p._parachute_deploy_pending,p._parachute_used_this_fall,p._parachute_open_frames,p._parachute_fall_touched,p._parachute_fallback_clock]
  values=[scalar(v) if not isinstance(v,str) else v for v in values]
  values+=[';'.join(str(label)+','+str(int(state)) for label,state in p._parachute_physics_schedule) or '-']
  e=p.last_parachute_event
  values+=[('-' if e is None else str(int(e['active']))+','+e['reason']+','+scalar(e['loop'])),profile_for(p).encode().hex(),bits(p._last_damage_at)]
  return '~'.join(values)
 def op(self,op,arg):
  a=arg.split(',');p=self.p;self.events=[];self.wire=[]
  if op=='equip':
   cid,pack,canopy=map(int,a);p.apply_class_selection(ClassSelection(cid,(5,6,*([pack] if pack else []),*([72] if canopy else []))));p.spawn(36,36,20)
  elif op=='negotiate':p.connection.flight_profile_capable=int(arg)>0;p.connection.flight_profile=profile_for_capability(int(arg))
  elif op=='rtt':p.connection.peer=None if arg=='-' else NS(roundTripTime=num(arg))
  elif op=='flightsettings':
   names=('jetpack_activation_defer_frames','jetpack_exhaustion_tail_frames','jetpack_handoff_latency_aware','parachute_min_deploy_clearance','parachute_max_open_seconds','parachute_max_rise_velocity','parachute_owner_handoff_frames')
   for index,(name,value) in enumerate(zip(names,a)):setattr(self.server.config,name, bool(int(value)) if index==2 else int(value) if index in (0,1,6) else num(value))
  elif op=='owner':p._note_parachute_owner_row()
  elif op=='velocity':p.velocity=tuple(num(x) for x in a)
  elif op=='hit':p.damage(num(arg),source=None,kill_type=6)
  elif op=='restock':p.restock_jetpack()
  else:
   result=super().op(op,arg);return (*result,self.flight())
  return self.snapshot(),';'.join(self.events) or '-', ';'.join(self.wire) or '-',self.flight()

def write_flight_chunks(data):
 directory=OUT/'flight-simulation';directory.mkdir(exist_ok=True)
 lines=data.splitlines(keepends=True);names=[];hashes={}
 for offset in range(0,len(lines),10000):
  name=f'part-{offset//10000:02d}.tsv.gz';buffer=io.BytesIO()
  with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(b''.join(lines[offset:offset+10000]))
  fixture=directory/name;fixture.write_bytes(buffer.getvalue());names.append(name)
  hashes[str(fixture.relative_to(OUT))]=hashlib.sha256(fixture.read_bytes()).hexdigest()
 index=directory/'index.txt';index.write_text('\n'.join(names)+'\n')
 hashes[str(index.relative_to(OUT))]=hashlib.sha256(index.read_bytes()).hexdigest()
 return hashes

def generate():
 rows=[]
 try:
  for group in range(44):
   cid=group%18;f=FlightFixture(cid);rng=random.Random(8725+group);label=100
   def emit(op,arg):rows.append((group,cid,op,arg,*f.op(op,arg)))
   def receive(n,move=0,actions=24):emit('receive',','.join(map(str,[n,move,actions,rng.randrange(256),bits(1),bits(0),bits(0)])))
   def tick():emit('tick',bits(1/60))
   def step(move=0,actions=24,owner=False):
    nonlocal label
    receive(label,move,actions);label+=1;tick()
    if owner:emit('owner','-')
   def equip(pack=0,canopy=0):emit('equip',f'{cid},{pack},{int(canopy)}')
   def settings(defer=2,tail=3,latency=1,clearance=6,maximum=30,rise=.05,handoff=3):emit('flightsettings',','.join(map(str,[defer,tail,int(latency),bits(clearance),bits(maximum),bits(rise),handoff])))
   if group<12:
    pack=66+group%4;equip(pack);emit('negotiate',str(group//4))
    for frame in range(950):
     if frame%173==0:emit('rtt',bits([8,13,150][(frame//173)%3]))
     step(1|16,152 if pack==69 else 24)
    for frame in range(360):
     if frame==30:emit('velocity',','.join(bits(x) for x in (0,0,4)))
     step(0,24)
    emit('restock','-');emit('god','0');emit('hit',bits(1))
    for frame in range(180):step(16 if frame<40 else 0,24)
    emit('spawn',','.join(bits(x) for x in (36,36,20)))
   elif group<24:
    equip(0,1);emit('negotiate',str((group-12)%3))
    mode=(group-12)//3
    if mode==1:settings(maximum=.4)
    if mode==2:emit('spawn',','.join(bits(x) for x in (36,36,95)))
    if mode==3:emit('spawn',','.join(bits(x) for x in (36,36,220)))
    for frame in range(1900 if mode==0 else 240):
     if mode==1 and frame==10:emit('velocity',','.join(bits(x) for x in (0,0,-.5)))
     step(16 if 3<=frame<15 else 0,152 if frame>=3 else 24,owner=frame%2==0)
    emit('god','0');emit('hit',bits(10000));emit('spawn',','.join(bits(x) for x in (36,36,20)))
   elif group<27:
    equip(0,1);emit('negotiate',str(group-24));emit('god','0')
    for frame in range(200):step(0,152 if frame>=65 else 24,owner=True)
   elif group<30:
    equip(66,1);emit('negotiate',str(group-27));settings(defer=0,tail=30,latency=0)
    for frame in range(300):step(16 if frame<280 else 0,152)
   elif group<33:
    equip(0,1);emit('negotiate',str(group-30));emit('rtt',bits(125));settings(maximum=.25,handoff=0)
    for frame in range(240):step(0,152 if frame>=2 else 24)
    # Explicit corners: a close ledge blocks deployment even when the centre is clear.
    emit('spawn',','.join(bits(x) for x in (36,36,20)));emit('cell','35,35,26,1')
    for frame in range(30):step(0,152)
   elif group<36:
    equip(66 if group==33 else 0,group!=33);emit('negotiate',str(group-33));settings(defer=30,tail=0)
    emit('settings',','.join([bits(.4),'0','1','8','1',bits(8),'4','1','6']))
    step(16,152)
    for i in range(90):receive(label,16,152);label+=1
    for frame in range(100):step(16,152,owner=True)
    for _ in range(40):tick()
    for frame in range(120):
     if frame%23:receive(label,16,152)
     if frame%29==0:receive(label-1,0,24)
     label+=1;tick()
   elif group<42:
    equip(66);emit('negotiate','0' if group<41 else '2')
    settings(defer=-10 if group==39 else 30 if group>=40 else 2,tail=40 if group==40 else 3,latency=group!=38)
    emit('rtt',bits([0,8,16.5,5000,250,5000][group-36]))
    for i in range(50):receive(label,16,24);label+=1
    for frame in range(180):step(16 if frame<160 else 0)
   else:
    equip(0,1);emit('negotiate','2');step();step()
    clearance=f.p._parachute_ground_clearance()
    settings(clearance=clearance+(1e-10 if group==43 else 0))
    for frame in range(20):step(32 if frame>=10 else 0,152)
   print('flight trace',group,'complete;',len(rows),'operations',flush=True)
  data=('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode();hashes=write_flight_chunks(data)
  sources=['server/player.py','server/simulation_runtime.py','server/handlers/movement.py','server/flight_profile.py','server/lag_compensation.py','shared/constants.py','aoslib/world.pyx','aoslib/vxl.pyx']
  (OUT/'flight-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','operations':len(rows),'groups':44,'method_substitutions':False,'source_files':{name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in sources},'fixtures':hashes,'data_sha256':hashlib.sha256(data).hexdigest(),'scope':'Original connected Player flight methods through SimulationRuntime and native mover; controlled equipment/profile/RTT/config/owner-row notifications; exact resource, canopy, body, pacing, observer and HP/Restock bytes. Owner-row queue calls are explicit ports, not a replication or real-client oracle.'},indent=2)+'\n')
  print('flight operations',len(rows),'parts',len(hashes)-1)
 finally:time.monotonic,time.time=original

def generate_impacts():
 rows=[]
 for cid in range(18):
  f=FlightFixture(cid)
  for capability in range(3):
   f.p.connection.flight_profile=profile_for_capability(capability)
   for gravity in (0,.125,.4,1,1.7,2):
    f.server.world_manager.world.set_gravity(gravity)
    for velocity in (-.1,0,.001,.05,.2,.5,1,3):
     for active in (False,True):
      for z in (20,238):
       f.p.position=(36,36,z)
       result=f.p._parachute_speed_damage(1/60,velocity,active)
       rows.append((cid,capability,bits(f.server.world_manager.world.get_gravity()),bits(velocity),int(active),bits(z),result))
 data=('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode();buffer=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(data)
 fixture=OUT/'flight-impact.tsv.gz';fixture.write_bytes(buffer.getvalue())
 p=OUT/'flight-provenance.json';provenance=json.loads(p.read_text());provenance['impact_vectors']=len(rows);provenance['impact_fixture_sha256']=hashlib.sha256(fixture.read_bytes()).hexdigest();p.write_text(json.dumps(provenance,indent=2)+'\n')
 print('flight impact vectors',len(rows),'bytes',len(buffer.getvalue()))

if __name__=='__main__':
 if '--impact-only' not in sys.argv:generate()
 generate_impacts()
