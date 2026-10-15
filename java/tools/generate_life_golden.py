"""Execute original Player, kill_feed and deployable_inventory policies.

Native World/VXL and all Player method bodies are unmodified. Only identity,
mode proposals, inert observers, maps, clocks and explicit operation inputs are
controlled. This covers owned life state, not the unported input/combat services.
"""
import asyncio,gzip,hashlib,io,json,logging,pathlib,random,struct,sys
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from aoslib import vxl,world
from shared import packet
from shared.bytes import ByteReader
from server.player import Player,ORIENTED_STOCK_AMMO
from server.game_rules import GameRules
from server.class_selection import ClassSelection
from server import kill_feed as K,deployable_inventory as D
from server.round_lifecycle import RoundLifecycle
from server import roster as R
assert sys.version_info[:2]==(3,12)
logging.disable(logging.CRITICAL)
OUT=ROOT/'java/src/test/resources/golden'
clock=[100.0,1000.0]
import time
original=(time.monotonic,time.time);time.monotonic=lambda:clock[0];time.time=lambda:clock[1]
def emit(name,rows):
 raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();out=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=out,mtime=0) as stream:stream.write(raw)
 (OUT/(name+'.tsv.gz')).write_bytes(out.getvalue());print(name,len(rows))
def bits(v):return struct.pack('>d',float(v)).hex()
def vec(v):return ','.join(bits(x) for x in v)
def mapping(m):return ','.join(str(k)+':'+('/'.join(map(str,v)) if isinstance(v,(list,tuple)) else str(v)) for k,v in sorted(m.items())) or '-'
def inv(p):
 return ';'.join([str(p.weapon),str(p.tool),f'{p.ammo_clip},{p.ammo_reserve}',mapping(p._weapon_ammo),mapping(p.oriented_stock),mapping(p.deployable_stock),mapping({k:bits(v) for k,v in p._deployable_next_use.items()}),str(p.rocket_turret_stock),str(p.disguise_stock),f'{p.blocks},{p._block_wallet_max()}',f'{int(p.reloading)},{bits(p.reload_end_time)}'])
def fixture(scale=1):
 m=vxl.VXL(-1,b'',0,2);w=world.World(m)
 server=NS(config=NS(game_rules=GameRules.server_defaults(),default_mode='tdm',game_mode='tdm',respawn_time=1.5),world_manager=NS(world=w),mode=NS(block_wallet_multiplier=scale),teams={2:NS(color=(18,52,86)),3:NS(color=(50,60,70))},players={},broadcast=lambda data:None,queue_mode_event=lambda *args:None)
 conn=NS(server=server,in_game=True,send=lambda data:None)
 return server,conn
try:
 rows=[]
 for cid in range(18):
  for scale in [0,0.5,1,1.25,1.5,3]:
   for equipment in [[],[66],[67],[68],[69],[72],[69,66,72]]:
    server,conn=fixture(scale);p=Player(0,'Life',2,6,conn);server.players={0:p}
    # Accepted selections are explicit inputs; normalization has its own source oracle.
    p.apply_class_selection(ClassSelection(cid,tuple([5,6,23]+equipment)))
    before=inv(p)
    p.set_orientation_vector(0,1,0);p.set_orientation_vector(0,0,-1)
    p.spawn(36.5,36.5,97.25)
    body=p._world_object
    rows.append((cid,scale,','.join(map(str,equipment)) or '-',inv(p),f'{p.replication_generation},{int(p.alive)},{int(p.spawned)},{p.health},{p.jetpack_id},{p.parachute_id},{p.block_color}',vec(p.position),vec((p.o_x,p.o_y,p.o_z)),vec((body.orientation.x,body.orientation.y,body.orientation.z)),before))
 emit('life-spawn',rows)
 rows=[];rng=random.Random(491)
 # Long stateful traces preserve outgoing/stowed magazines, crate grants and cadence across switches.
 for case in range(24):
  cid=case%18;scale=[0.5,1,1.25][case%3];server,conn=fixture(scale);p=Player(0,'Life',2,6,conn);server.players={0:p};p.apply_class_selection(ClassSelection(cid,(5,6,23)));p.spawn(36,36,97)
  def event(op,arg,result='-'):rows.append((case,cid,scale,op,arg,result,inv(p)))
  event('start','-')
  for tick in range(150):
   clock[0]=100+tick*0.1;kind=tick%10
   if kind==0:tool=rng.choice([6,7,8,9,10,11,15,23,5]);p.set_tool(tool,True);event('tool',tool)
   elif kind==1:p.ammo_clip=rng.randrange(5);p.ammo_reserve=rng.randrange(10);event('ammo',f'{p.ammo_clip},{p.ammo_reserve}')
   elif kind==2:p.reloading=True;p.reload_end_time=clock[0]+1.25;event('reload',bits(p.reload_end_time))
   elif kind in (3,4,5):
    tool=rng.choice(list(D._INTERVALS));ready=D.deployable_ready(p,tool,clock[0]);
    if ready:D.commit_deployable_use(p,tool,clock[0])
    event('deploy',f'{tool},{bits(clock[0])}',int(ready))
   elif kind==6:
    tool=rng.choice(list(ORIENTED_STOCK_AMMO));p.oriented_stock[tool]=rng.randrange(12);p._launcher_rounds[tool]=(0,clock[0]-rng.choice([0,1,2,5,12]));event('oriented',f'{tool},{p.oriented_stock[tool]},{bits(p._launcher_rounds[tool][1])}')
   elif kind in (7,8):
    p.is_bot=case%2==0;stock=3 if kind==7 else rng.choice([0,1,2,4]);p.restock_ammo(stock);event('restock',f'{stock},{bits(clock[0])},{int(p.is_bot)}')
   else:p.spawn(36,36,97);event('spawn','-')
 emit('life-inventory',rows)
 rows=[];server,conn=fixture();players={i:Player(i,'Life',2+i%2,6,conn) for i in range(8)};server.players=players
 for i in range(2000):
  a,b=rng.sample(range(8),2);now=100+i*rng.choice([0.01,1,6,6.0000001]);op=i%13
  if op<7:value=K.register_multikill(players[a],now);rows.append(('multi',a,b,bits(now),str(value)))
  elif op<11:flags=K.register_domination(players[a],players[b]);rows.append(('dom',a,b,bits(now),','.join(str(int(v)) for v in flags)))
  elif op==11:K.forget_player(server,players[a]);rows.append(('forget',a,b,bits(now),'-'))
  else:K.clear_player(server,players[a]);rows.append(('clear',a,b,bits(now),'-'))
  rows.append(('relation',a,b,bits(now),str(int(K.is_dominating(players[a],players[b])))))
 emit('life-killfeed',rows)
 # Native packet codec bytes include boolean canonicalization and noncanonical reader inputs.
 rows=[]
 for i in range(100):
  p=packet.SetHP();p.hp=i;p.damage_type=i%3;p.source_x=(i-50)*0.125;p.source_y=36.5;p.source_z=97.25;rows.append(('hp',i,bytes(p.generate()).hex()))
  p=packet.KillAction();p.player_id=i;p.killer_id=99-i;p.kill_type=i%26;p.respawn_time=i%8;p.kill_count=i;p.isDominationKill=i%2;p.isRevengeKill=(i//2)%2;rows.append(('death',i,bytes(p.generate()).hex()))
  p=packet.WeaponReload();p.player_id=i;p.tool_id=i%66;p.is_done=i%2;rows.append(('reload',i,bytes(p.generate()).hex()))
 decoded=[]
 for kind,i,body in rows:
  cls={'hp':packet.SetHP,'death':packet.KillAction,'reload':packet.WeaponReload}[kind];p=cls();p.read(ByteReader(bytes.fromhex(body)[1:]));decoded.append((kind,i,body,bytes(p.generate()).hex()))
 emit('life-packets',decoded)
 # Original death and asynchronous respawn scheduler, with explicit mode callbacks and inert effect ports.
 rows=[];server,conn=fixture();allowed={0:True,1:True,2:True};failing=set();delay=[1.5];wire=[]
 server.mode.can_player_respawn=lambda p:allowed[p.id]
 server.mode.respawn_time_for=lambda p:delay[0]
 def prepare_spawn(p):
  if p.id in failing:raise ValueError('controlled prepare failure')
 server.mode.prepare_player_spawn=prepare_spawn;server.mode.get_spawn_point=lambda p:(36,36,97)
 server._broadcast_create_player=lambda p,at:wire.extend([28,85,11])
 server.broadcast=lambda data:wire.append(bytes(data)[0]);conn.send=lambda data:wire.append(bytes(data)[0])
 server.players={i:Player(i,'Life',2+i%2 if i<2 else 0,6,conn) for i in range(3)}
 for p in server.players.values():
  p.apply_class_selection(ClassSelection(0,(5,6,23)))
  if p.team:p.spawn(36,36,97)
 lifecycle=RoundLifecycle(server);clock[:]=[100,1000]
 def states():
  return ';'.join(str(p.id)+':'+','.join(map(str,[p.replication_generation,int(p.alive),int(p.spawned),bits(p.death_time),p.class_id,p.health,p.kills,p.deaths,p.kill_streak,getattr(p,'last_killed_by_id',-1),p.rocket_turret_stock])) for p in server.players.values())
 def operation(op,args):
  wire.clear()
  if op=='die':
   a,b,kind=map(int,args.split(','));server.players[a].die(None if b<0 else server.players[b],kind)
  elif op=='stage':
   a,cid=map(int,args.split(','));server.players[a].stage_class_selection(ClassSelection(cid,(5,6,23,69)))
  elif op=='advance':
   elapsed=float(args);clock[0]+=elapsed;clock[1]+=elapsed;asyncio.run(lifecycle.process_respawns())
  elif op=='allow':a,v=map(int,args.split(','));allowed[a]=bool(v)
  elif op=='fail':
   a,v=map(int,args.split(','));failing.add(a) if v else failing.discard(a)
  elif op=='delay':delay[0]=float(args)
  else:raise AssertionError(op)
  rows.append((op,args,states(),','.join(map(str,wire)) or '-', ';'.join(str(p.id)+':'+(p.last_kill_action_data.hex() if p.last_kill_action_data else '-') for p in server.players.values())))
 for cycle in range(30):
  operation('die','1,0,0');operation('die','1,0,0');operation('stage',f'1,{cycle%18}');operation('advance','1.499999');operation('advance','0.000002')
 operation('die','0,1,0');operation('advance','1.5');operation('die','0,1,8');operation('advance','1.5');operation('die','0,1,10');operation('advance','1.5')
 operation('allow','0,0');operation('die','0,1,0');operation('advance','10');operation('allow','0,1');operation('advance','0')
 operation('die','0,-1,7');operation('die','1,-1,0');operation('fail','0,1');operation('advance','1.5');operation('advance','1.49999');operation('fail','0,0');operation('advance','0.00002')
 for value in ['0','0.000001','1.0000001','255.1','300']:
  operation('delay',value);operation('die','0,0,9');operation('advance',str(float(value)+0.01))
 emit('life-runtime',rows)
 rows=[];server=NS(players={});wire=[];conn=NS(server=server,player=NS(id=0),send=lambda data,**kwargs:wire.append(bytes(data).hex()))
 def roster_op(op,id):
  wire.clear()
  if op=='create':server.players[id]=NS(id=id,name='Life',team=2,class_id=0,alive=True,spawned=True,replication_generation=1,position=(1,2,3),o_x=1,o_y=0,o_z=0,local_language=0,loadout=[5,6,23],prefabs=[],last_kill_action_data=None)
  elif op=='respawn':
   p=server.players[id];p.replication_generation+=1;p.alive=p.spawned=True;p.last_kill_action_data=None
  elif op=='dead':
   p=server.players[id];p.alive=p.spawned=False;d=packet.KillAction();d.player_id=id;d.killer_id=0;d.kill_type=3;d.respawn_time=2;d.kill_count=0;d.isDominationKill=d.isRevengeKill=False;p.last_kill_action_data=bytes(d.generate())
  elif op=='remove':server.players.pop(id,None)
  elif op=='sync':R.catch_up_roster(server,conn)
  else:raise AssertionError(op)
  rows.append((op,id,','.join(wire) or '-'))
 for op in ['create','sync','dead','sync','sync','respawn','sync','remove','create','sync','remove','create','dead','sync','respawn','sync']:roster_op(op,1)
 for i in range(500):
  id=rng.choice([1,2,3]);op=rng.choice(['create','dead','respawn','remove','sync'])
  if op in ('dead','respawn') and id not in server.players:op='create'
  if op=='create' and id in server.players:roster_op('remove',id)
  roster_op(op,id)
 emit('life-roster',rows)
finally:time.monotonic,time.time=original
manifest={'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','policy_substitutions':False,'scope':'original Player.spawn/die/selection/tool/restock, RoundLifecycle scheduler, roster reconciliation, deployable inventory and kill_feed; compiled world/VXL/packets; no input timeline or combat replay','controlled_ports':'mode proposals/delays/gates/failures, clocks, inert mode events; server create broadcast callback records packet IDs only (not an oracle for Main creation policy); Java palette packets are independently tested extensions to source roster catch-up','source_files':{p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/player.py','server/round_lifecycle.py','server/roster.py','server/connection.py','server/kill_feed.py','server/deployable_inventory.py','server/class_selection.py','server/game_rules.py','shared/packet.pyx','aoslib/world.pyx']},'fixtures':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(OUT.glob('life-*.tsv.gz'))}}
(OUT/'life-provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
