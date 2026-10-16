"""Observe unchanged original ClientData handler, Player input phases and action evidence.

Uses compiled original World/VXL and Player.update; no movement/input/action method
is replaced. A profile hook observes the raw phase at the original update boundary.
Scope excludes downstream flight/movement results, tool policy, conduct and awards.
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
assert sys.version_info[:2]==(3,12)
OUT=ROOT/'java/src/test/resources/golden';logging.disable(logging.CRITICAL)
clock=[100.0];original=(time.monotonic,time.time);time.monotonic=lambda:clock[0];time.time=lambda:clock[0]+900
def bits(x):return struct.pack('>d',float(x)).hex()
def num(x):return struct.unpack('>d',bytes.fromhex(x))[0]
def scalar(x):return '-' if x is None else str(int(x))
def mask(flags):return None if flags is None else sum(1<<i for i,x in enumerate(flags) if x)
def flags(value,n):return tuple(bool(value&(1<<i)) for i in range(n))
def aim(v):return '-' if v is None else ','.join(bits(x) for x in v)
def frame(label,f):return ','.join(map(str,[label,mask(f.movement_flags),aim(f.orientation),scalar(mask(f.action_flags)),f.received_server_tick,f.received_owner_sequence,f.received_input_sequence,f.wire_unknown_byte,f.topology_version]))
def snapshot(p):
 values=[p._input_newest_label,p.last_applied_input_loop,p.last_input_arrival_fresh,p.last_applied_input_synthesized,p._owner_timeline_sequence,p._input_receive_sequence,p._current_input_receive_sequence,p._current_input_owner_sequence,mask(p._pending_packet_flags),p._pending_packet_loop,p._pending_packet_received_server_tick,p._pending_packet_received_owner_sequence,p._pending_packet_wire_unknown_byte,p._applied_input_source_loop,p._applied_input_source_server_tick,p._applied_input_source_owner_sequence,p._applied_input_source_wire_unknown_byte,mask(p._applied_input_flags)]
 parts=[scalar(x) for x in values]+[aim(p._applied_orientation),scalar(mask(p._applied_action_flags)),str(mask(p._press_latch_flags)),str(mask(p._press_latch_actions)),scalar(p._orientation_after_synth)]
 parts+=[';'.join(frame(k,v) for k,v in sorted(p.input_history.items())) or '-', ';'.join(f'{k},{mask(v[0])},{scalar(mask(v[1]))}' for k,v in sorted(p._synthesized_labels.items())) or '-']
 parts+=list(map(str,[p.input_frames_dropped,p.input_frames_stale,p.input_frames_overflow,p.input_frames_applied,p.input_frames_synthesized,p.input_frames_reordered,p.input_frames_salvaged,p.input_presses_latched,p.input_frames_rejected_ahead]))
 return '~'.join(parts)
class Fixture:
 def __init__(self,latch):
  clock[0]=100;self.steps=[]
  rules=GameRules.server_defaults();rules.values['RULE_SPAWN_PROTECTION_TIME']=0
  w=world.World(vxl.VXL(-1,b'',0,2));w.set_gravity(0)
  self.server=NS(loop_count=1000,tick_rate=60,config=NS(game_rules=rules,default_mode='tdm',game_mode='tdm',movement_input_latch_frames=latch,input_gap_fill_limit=8,conduct=NS(grief_kick_enabled=False),anticheat=NS(client_data_rate=0,enforce_input_starvation=False)),world_manager=NS(world=w,topology_version=0),players={},mode=NS(),teams={2:NS(color=(18,52,86))},queue_mode_event=lambda *args:None,broadcast=lambda *args,**kw:None)
  self.p=Player(0,'Input',2,6,NS(server=self.server,in_game=True,send=lambda *args,**kw:None));self.server.players[0]=self.p;self.p.apply_class_selection(ClassSelection(0,(5,6,23)));self.p.spawn(100.5,100.5,97.25);self.p.god_mode=True
 def observe(self,f,event,arg):
  if event!='call' or f.f_code is not Player.update.__code__:return
  caller=f.f_back
  if caller.f_code not in (Player._consume_input_frame.__code__,Player._synthesize_missing_frame.__code__):return
  a=caller.f_locals;p=self.p;synth=caller.f_code is Player._synthesize_missing_frame.__code__
  orientation=a['orientation'] if synth else a['applied_orientation']
  actions=None if synth else mask(a['action_flags'])
  self.steps.append(','.join(map(str,[p.last_applied_input_loop,mask(a['flags']),aim(orientation),scalar(actions),int(synth),scalar(p._applied_input_source_loop),scalar(p._applied_input_source_server_tick),scalar(p._applied_input_source_owner_sequence),scalar(p._applied_input_source_wire_unknown_byte),p._current_input_receive_sequence,scalar(p._current_input_owner_sequence)])))
 def op(self,op,arg):
  p=self.p;s=self.server;a=arg.split(',');result='-';self.steps=[];clock[0]+=1/60
  sys.setprofile(self.observe)
  try:
   if op=='receive':
    label,tick,movement,actions,unknown=map(int,a[:5]);s.loop_count=tick;s.world_manager.topology_version=int(a[8]);s.config.anticheat.enforce_input_starvation=bool(int(a[9]))
    data=dict(zip(('up','down','left','right','jump','crouch','sneak','sprint'),flags(movement,8)))
    data.update(zip(('primary','secondary','zoom','can_pickup','can_display_weapon','is_on_fire','is_weapon_deployed','hover','palette_enabled'),flags(actions,9)))
    packet=NS(loop_count=label,tool_id=6,ooo=unknown,o_x=num(a[5]),o_y=num(a[6]),o_z=num(a[7]),**data)
    asyncio.run(handle_client_data(s,p,packet))
   elif op=='consume':
    s.config.input_gap_fill_limit=int(a[0]);s.loop_count=int(a[1])
    # Deliberately test the one-frame selector; outer starvation/backlog policy is a separate port.
    if p.input_history:asyncio.run(p._consume_input_frame(s,1/60))
   elif op=='spawn':p.spawn(100.5,100.5,97.25)
   elif op=='dead':p.alive=p.spawned=False
   elif op=='owner':result=str(p._claim_owner_timeline_sequence(None if a[0]=='-' else int(a[0])))
   elif op=='spread':result=str(p.input_reorder_spread_frames(int(arg)))
   elif op=='zoom':result=str(int(p.zoom_for_action(None if arg=='-' else int(arg))))
   elif op=='arrival':result=scalar(p.label_arrival_tick(None if arg=='-' else int(arg)))
   else:raise AssertionError(op)
  finally:sys.setprofile(None)
  return snapshot(p),';'.join(self.steps) or '-',result
rows=[]
try:
 for group in range(24):
  latch=group%2;f=Fixture(latch);rng=random.Random(1800+group);label=100;tick=1000
  def emit(op,arg):rows.append((group,latch,op,arg,*f.op(op,arg)))
  def receive(n,m=1,a=0,v=(1,0,0),enforce=0):emit('receive',','.join(map(str,[n,tick,m,a,rng.randrange(256),*(bits(x) for x in v),group,enforce])))
  # Lost-label salvage, delayed presses, duplicate retained clocks, wide relabels and lifecycle reset.
  for n in [100,101]:receive(n);emit('consume',f'8,{tick}')
  receive(103);receive(104);emit('consume',f'8,{tick}');
  if group%3:emit('consume',f'8,{tick}')
  receive(102,17,128,(0,1,0));receive(102,17,128,(0,1,0));emit('consume',f'8,{tick}');emit('consume',f'8,{tick}')
  for _ in range(180):
   tick+=rng.randrange(1,4)
   if rng.random()<0.64:
    label+=rng.choice([0,1,1,1,2,3,12]);n=label-rng.choice([0,0,0,1,2,10,11])
    receive(n,rng.randrange(256),rng.randrange(512),rng.choice([(1,0,0),(0,1,0),(-1,0,0),(0,0,1),(0,0,0),(1e-7,0,0)]))
   else:emit('consume',f'{rng.choice([0,1,8])},{tick}')
   if rng.random()<0.1:emit('owner',rng.choice(['-',str(tick+100)]))
   if rng.random()<0.15:emit('zoom',str(label-rng.randrange(20)));emit('arrival',str(label-rng.randrange(20)))
  emit('dead','-');receive(label+1);emit('spawn','-');receive(label-1,32,4);emit('consume',f'8,{tick}')
  receive(2**31-1,1,4,enforce=1);receive(2**31-1,32,0,enforce=1);receive(-2**31,2,0)
  # Capacity: retain the newest 128 in sorted label order, but zoom in insertion order.
  emit('spawn','-')
  for n in range(500,800):receive(n,0,4 if n%5==0 else 0)
  for n in [500,543,544,799,800,820]:emit('zoom',str(n));emit('arrival',str(n))
  emit('spread',str(tick));emit('spread',str(tick+1201));emit('owner','-');emit('consume',f'8,{tick}')
 raw=('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode();buffer=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
 fixture=OUT/'input-timeline.tsv.gz';fixture.write_bytes(buffer.getvalue())
 sources=['server/player.py','server/handlers/movement.py','server/class_selection.py','server/anticheat.py','aoslib/world.pyx','aoslib/vxl.pyx']
 provenance={'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','generator':'java/tools/generate_input_golden.py','source_files':{n:hashlib.sha256((ROOT/n).read_bytes()).hexdigest() for n in sources},'operations':len(rows),'groups':24,'fixture_sha256':hashlib.sha256(fixture.read_bytes()).hexdigest(),'method_substitutions':False,'observation':'sys.setprofile at unchanged Player.update call boundary; original handler and native movement execute','scope':'receive chronology, one-frame phase selection, lost-label salvage, labelled action zoom/arrival and causal sequence; excludes outer backlog/starvation, flight results, impulses, eyes, tool/conduct policies and awards'}
 (OUT/'input-provenance.json').write_text(json.dumps(provenance,indent=2)+'\n');print('input operations',len(rows))
finally:time.monotonic,time.time=original
