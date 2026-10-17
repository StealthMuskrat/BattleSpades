"""Unchanged original Player gun/reload methods and action_clock, with controlled calls and wallets.

Selection here is a trusted fixture input; tool/hit authorization is outside this oracle.
"""
import gzip,hashlib,io,json,logging,pathlib,random,struct,sys,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from aoslib import vxl,world
from server.player import Player
from server.game_constants import WEAPON_PROFILES
from server.game_rules import GameRules
from server.class_selection import ClassSelection
from server import action_clock
logging.disable(logging.CRITICAL)
clock=[100.];original=time.monotonic;time.monotonic=lambda:clock[0]
def bits(v):return struct.pack('>d',float(v)).hex()
def number(v):return struct.unpack('>d',bytes.fromhex(v))[0]
def scalar(v):return '-' if v is None else str(int(v))
def state(p):
 floor=getattr(p,'_auto_reload_floor',None)
 values=[p.tool,p.weapon,int(p.tool_is_raw),scalar(p._tool_before_mg),p.ammo_clip,p.ammo_reserve,int(p.reloading),bits(p.reload_end_time),bits(p.last_shot_time),bits(p.next_shot_time),'-' if floor is None else ':'.join(map(str,floor)),int(getattr(p,'_reload_done_pending',False)),p.get_weapon_profile().tool_id]
 values=[str(v) for v in values];values+=[';'.join(str(k)+':'+str(c)+':'+str(r) for k,(c,r) in sorted(p._weapon_ammo.items()))]
 values+=[';'.join(','.join(map(str,[key,scalar(l.label),bits(l.interval),bits(l.due),bits(l.tokens),bits(l.token_interval),bits(l.refilled_at),l.admitted,l.refused,l.by_label])) for key,l in action_clock.lanes(p).items()) or '-']
 return '~'.join(values)
rows=[];voxels=vxl.VXL(-1,b'',0,2);native=world.World(voxels)
tools=list(WEAPON_PROFILES)+[0,2,5,15,32,33,35,36,37,38,54,62,64,65,66,254]
try:
 for group,tool in enumerate(tools):
  wire=[];clock[0]=100.;bot=False;rng=random.Random(2561+group)
  rules=GameRules.server_defaults()
  server=NS(loop_count=1000,config=NS(game_rules=rules,default_mode='tdm',game_mode='tdm',respawn_time=1.5),world_manager=NS(world=native),mode=NS(block_wallet_multiplier=1),teams={2:NS(color=(18,52,86))},players={},broadcast=lambda data:wire.append(bytes(data).hex()),queue_mode_event=lambda *args:None)
  conn=NS(server=server,in_game=True,send=lambda data:None,peer=None);p=Player(0,'Fire',2,6,conn);server.players[0]=p;p.apply_class_selection(ClassSelection(0,(5,6,23)));p.spawn(36,36,97)
  def emit(op,arg):
   global bot
   a=arg.split(',');wire.clear();result='-'
   if op=='tool':p.set_tool(int(a[0]),raw=None if a[1]=='-' else bool(int(a[1])))
   elif op=='ammo':p.ammo_clip,p.ammo_reserve=map(int,a)
   elif op=='newest':p._observe_input_arrival_order(int(arg),1000)
   elif op=='time':clock[0]=number(arg)
   elif op=='link':conn.peer=None if a[0]=='-' else NS(roundTripTime=number(a[0]),roundTripTimeVariance=number(a[1]))
   elif op=='bot':bot=bool(int(arg));p.is_bot=bot
   elif op in ('can','shot'):
    interval=None if a[0]=='-' else number(a[0]);label=None if a[1]=='-' else int(a[1]);result=scalar((p.can_fire if op=='can' else p.consume_shot)(clock[0],fire_interval=interval,loop=label))
   elif op=='reload':result=scalar(p.start_reload(clock[0]))
   elif op=='advance':result=scalar(p._advance_reload(clock[0]))
   elif op=='announce':p._advance_reload_and_announce()
   elif op=='finish':result=scalar(p.finish_reload())
   elif op=='spawn':p.spawn(36,36,97)
   elif op=='crate':p._restock_ammo_crate()
   elif op=='launcher':p._launcher_rounds[int(a[0])]=(int(a[1]),number(a[2]))
   elif op=='stock':p.oriented_stock[int(a[0])]=int(a[1])
   else:raise AssertionError(op)
   rows.append((group,op,arg,result,state(p),';'.join(wire) or '-'))
  emit('tool',f'{tool},1');emit('newest','100000');label=100
  for step in range(200):
   if step%17==0:emit('link',rng.choice(['-,-',bits(0)+','+bits(0),bits(80)+','+bits(30),bits(1000)+','+bits(250)]))
   if step%29==0:emit('bot',str(int(step%58==0)))
   if step%13==0:emit('ammo',f'{rng.randrange(4)},{rng.choice([0,1,2,30,100])}');emit('reload','-')
   if step%31==0:emit('tool',f'{rng.choice([2,6,15,tool])},{rng.choice([0,1,"-"])}')
   if step%31==1:emit('tool',f'{tool},1')
   if step==115:emit('spawn','-')
   clock[0]+=rng.choice([0,1/120,1/60,.1,.4,1.5]);emit('time',bits(clock[0]));label+=rng.choice([0,1,6,30])
   arg=','.join([rng.choice(['-',bits(.1),bits(.7)]),rng.choice(['-',str(label),str(label+100000)])]);emit('can',arg);emit('shot',arg)
   if step%7==0:emit('reload','-')
   if step%19==0:emit('finish','-')
   if step%5==0:emit('announce','-')
   if step%37==0:emit('crate','-')
  if tool in p._RELOADING_LAUNCHERS:
   emit('tool',f'{tool},1');emit('bot','0')
   for left in (0,1):
    emit('launcher',f'{tool},{left},{bits(clock[0])}');emit('reload','-');emit('time',bits(clock[0]+10));emit('reload','-')
 raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
 path=OUT/'player-fire.tsv.gz';path.write_bytes(buffer.getvalue())
 (OUT/'player-fire-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','operations':len(rows),'groups':len(tools),'fixture_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'source_files_sha256':{s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/player.py','server/action_clock.py','server/game_constants.py')},'scope':'Unchanged original Player can_fire/consume_shot, reload chains/grace/label floors, raw tool/weapon wallets, launcher reload relay and action_clock. Trusted selection, peer measurements, clocks and wallet states are controlled inputs; no tool or hit authorization proof.','method_substitutions':False},indent=2)+'\n')
 print('player firing operations',len(rows),'groups',len(tools),'bytes',path.stat().st_size)
finally:time.monotonic=original
