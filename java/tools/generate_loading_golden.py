"""Execute original packet and immutable map snapshot oracles. Development only."""
import hashlib,pathlib,struct,sys,zlib
ROOT=pathlib.Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
from shared import packet
from server.world_manager import MapSyncSnapshot
from aoslib import vxl
OUT=ROOT/'java/src/test/resources/golden'
rows=[]
def fixture(name,p):rows.append(name+'\t'+bytes(p.generate()).hex())
p=packet.InitialInfo()
values=dict(server_steam_id=2**64-1,server_ip=-1062731519,server_port=32887,query_port=65535,
 mode_name='CTF',mode_description='café 🂡',mode_infographic_text1='one',mode_infographic_text2='',mode_infographic_text3='three',mode_key=5,
 map_name='island',filename='island.vxl',checksum=-1,map_is_ugc=1,classic=0,enable_minimap=1,same_team_collision=0,max_draw_distance=255,
 enable_colour_picker=1,enable_colour_palette=1,enable_deathcam=0,enable_sniper_beam=1,enable_spectator=1,exposed_teams_always_on_minimap=0,enable_numeric_hp=1,
 texture_skin='default',beach_z_modifiable=1,enable_minimap_height_icons=1,enable_fall_on_water_damage=0,block_wallet_multiplier=1.25,block_health_multiplier=-1.0,
 disabled_tools=[0,128,255],disabled_classes=[1,5],movement_speed_multipliers=[1.0,1.25,-0.5,0.0078124],ugc_prefab_sets=[0,255],enable_player_score=1,server_name='Source oracle',
 ground_colors=[(18,52,86,255),(0,255,128,64)],loadout_overrides={(255,128):[0,127,255],(1,2):[]},allow_shooting_holding_intel=1,friendly_fire=1,
 custom_game_rules=[('key','value'),('café','🂡')],enable_corpse_explosion=1,ugc_mode=7)
for key,value in values.items():setattr(p,key,value)
fixture('InitialInfo',p)
p=packet.StateData();fixture('StateDataEmpty',p)
p.player_id=255;p.fog_color=(18,52,86);p.gravity=-9.81;p.light_color=(171,205,239);p.light_direction=(1.25,-2.5,3.75)
p.back_light_color=(1,2,3);p.back_light_direction=(-1,0.125,511);p.ambient_light_color=(4,5,6);p.ambient_light_intensity=0.75;p.time_scale=1.25
p.score_limit=255;p.mode_type=7;p.team_headcount_type=2;p.team1_name='Blue';p.team2_name='café';p.team1_color=(0,64,255);p.team2_color=(255,32,0)
p.team1_score=-123;p.team2_score=2147483647
for key in ['team1_locked','team1_show_score','team1_infinite_blocks','team1_locked_score','team2_can_see_team1','team2_show_max_score','team2_locked_class','lock_team_swap','lock_spectator_swap']:setattr(p,key,True)
p.team1_classes=[0,128,255];p.team2_classes=[];p.prefabs=[f'prefab/{n}' for n in range(373)]
e=packet.Entity();e.entity_id=-25536;e.type=38;e.state=2;e.player_id=3
for attr,value in dict(pos_x=10.25,pos_y=20.5,pos_z=200.75,vel_x=-1,vel_y=0.5,vel_z=0,yaw=-2,color_r=18,color_g=52,color_b=86,radius=1.5,face=4,fuse=3.25,ugc_mode=1).items():setattr(e,attr,value)
e.int_properties=[-1,2147483647];e.float_properties=[-1,0.5,2];p.entities=[e];p.screenshot_cameras_points=[(1.25,2.5,230.75),(-1,0.0078124,511)]
p.screenshot_cameras_rotations=[(-0.25,0.5,0)];p.has_map_ended=1
fixture('StateDataCatalog',p)
p=packet.MapSyncStart();p.size=123456;fixture('MapSyncStart',p)
for cls in ['MapDataChunk','MapSyncChunk']:
 for size in [0,1024,32767]:
  p=getattr(packet,cls)();p.percent_complete=73;p.data=bytes((n*17)&255 for n in range(size));fixture(f'{cls}/{size}',p)
p=packet.SteamSessionTicket();p.ticket=bytes(range(256));p.ticket_size=len(p.ticket);fixture('SteamSessionTicket',p)
(OUT/'loading.tsv').write_text('\n'.join(rows)+'\n')
# Digest uncompressed records: zlib implementation versions need not emit identical streams.
rows=[]
overlay_column=bytes([0,238,239,0])+struct.pack('<II',0xff123456,0xffabcdef)
coords=[(511,0),(0,511),(255,255)]
overlay=b''.join(struct.pack('<II',x,y)+overlay_column for x,y in sorted(coords))
for name in ['classic_water','full_surface','small_grid']:
 raw=(OUT/f'world/{name}.vxl').read_bytes() if name!='full_surface' else (bytes([0,63,63,0])+struct.pack('<I',0xff123456))*512*512
 native=vxl.VXL(None,raw,len(raw),source_format="auto");assert native.ready
 for full in [False,True]:
  for changed in [False,True]:
   snap=MapSyncSnapshot(raw,overlay if changed else b'',(176 if native.source_format=="classic64" else 0),name,17,full,frozenset(coords if changed else ()))
   chunks=snap.build_chunks()
   if chunks is None:
    # Only small full maps use the canonical-column fallback.
    assert full and name=='small_grid'
    # Apply canonical replacement data through source-native columns, in source wire order.
    record=bytearray()
    for y in range(512):
     for x in range(512):
      record+=struct.pack('<II',x,y)+(overlay_column if changed and (x,y) in coords else native.serialize_columns([(x,y)])[8:])
    chunks=[zlib.compress(bytes(record),6)]
   record=zlib.decompress(b''.join(chunks)) if chunks else b''
   rows.append(f'{name}\t{int(full)}\t{int(changed)}\t{len(record)}\t{hashlib.sha256(record).hexdigest()}')
(OUT/'map-sync.tsv').write_text('\n'.join(rows)+'\n')
print('11 loading packet fixtures; 12 source snapshot record hashes')
