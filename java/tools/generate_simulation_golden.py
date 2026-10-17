"""Run original SimulationRuntime/Player/World with real infantry movement, never a replacement mover.

Fixtures explicitly configure ordinary no-flight bodies, maps, clocks and policy flags.
Profiles observe original reload/lag/after-tick/report call boundaries without replacing methods.
Flight, concrete modes, reload results, awards and replication output are outside this scope.
"""
import asyncio,gzip,hashlib,io,json,logging,pathlib,random,struct,sys,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from aoslib import vxl,world
from server.player import Player
from server.game_rules import GameRules
from server.class_selection import ClassSelection
from server.handlers.movement import handle_client_data
from server.simulation_runtime import SimulationRuntime
from server import anticheat,lag_compensation,profile_stats
OUT=ROOT/'java/src/test/resources/golden';assert sys.version_info[:2]==(3,12)
logging.disable(logging.CRITICAL);clock=[100.0];original=(time.monotonic,time.time);time.monotonic=lambda:clock[0];time.time=lambda:clock[0]+900
def bits(x):return struct.pack('>d',float(x)).hex()
def num(x):return struct.unpack('>d',bytes.fromhex(x))[0]
def scalar(v):return '-' if v is None else str(int(v))
def mask(flags):return None if flags is None else sum(1<<i for i,v in enumerate(flags) if v)
def vector(v):return '-' if v is None else ','.join(bits(x) for x in v)
def digest(s):return hashlib.sha256(s.encode()).hexdigest()
def timeline(p):
 values=[p._input_newest_label,p.last_applied_input_loop,p.last_input_arrival_fresh,p.last_applied_input_synthesized,p._owner_timeline_sequence,p._input_receive_sequence,p._current_input_receive_sequence,p._current_input_owner_sequence,mask(p._pending_packet_flags),p._pending_packet_loop,p._pending_packet_received_server_tick,p._pending_packet_received_owner_sequence,p._pending_packet_wire_unknown_byte,p._applied_input_source_loop,p._applied_input_source_server_tick,p._applied_input_source_owner_sequence,p._applied_input_source_wire_unknown_byte,mask(p._applied_input_flags)]
 values=[scalar(x) for x in values]+[vector(p._applied_orientation),scalar(mask(p._applied_action_flags)),str(mask(p._press_latch_flags)),str(mask(p._press_latch_actions)),scalar(p._orientation_after_synth)]
 values+=[';'.join(','.join(map(str,[k,mask(f.movement_flags),vector(f.orientation),scalar(mask(f.action_flags)),f.received_server_tick,f.received_owner_sequence,f.received_input_sequence,f.wire_unknown_byte,f.topology_version])) for k,f in sorted(p.input_history.items())) or '-', ';'.join(f'{k},{mask(v[0])},{scalar(mask(v[1]))}' for k,v in sorted(p._synthesized_labels.items())) or '-']
 values+=list(map(str,[p.input_frames_dropped,p.input_frames_stale,p.input_frames_overflow,p.input_frames_applied,p.input_frames_synthesized,p.input_frames_reordered,p.input_frames_salvaged,p.input_presses_latched,p.input_frames_rejected_ahead]))
 return digest('~'.join(values))
class Fixture:
 def __init__(self,cid):
  clock[0]=100;self.housekeeping=0;self.events=[];self.wire=[];self.voxels=vxl.VXL(-1,b'',0,2)
  for x in range(20,71):
   for y in range(20,71):self.voxels.set_point(x,y,100,True,0x7f123456)
  w=world.World(self.voxels);rules=GameRules.server_defaults();rules.values['RULE_SPAWN_PROTECTION_TIME']=0
  self.server=NS(loop_count=1000,tick_rate=60,tick_interval=1/60,config=NS(game_rules=rules,default_mode='tdm',game_mode='tdm',movement_input_latch_frames=1,input_gap_fill_limit=8,fall_damage=True,same_team_collision=False,respawn_time=1.5,conduct=NS(grief_kick_enabled=False),anticheat=NS(client_data_rate=0,enforce_input_starvation=False,starvation_timeout_seconds=8,starvation_airborne_ticks=24,enforce_input_backlog=False,backlog_max_frames=6)),players={},world_manager=NS(world=w,topology_version=2601),world_mutations=NS(pending_count=0),mode=NS(),teams={2:NS(color=(18,52,86)),3:NS(color=(50,60,70))},queue_mode_event=lambda *args:None)
  self.server.broadcast=lambda data:self.capture('B',data)
  self.p=self.add(0,2,cid,36,36,97);self.runtime=SimulationRuntime(self.server)
 def add(self,id,team,cid,x,y,z):
  conn=NS(server=self.server,in_game=id==0,send=lambda data,**kw:self.capture('O'+str(id),data),disconnect=lambda reason:self.events.append('disconnect:'+str(int(reason))))
  p=Player(id,'Life',team,6,conn);self.server.players[id]=p;p.apply_class_selection(ClassSelection(cid,(5,6,23)));p.spawn(x,y,z);p.god_mode=True;return p
 def capture(self,lane,data):
  if bytes(data)[0] in (5,46):self.wire.append(lane+':'+bytes(data).hex())
 def profile(self,f,event,arg):
  if event!='call':return
  if f.f_code is Player._advance_reload_and_announce.__code__ and f.f_locals['self'] is self.p:self.housekeeping+=1
  elif f.f_code is anticheat.report.__code__:self.events.append('report:'+f.f_locals['kind']+':'+str(int(f.f_locals['enforced'])))
  elif f.f_code is lag_compensation.record_player.__code__:self.events.append('lag:'+str(f.f_locals['player'].id))
  elif f.f_code is profile_stats.tick.__code__:self.events.append('after:'+str(f.f_locals['player'].id))
 def snapshot(self):
  p=self.p;b=p._world_object
  move=mask(tuple(getattr(p.input,k) for k in ('up','down','left','right','jump','crouch','sneak','sprint')));actions=mask(tuple(getattr(p.input,k) for k in ('primary_fire','secondary_fire','zoom','can_pickup','can_display_weapon','is_on_fire','is_weapon_deployed','hover','palette_enabled')))
  values=[vector(b.position),vector(b.velocity),scalar(b.airborne),scalar(b.wade),scalar(b.crouch),scalar(b.jump),scalar(b.jump_this_frame),vector(p.velocity),vector(p.orientation),p.health,scalar(p.alive),scalar(p.spawned),move,actions,p.input_starved_ticks,p.input_frames_starvation_steps,p.input_frames_catchup,p.input_frames_backlog_dropped,p._starved_streak,p._backlog_over_ticks,scalar(p._backlog_catchup),scalar(p._starvation_timeout_flagged),'-' if p._last_client_data_at is None else bits(p._last_client_data_at),'-' if p._last_simulated_at is None else bits(p._last_simulated_at),bits(p._starvation_baseline),bits(p.movement_time),bits(p._fall_air_time),bits(p._airborne_since),'-' if p._rocket_jump_blast_at is None else bits(p._rocket_jump_blast_at),p.last_fall_result,scalar(p.last_landed),scalar(p.last_trigger_jump),scalar(p.last_retail_jump_loop),len(p._pending_velocity_impulses),self.housekeeping]
  eyes=';'.join(str(k)+':'+vector(v[0])+':'+vector(v[1]) for k,v in p._eye_history.items())
  values+=[digest(eyes),','.join(map(str,p.input_queue_delays)) or '-',timeline(p)]
  return '~'.join(map(str,values))
 def op(self,op,arg):
  a=arg.split(',');p=self.p;s=self.server;self.events=[];self.wire=[]
  sys.setprofile(self.profile)
  try:
   if op=='receive':
    label,movement,actions,unknown=map(int,a[:4]);values=dict(zip(('up','down','left','right','jump','crouch','sneak','sprint'),[bool(movement&(1<<i)) for i in range(8)]));values.update(zip(('primary','secondary','zoom','can_pickup','can_display_weapon','is_on_fire','is_weapon_deployed','hover','palette_enabled'),[bool(actions&(1<<i)) for i in range(9)]))
    packet=NS(loop_count=label,tool_id=6,ooo=unknown,o_x=num(a[4]),o_y=num(a[5]),o_z=num(a[6]),**values);asyncio.run(handle_client_data(s,p,packet))
   elif op=='tick':clock[0]+=num(a[0]);s.loop_count+=1;asyncio.run(self.runtime._simulate_players());assert not self.runtime._failure_log,self.runtime._failure_log
   elif op=='settings':
    gravity,same,fall,gap,enforce,timeout,air,backlog,cap=a;s.world_manager.world.set_gravity(num(gravity));s.config.same_team_collision=bool(int(same));s.config.fall_damage=bool(int(fall));s.config.input_gap_fill_limit=int(gap);c=s.config.anticheat;c.enforce_input_starvation=bool(int(enforce));c.starvation_timeout_seconds=num(timeout);c.starvation_airborne_ticks=int(air);c.enforce_input_backlog=bool(int(backlog));c.backlog_max_frames=int(cap)
   elif op=='spawn':p.spawn(*(num(x) for x in a))
   elif op=='god':p.god_mode=bool(int(arg))
   elif op=='live':p.connection.in_game=bool(int(arg))
   elif op=='retail':p.connection.flight_profile_capable=None if arg=='-' else bool(int(arg))
   elif op=='burden':p.pickup_burdensome=bool(int(arg))
   elif op=='impulse':p.queue_velocity_impulse(int(a[0]),tuple(num(x) for x in a[1:]))
   elif op=='ownblast':p.note_own_blast_push(int(arg))
   elif op=='peer':self.add(int(a[0]),int(a[1]),0,*(num(x) for x in a[2:]))
   elif op=='pending':s.world_mutations.pending_count=int(arg)
   elif op=='cell':
    x,y,z,solid=map(int,a);self.voxels.set_point(x,y,z,bool(solid),0x7f123456);s.world_manager.topology_version+=1
   else:raise AssertionError(op)
  finally:sys.setprofile(None)
  return self.snapshot(),';'.join(self.events) or '-', ';'.join(self.wire) or '-'
def generate():
 rows=[]
 try:
  for group in range(33):
   cid=group%18;f=Fixture(cid);label=100;randomizer=random.Random(6220+group)
   def emit(op,arg):rows.append((group,cid,op,arg,*f.op(op,arg)))
   def receive(n,move=0,actions=24,aim=(1,0,0)):emit('receive',','.join(map(str,[n,move,actions,randomizer.randrange(256),*(bits(v) for v in aim)])))
   def tick(seconds=1/60):emit('tick',bits(seconds))
   if group<18:
    if group%3==0:emit('peer',','.join(map(str,[1,3,*[bits(v) for v in (37.25,36,97.75)]])))
    for i in range(180):
     label+=1;move=[1,129,33,16,65,0][(i//30)%6];a=[(1,0,0),(0,1,0),(0,0,1),(.125,.25,-.5),(1e-7,0,0),(-1,0,0)][(i//30)%6]
     if i%19!=0:receive(label,move,24,a)
     if i%29==0:receive(label-1,17,128,(0,1,0))
     if i%71==0:emit('impulse',','.join([str(label+2),bits(.00003),bits(.02),bits(-.03)]))
     tick()
   elif group<24:
    enforce=group%2;emit('settings',','.join([bits(1),'0','1','8',str(enforce),bits(.4 if group<22 else 8),'4',str(enforce),'6']))
    receive(100);tick();emit('spawn',','.join(bits(x) for x in (36,36,30)));receive(101,129,128);tick()
    for _ in range(45):tick()
    tick(2);receive(102);tick();emit('live','0');receive(103);tick();emit('live','1');tick();emit('god','0')
    for i in range(104,240):receive(i);tick()
    emit('spawn',','.join(bits(x) for x in (36,36,20)));emit('ownblast','4')
    for i in range(240,560):receive(i);tick()
   elif group<29:
    enforce=1 if group==28 else group%2;emit('settings',','.join([bits(1),'0','1','8','0',bits(8),'24',str(enforce),'6']));emit('retail','0' if group==24 else '-')
    receive(100);tick()
    for n in range(101,191):receive(n)
    if group==27:emit('pending','1')
    if group==28:emit('cell','36,36,100,0')
    for n in range(191,431):receive(n,0,128 if group==26 else 24);tick()
   elif group==29:
    emit('peer',','.join(map(str,[1,2,*[bits(v) for v in (37.25,36,97.75)]])))
    for n in range(100,280):
     if n==160:emit('settings',','.join([bits(.4),'1','1','0','0',bits(8),'24','0','6']));emit('burden','1')
     receive(n,1 if n<220 else 161);tick()
   elif group==30:
    emit('spawn',','.join(bits(x) for x in (36,36,220)));emit('god','0')
    for n in range(100,260):receive(n);tick()
   elif group==31:
    receive(100);tick()
    for i in range(65):emit('impulse',','.join([str(500+i),bits(.003),bits(.00003),bits(0)]))
    receive(600);tick();emit('spawn',','.join(bits(x) for x in (36,36,97)));receive(700);tick()
   elif group==32:
    receive(100);tick();emit('spawn',','.join(bits(x) for x in (36,36,20)));emit('god','0')
    for n in range(101,461):receive(n);tick()
  buffer=io.BytesIO();raw=('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode()
  with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
  fixture=OUT/'infantry-simulation.tsv.gz';fixture.write_bytes(buffer.getvalue())
  sources=['server/player.py','server/simulation_runtime.py','server/handlers/movement.py','server/anticheat.py','aoslib/world.pyx','server/game_rules.py']
  (OUT/'infantry-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','operations':len(rows),'groups':33,'method_substitutions':False,'source_files':{n:hashlib.sha256((ROOT/n).read_bytes()).hexdigest() for n in sources},'fixture_sha256':hashlib.sha256(fixture.read_bytes()).hexdigest(),'scope':'ordinary no-flight original SimulationRuntime._simulate_players, Player.simulate_tick/update and real native mover; profiles observe reload/lag/after-tick/report boundaries; excludes flight, concrete modes, award/reload results and replication'},indent=2)+'\n');print('infantry operations',len(rows))
 finally:time.monotonic,time.time=original

if __name__ == "__main__":generate()
