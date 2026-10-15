"""Execute reference join codecs/runtime decoders, health rows and committed voxel mutations."""
import json,pathlib,struct,sys
from types import SimpleNamespace
ROOT=pathlib.Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
from shared import packet
from protocol.runtime_packets import decode_client_data_payload,decode_set_class_loadout_payload
from server.flight_profile import profile_for_capability,ticket_flight_capability
from server.prefab_actions import encode_block_manager_state
from server.world_manager import WorldManager
from aoslib import vxl
OUT=ROOT/'java/src/test/resources/golden'
rows=[]
def fixture(name,p):rows.append(name+'\t'+bytes(p.generate()).hex())
p=packet.CreatePlayer();p.player_id=127;p.demo_player=0;p.class_id=5;p.team=2;p.dead=0;p.local_language=1
p.x=255.5;p.y=256.25;p.z=237.5;p.ori_x=-1;p.ori_y=0.5;p.ori_z=0;p.name='café 🂡';p.loadout=[0,128,255];p.prefabs=['one','café'];fixture('CreatePlayer',p)
p=packet.ExistingPlayer();p.player_id=3;p.demo_player=0;p.team=1;p.class_id=4;p.tool=7;p.pickup=255;p.dead=1;p.score=-123;p.forced_team=2;p.local_language=1;p.color=0x123456;p.name='Source';p.loadout=[1,255];p.prefabs=['one'];fixture('ExistingPlayer',p)
p=packet.SetClassLoadout();p.player_id=255;p.class_id=5;p.instant=1;p.loadout=[0,128,255];p.prefabs=['one','café'];p.ugc_tools=[255,0];fixture('SetClassLoadout',p)
rows.append('BlockManagerState\t'+encode_block_manager_state([(1,2,3,9),(511,511,238,1.00001),(0,0,0,100)],[(4,5,6,2.125,(18,52,86))]).hex())
for cap in range(1,5):rows.append(f'Flight/{cap}\t'+profile_for_capability(cap).encode().hex())
(OUT/'session.tsv').write_text('\n'.join(rows)+'\n')
rows=[]
for orientation in [(0,8192,40960),(16384,24576,57344),(65535,32767,32768)]:
 for tail in [b'',struct.pack('<H',0x803f),struct.pack('<f',-1.25),struct.pack('<f',1.5)+b'extra']:
  body=struct.pack('<BiBBHHHBBB',4,12345,0x83,7,*orientation,5,0xb1,0xc5)+tail
  result=decode_client_data_payload(body[1:]);rows.append(body.hex()+'\t'+json.dumps([result.player_id,result.palette_enabled,result.o_x,result.o_y,result.o_z,result.weapon_deployment_yaw]))
(OUT/'live-input.tsv').write_text('\n'.join(rows)+'\n')
raw=(OUT/'world/cave.vxl').read_bytes();native=vxl.VXL(None,raw,len(raw));assert native.ready
wm=WorldManager(SimpleNamespace(game_mode='tdm',ugc_runtime=False));wm.map=native
ops=[(255,255,198,True,0x123456,9),(255,255,198,True,0x654321,None),(255,255,201,False,0,None),(255,255,202,False,0,None),
     (256,255,201,True,0xabcdef,3),(255,255,201,True,0,5),(255,255,198,False,0,None),(255,255,201,False,0,None)]
rows=[];points=[];columns=[]
for index,(x,y,z,solid,rgb,health) in enumerate(ops):
 assert wm.set_block(x,y,z,solid,rgb,health)
 rows.append(f'{x}\t{y}\t{z}\t{int(solid)}\t{rgb}\t'+('none' if health is None else str(health)))
 for xx in [254,255,256]:
  for yy in [254,255,256]:
   columns.append(f'{index}\t{xx}\t{yy}\t'+native.serialize_columns([(xx,yy)])[8:].hex())
   for zz in [0,197,198,199,200,201,202,203,219,238,239]:
    points.append(f'{index}\t{xx}\t{yy}\t{zz}\t{int(native.get_solid(xx,yy,zz))}\t{int(native.get_color(xx,yy,zz))}\t{int(native.has_explicit_color(xx,yy,zz))}\t{wm.initial_block_health(xx,yy,zz)}')
(OUT/'mutations.ops.tsv').write_text('\n'.join(rows)+'\n');(OUT/'mutations.points.tsv').write_text('\n'.join(points)+'\n');(OUT/'mutations.columns.tsv').write_text('\n'.join(columns)+'\n')
print('8 packet/profile,12 live-input,8 mutation operations,',len(points),'points and',len(columns),'column oracle vectors')
