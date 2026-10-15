"""Execute original Cython packet encoders; persist independent golden bytes.
Run from repository root with reference-venv after setup.py build_ext --inplace.
"""
import hashlib, json, pathlib, random, struct, sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from shared import packet
from server.util import lzf_compress
specs=json.loads((ROOT/'java/docs/simple-packets.json').read_text())
rows=[]
for spec in specs:
    for variation in range(3):
        p=getattr(packet,spec['name'])()
        for index,(source,java,typ) in enumerate(spec['fields']):
            if typ=='String': value=['','Golden','café 🂡'][variation]
            elif typ=='float': value=[0.0,-1.25,123.5][variation]
            elif typ=='long': value=[0,90087911866072064,2**64-1][variation]
            elif 'color' in source: value=[0,0x123456,0xffffff][variation]
            else: value=[0,3,127][variation]
            
            if spec['name']=='PaintBlockPacket' and source=='color': value=((value>>16)&255,(value>>8)&255,value&255)
            setattr(p,source,value)
        body=bytes(p.generate())
        rows.append(f"{spec['name']}/{variation}\t{body.hex()}")
(ROOT/'java/src/test/resources/golden/packets.tsv').write_text('\n'.join(rows)+'\n')
rng=random.Random(168)
rows=[]
for n in [0,1,2,31,32,33,63,64,65,1024,4096,8192]:
    body=bytes(rng.randrange(256) for _ in range(n))
    rows.append(f'{n}\t{body.hex()}\t{lzf_compress(body).hex()}')
(ROOT/'java/src/test/resources/golden/lzf.tsv').write_text('\n'.join(rows)+'\n')
# source encoder input fixtures for handwritten ClientData; include saturation hacks.
rows=[]
for i,(x,y,z,yaw) in enumerate([(0,0,0,0),(1,-1,0.5,-1),(7,-7,0,511),(0.125,-0.125,0.25,-0.125)]):
    p=packet.ClientData()
    p.loop_count=12345;p.player_id=3;p.palette_enabled=True;p.tool_id=7
    p.o_x=x;p.o_y=y;p.o_z=z;p.ooo=5
    p.up=True;p.jump=True;p.crouch=True;p.sprint=True
    p.primary=True;p.zoom=True;p.is_weapon_deployed=True;p.hover=True
    p.weapon_deployment_yaw=yaw
    rows.append(f'{x}\t{y}\t{z}\t{yaw}\t{bytes(p.generate()).hex()}')
(ROOT/'java/src/test/resources/golden/client-input.tsv').write_text('\n'.join(rows)+'\n')
manifest={'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','generator':'java/tools/generate_golden.py','oracles':{'packet':'compiled shared.packet + server.util','world':'compiled aoslib.vxl / aoslib.kv6','loading':'compiled shared.packet + server.world_manager.MapSyncSnapshot','range':'vendored ENet compress.c','session':'compiled shared.packet, protocol.runtime_packets, server.flight_profile, prefab_actions.encode_block_manager_state','mutations':'compiled aoslib.vxl + server.world_manager.WorldManager','movement':'compiled aoslib.world.Player + aoslib.vxl.VXL; exact public float/double stores','queries':'compiled aoslib.world raycast/cube_line/face/is_centered','scene':'server.game_rules/class_data/class_selection, common initial_info/state_data builders and compiled packet/world codecs'},'generators':['generate_golden.py','generate_world_golden.py','generate_range_golden.py','generate_loading_golden.py','generate_session_golden.py','generate_movement_golden.py','generate_query_golden.py','generate_scene_golden.py','export_domain_catalogs.py'],'fixtures':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT/'java/src/test/resources/golden').glob('*.tsv'))}}
(ROOT/'java/src/test/resources/golden/provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(len(specs)*3,'simple packet vectors; 4 ClientData vectors; 12 LZF vectors')
# Complex packets are constructed explicitly; do not synthesize expected bytes in Java.
e=packet.Entity();e.entity_id=-25536;e.type=38;e.state=2;e.player_id=3
for attr,value in dict(pos_x=10.25,pos_y=20.5,pos_z=200.75,vel_x=-1,vel_y=0.5,vel_z=0,yaw=-2,color_r=18,color_g=52,color_b=86,radius=1.5,face=4,fuse=3.25,ugc_mode=1).items():setattr(e,attr,value)
e.int_properties=[-1,2147483647];e.float_properties=[-1,0.5,2]
create=packet.CreateEntity();create.entity=e
wu=packet.WorldUpdate();wu.loop_count=12345
wu.player_updates={3:((1.25,2.5,230.75),(1,0,-0.5),(-0.25,0.5,0),23,12344,100,0xb1,0xc5,9,7,255,12.5,2.25,-1.0)}
wu.updated_entities=[e];wu.rocket_turrets=[(-25536,-1,0.5)]
damage=packet.Damage();damage.player_id=3;damage.type=6;damage.damage=31.75;damage.face=4;damage.chunk_check=0;damage.seed=168;damage.causer_id=-1;damage.position=(1.25,2.5,230.75)
(ROOT/'java/src/test/resources/golden/complex.tsv').write_text('\n'.join(f'{name}\t{bytes(obj.generate()).hex()}' for name,obj in [('CreateEntity',create),('WorldUpdate',wu),('Damage',damage)])+'\n')
# Refresh provenance after all fixtures.
manifest['fixtures']={str(p.relative_to(ROOT/'java/src/test/resources/golden')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT/'java/src/test/resources/golden').rglob('*')) if p.is_file() and p.name!='provenance.json'}
manifest['catalogs']={str(p.relative_to(ROOT/'java/src/main/resources')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT/'java/src/main/resources/net/battlespades/catalog').glob('*.toml'))}
manifest['oracles']['admission']='original player_names; unmodified connection AST methods; original equipment/rules/class selection; Python 3.12 Unicode 15'
manifest['generators']+=['export_name_unicode.py','generate_admission_golden.py']
manifest['unicode_data']={'net/battlespades/player/name-unicode.bin.gz':hashlib.sha256((ROOT/'java/src/main/resources/net/battlespades/player/name-unicode.bin.gz').read_bytes()).hexdigest()}
(ROOT/'java/src/test/resources/golden/provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
