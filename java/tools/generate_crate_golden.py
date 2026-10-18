"""Unchanged physical crate behavior/registry, original Player healing/audio, native world and entity/sound codecs."""
import gzip,hashlib,io,json,pathlib,struct,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from aoslib import vxl,world
from server.entities.registry import EntityRegistry,EntityContext
from server.entities.behaviors import PickupCrateBehavior
from server.world_manager import WorldManager
from server.game_rules import GameRules
from server.player import Player
from server.class_selection import ClassSelection
from shared.packet import CreateEntity,DestroyEntity,PlaySound,ChangeEntity
from shared.bytes import ByteReader
bits=lambda v:struct.pack('>d',float(v)).hex();vec=lambda v:','.join(map(bits,v));rows=[];clock=[1000.];old=(time.time,time.monotonic);time.time=lambda:clock[0];time.monotonic=lambda:clock[0]
def packet(e):
 p=CreateEntity();p.set_entity(e.to_wire_entity());return bytes(p.generate()).hex()
try:
 for trace in range(32):
  clock[0]=1000.;events=[];wire=[];config=NS(game_rules=GameRules.server_defaults(),game_mode='tdm',default_mode='tdm');wm=WorldManager(config);raw=(OUT/'world/empty.vxl').read_bytes();wm.map=vxl.VXL(None,raw,len(raw));wm.world=world.World(wm.map);reg=EntityRegistry();server=NS(config=config,world_manager=wm,players={},connections={},entity_registry=reg,mode=NS(),queue_mode_event=lambda *a:None)
  p=Player(0,'Life',2,6,NS(server=server,in_game=True,send=lambda data,*args,**kw:wire.append(bytes(data).hex())));server.players[0]=p;p.apply_class_selection(ClassSelection(0,(5,6,23)));p.spawn(36,36,97);p.health=25;p.blocks=0;p.ammo_clip=2;p.ammo_reserve=0
  for x,y,z in [(39,36,100),(37,34,97),(41,38,105)]:wm.set_block(x,y,z,True,0x406080)
  for x in range(32,48):wm.set_block(x,32,100,True,0x406080)
  if trace%4==2:wm.set_block(39,36,80,True,0x406080)
  def refill(player):
   events.append('refill')
   if trace%3==0:player.heal(25)
   elif trace%3==1:player.restock_ammo(3)
   else:player.restock_blocks()
  def cue(e):
   events.append('cue')
   if trace%5==0:raise RuntimeError('Controlled cosmetic cue fault')
  b=PickupCrateBehavior(refill,respawn_delay=[0.,.25,1.,15.][trace%4],sound_id=14 if trace%2 else None,airdrop=trace%3!=0,drop_start_z=[1.,90.,1.,250.][trace%4],drop_cue=cue)
  e=reg.place(4,39.25,36.75,98.75,behavior=b)
  def create(e):events.append('create');wire.append(packet(e))
  def destroy(id):
   events.append('destroy');d=DestroyEntity();d.entity_id=id;wire.append(bytes(d.generate()).hex())
  def move(e):
   events.append('move:'+vec((e.x,e.y,e.z)));p=ChangeEntity();p.entity_id=e.entity_id;p.action=1;p.pos_x,p.pos_y,p.pos_z=e.x,e.y,e.z;wire.append(bytes(p.generate()).hex())
  for step in range(256):
   dt=[1/60,.05,.125,.5][(step+trace)%4];clock[0]+=3. if step==127 else -.2 if step==160 else dt;events.clear();wire.clear();ctx=EntityContext(dt,clock[0],[],world=wm,server=server,create=create,destroy=destroy,move=move);op='T';arg='0' if step%11==0 else '-';result='-'
   if step in [0,128] and e.alive:op='U';result=str(int(b.on_touch(e,p,ctx)))
   elif step%32==12:
    op='R';x,y=int(e.x//1),int(e.y//1);arg=f'{x},{y}'
    for z in range(239):
     if wm.get_solid(x,y,z):wm.set_block(x,y,z,False)
   elif step%32==8:op='B';x,y=int(e.x//1),int(e.y//1);arg=f'{x},{y},110';wm.set_block(x,y,110,True,0x406080)
   else:
    if step==224 and trace%4==0:p.x,p.y,p.z=e.x,e.y,e.z;ctx.players=[p];arg+=':touch'
    result=str(reg.tick(ctx,0 if step%11==0 else None))
   state=','.join([str(int(e.alive)),bits(e.respawn_at),vec((e.x,e.y,e.z)),vec(e.vel),str(int(e.falling)),str(int(e.parachute_deployed)),str(int(e.parachute_removed)),bits(e.fall_accumulator),bits(e.fall_clock),'-' if e.terrain_support_z is None else str(e.terrain_support_z),bits(e.terrain_offset_z),bits(e.terrain_check_at),vec(e.home)])
   rows.append((trace,step,bits(dt),bits(ctx.now),op,arg,result,f'{p.health},{p.blocks},{p.ammo_clip},{p.ammo_reserve}',state,';'.join(events) or '-','|'.join(wire) or '-'))
finally:time.time,time.monotonic=old
roof_drop_observed=any('create' in r[9] and r[8].split(',')[4]==bits(82.) for r in rows)
assert roof_drop_observed,'Roof fixture must start below the actual overhang'
for case in range(512):
 p=PlaySound();p.sound_id=case%256;p.looping=bool(case&1);p.positioned=bool(case&2);p.loop_id=case*13%256;values=[(-700+(case*83)%1401)/7,(-600+(case*47)%1201)/11,(case*37%1401-700)/9,(case*73%1401-700)/13,(case*31%1401-700)/17,(case*19%1401-700)/23]
 p.volume,p.time,p.x,p.y,p.z,p.attenuation=values;raw=bytes(p.generate());decoded=PlaySound(ByteReader(raw[1:]));parsed=','.join(map(bits,[decoded.volume,decoded.time,decoded.x,decoded.y,decoded.z,decoded.attenuation]));rows.append(('sound',case,p.sound_id,int(p.looping),int(p.positioned),p.loop_id,','.join(map(bits,values)),raw.hex(),bytes(decoded.generate()).hex(),parsed))
for case in range(512):
 p=ChangeEntity();p.entity_id=case*173%65536;p.action=case%10;p.state=case%4;p.player_id=case%255;p.target_id=case%256-128;values=[(case*37%1401-700)/9,(case*73%1401-700)/13,(case*31%1401-700)/17,case/37];p.pos_x,p.pos_y,p.pos_z=values[:3];p.vel_x,p.vel_y,p.vel_z=values[:3];p.forward_x,p.forward_y,p.forward_z=values[:3];p.fuse=p.ammo=values[3];raw=bytes(p.generate());decoded=ChangeEntity(ByteReader(raw[1:]));rows.append(('change',case,p.entity_id,p.action,p.state,p.player_id,p.target_id,','.join(map(bits,values)),raw.hex(),bytes(decoded.generate()).hex()))
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
p=OUT/'crate.tsv.gz';p.write_bytes(buffer.getvalue());(OUT/'crate-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',crate_operations=8192,roof_drop_observed=roof_drop_observed,sound_codec_cases=512,change_entity_codec_cases=512,source_files_sha256={name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in ['server/entities/behaviors.py','server/entities/registry.py','server/world_manager.py','server/player.py','server/audio.py','shared/packet.pyx']},fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),method_substitutions=False,scope='Actual original crate, registry, native WorldManager, Player.heal/audio and compiled entity/sound codecs. Controlled clocks, finite authored placements/refill/cue and lifecycle/send IO. Includes capped/stalled/backward-time ticks, roof/drop/support/water rescue and live map mutation. No stock crate assembly/equipment/full service/server/client claim.'),indent=2)+'\n');print('Crate operations 8192; sound codec cases 512; change codec cases 512; bytes',p.stat().st_size)
