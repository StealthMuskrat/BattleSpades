"""Run the unchanged original ReplicationService against controlled domain/connection inputs.

Native WorldUpdate serialization, ping, sanitization, cadence, recovery and routing
are real source code. Clocks, player snapshots, packet queues and releases are inputs.
"""
import gzip,hashlib,io,json,pathlib,random
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server.replication import ReplicationService
rows=[]
for group in range(16):
 rng=random.Random(252500+group);records=[];metrics=[0,0];flushes=[0]
 config=NS(broadcast_world_updates=True,worldupdate_broadcast_interval=2,worldupdate_self_row_interval=6,
  worldupdate_airborne_self_row_interval=10,worldupdate_retail_airborne_self_row_interval=20,
  worldupdate_loop_offset=group%3-1,worldupdate_include_self=group!=15,worldupdate_delivery='split' if group%2==0 else 'sequenced',worldupdate_reorder_guard=True,debug_selfrow=False)
 def metric(size,count):metrics[0]+=count;metrics[1]+=size*count
 def delivery(count,size):metrics[0]+=count;metrics[1]+=size
 def flush():flushes[0]+=1
 server=NS(config=config,players={},connections={},entities={},rocket_turrets={},loop_count=0,
  metrics=NS(record_world_packet=metric,record_world_delivery=delivery),host=NS(flush=flush))
 service=ReplicationService(server);server.build_world_update_data=service.build_world_update_data
 class Player:
  def __init__(self,id):
   self.id=id;self.name='Pilot Ω:'+str(id);self.wu_ack_loop=0;self.loadout=[7,15];self.input_history={}
  def world_update_snapshot(self):
   return ((100.125+self.id,200.25,50.5),(1.,0.,0.),(.125,-.25,0.),0,self.wu_ack_loop,100,17,self.action,self.state,self.tool,255,12.5,1.5,0.)
  def record_owner_anchor(self,stamp,position,velocity,queued_server_tick):records.append(f'{self.id},{stamp},{queued_server_tick}')
  def input_reorder_spread_frames(self,loop):return self.spread
 class Link:
  def __init__(self,p):self.player=p;self.in_game=True;self.sent=[];self.held=set();self.limit=1329;self.reliable_send_index=0;self.pending=False;self.peer=NS(roundTripTime=0)
  def send(self,data,reliable=True):
   self.sent.append(('R' if reliable else 'U',bytes(data).hex()))
   if reliable:self.reliable_send_index+=1
  def send_snapshot(self,data,unsequenced=True):self.sent.append(('N' if unsequenced else 'U',bytes(data).hex()))
  def snapshot_payload_limit(self):return self.limit
  def held_snapshot_rows(self):return self.held
  def reliable_unacked_through(self,index):return self.pending and index>0
 def emit(op,arg):
  result='-'
  if op=='state':
   a=arg.split(',');id=int(a[0]);p=server.players.get(id)
   if p is None:p=Player(id);server.players[id]=p;link=Link(p);server.connections[id]=link;p.connection=link
   else:link=p.connection
   (p.alive,p.spawned,p.is_bot,p.last_applied_input_loop,p.last_applied_input_synthesized,link.flight_profile_capable,
    p.airborne,p.last_retail_jump_loop,newest,link.peer.roundTripTime,p.jetpack_active,p._jetpack_physics_active,
    p._jetpack_activation_defer_remaining,p._jetpack_exhaustion_tail_remaining,p.parachute_active,p._parachute_physics_active,
    p._parachute_deploy_pending,p.spread,p.tool,p.action,p.state)=[None if v=='-' else float(v) if i==9 else int(v) for i,v in enumerate(a[1:])]
   # The source tests exact False here, unlike truthiness in the interval gate.
   if link.flight_profile_capable is not None:link.flight_profile_capable=bool(link.flight_profile_capable)
   p.input_history={} if newest is None else {newest:None}
  elif op=='link':
   id,limit,pending,index,held=arg.split(',');link=server.connections[int(id)];link.limit=int(limit);link.pending=bool(int(pending));link.reliable_send_index=int(index);link.held=set() if held=='-' else set(map(int,held.split(':')))
  elif op=='forget':service.forget_player(int(arg))
  elif op=='ingame':
   id,value=map(int,arg.split(','));server.connections[id].in_game=bool(value)
  elif op=='broadcast':
   server.loop_count=int(arg);records.clear();metrics[:]=[0,0];flushes[0]=0
   for link in server.connections.values():link.sent.clear()
   service.broadcast_world_updates()
   packets=';'.join(f'{id},{mode},{data}' for id,link in server.connections.items() for mode,data in link.sent) or '-'
   observers=';'.join(f'{id},{getattr(c,"_wu_observer_loop","-")},{int(getattr(c,"_wu_observer_sequenced",False))},{getattr(c,"_wu_ordered_barrier","-")}' for id,c in server.connections.items())
   result='|'.join([packets,';'.join(records) or '-',str(flushes[0]),','.join(map(str,metrics)),observers])
  else:raise AssertionError(op)
  rows.append((group,op,arg,result))
 for tick in range(150):
  for id in range(6):
   # Include dead rows, bots, bad tools, guessed labels, recovery windows,
   # urgent changes on odd endpoints, clock repeats, reordering and barriers.
   applied=1000+tick if tick%19 else None
   values=[id,int((tick+id)%23!=0),1,int(id==5),applied,int(tick%11==0),[False,True,None][id%3],int(20<tick%60<45),1000+(tick//60)*60+20 if tick%60>20 else None,1002+tick,[0,12.5,999.5,1500][(tick//30+id)%4],
    int(id==0 and tick%27<5),int(id==0 and tick%27<7),int(id==1 and tick%31==0),int(id==1 and tick%31==1),int(id==2 and tick%40<4),int(id==2 and tick%40<6),int(id==2 and tick%40==39),[0,3,7,0][(tick//22+id)%4],[7,15,65,-1][(tick//17+id)%4],255,(tick+id)%16]
   emit('state',','.join('-' if v is None else str(int(v)) if isinstance(v,bool) else str(v) for v in values))
   if tick%13==0:emit('link',f'{id},{[1329,180,67][id%3]},{int(tick%26==0)},{tick+3},{(id+1)%6 if tick%26==0 else "-"}')
  if tick in (40,43):
   for id in range(6):emit('ingame',f'{id},{int(tick==43)}')
  if tick in (57,114):emit('forget','0')
  emit('broadcast',str(tick if group<8 else tick+(tick//3)))
  if tick%9==0:emit('broadcast',str(tick if group<8 else tick+(tick//3)))
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
path=OUT/'replication.tsv.gz';path.write_bytes(buffer.getvalue())
sources=['server/replication.py','server/class_selection.py','server/lag_compensation.py','shared/packet.pyx']
(OUT/'replication-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','source_files_sha256':{s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in sources},'operations':len(rows),'groups':16,'fixture_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'scope':'Unmodified original ReplicationService and native packet serialization; controlled player/domain rows, clocks, queues, holds and prefix-release inputs. No real client proof.'},indent=2)+'\n')
print('replication operations',len(rows),'fixture bytes',path.stat().st_size)
