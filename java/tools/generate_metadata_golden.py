"""Execute original map metadata loader and scene builders; preserve original policy bodies."""
import gzip, hashlib, io, json, logging, math, pathlib, random, struct, tempfile
from types import SimpleNamespace as NS
ROOT=pathlib.Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
from server import map_metadata as M, mode_data
from server.builders.initial_info import build_initial_info
from server.builders.state_data import build_state_data
from server.game_rules import GameRules
from server.game_constants import TEAM1,TEAM2
from shared import constants as C
OUT=ROOT/'java/src/test/resources/golden'
logging.disable(logging.CRITICAL)
def dump(value):return json.dumps(value,ensure_ascii=False,separators=(',',':'))
def canonical(value):
    if value is None:return b'N;'
    if isinstance(value,bool):return b'T;' if value else b'B;'
    if isinstance(value,(int,float)):
        if isinstance(value,int) or math.isfinite(value) and value.is_integer() and not (value==0 and math.copysign(1,value)<0):return b'I'+str(int(value)).encode()+b';'
        return b'F'+struct.pack('>d',value).hex().encode()+b';'
    if isinstance(value,str):
        data=value.encode();return b'S'+str(len(data)).encode()+b':'+data
    if isinstance(value,(list,tuple)):return b'L'+str(len(value)).encode()+b':'+b''.join(canonical(v) for v in value)
    if isinstance(value,dict):return b'D'+str(len(value)).encode()+b':'+b''.join(canonical(k)+canonical(value[k]) for k in sorted(value,key=lambda k:k.encode()))
    raise TypeError(type(value))
def emit(name,rows):
    raw=('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode();out=io.BytesIO()
    with gzip.GzipFile(filename='',mode='wb',fileobj=out,mtime=0) as stream:stream.write(raw)
    (OUT/(name+'.tsv.gz')).write_bytes(out.getvalue());print(name,len(rows))
def zone(z):return None if z is None else [z.kind,z.team,z.x,z.y,z.z,list(z.extents),z.item]
def snapshot(m):
    return dict(source=None if m.source is None else m.source.name,official=m.official_map,format=m.vxl_format,unofficial=m.unofficial,
        environment=[m.gravity,m.fog_color,m.light_color,m.light_direction,m.back_light_color,m.back_light_direction,m.ambient_light_color,m.ambient_light_intensity,m.ground_colors,m.skybox_name],
        regions={str(k):list(v) for k,v in m.fallback_spawn_regions.items()},spawns={str(k):[zone(z) for z in v] for k,v in m.spawn_zones.items()},bases={str(k):[zone(z) for z in v] for k,v in m.base_zones.items()},
        points={str(k):v for k,v in m.spawn_points.items()},neutral=[zone(z) for z in m.neutral_base_zones],occupation=zone(m.occupation_base_zone),bombs=m.occupation_bomb_points,diamonds=[zone(z) for z in m.diamond_base_zones],capacities=m.diamond_base_capacities,minimum={str(k):v for k,v in m.base_min_destruction.items()},
        entities=[[e.entity_type,e.kind,e.x,e.y,e.z,e.item,e.color] for e in m.entities],ambient=[[a.name,a.points,a.volume,a.attenuation] for a in m.ambient_sounds],static={str(k):v for k,v in m.static_light_colors.items()},
        achievements=[[a.api_name,a.kind,a.bounds,a.team,a.kills,a.weapon] for a in m.achievement_regions],layout=m.layout_sources,name=m.display_name,cap=m.cap_limit,time=m.time_limit,retail=[m.retail_modes,m.retail_playlist_modes,m.retail_max_players])
inputs=[];cases=[];pools=[];packet_rows=[]
def add(name,files,modes,label='synthetic'):
    index=len(inputs);inputs.append((index,name,dump(files)));cases.append((index,name,files,modes,label))
modes=['tdm','ctf','classic_ctf','mh','territory_control','occupation','zombie','dem','diamond_mine','vip','hc_ctf','arena','CUSTOM_TAG']
catalogue=(ROOT/'maps/retail_map_info.json').read_text()
source_inputs={}
for name in sorted(M.STOCK_MAP_SKYBOXES):
    # Restore actual basename spelling, including stock entries that have no sidecar or VXL in Git.
    entry=next((p for p in (ROOT/'maps').glob('*') if p.stem.casefold()==name and p.suffix in ('.json','.vxl')),None)
    stem=entry.stem if entry else {'lunarbase':'LunarBase','tokyoneon':'TokyoNeon','doubledragon':'DoubleDragon','tothebridge':'ToTheBridge','crossroads':'Crossroads','ww1':'WW1'}.get(name,name)
    files={'retail_map_info.json':catalogue}
    for ext in ['.json','.ugc','.txt','.vxl.json']:
        p=ROOT/'maps'/(stem+ext)
        if p.is_file():files[p.name]=p.read_text(encoding='utf-8-sig');source_inputs[str(p.relative_to(ROOT))]=hashlib.sha256(p.read_bytes()).hexdigest()
    add(stem,files,modes,'stock')
for p in sorted((ROOT/'maps/unofficial').glob('*.txt')):
    add(p.stem,{p.name:p.read_text(encoding='utf-8-sig')},['tdm','oc','zom','arena'],'community')
    source_inputs[str(p.relative_to(ROOT))]=hashlib.sha256(p.read_bytes()).hexdigest()
legacy="""name = 'Legacy \\U0001f431 map'
skybox_texture = 'MayanJungle.txt'
fog_color = 0x454c27
gravity = +0.4
ambient_sounds = [['em_river', [(10.5, 20.5, 236-3), (-1.5, -2.5, 3)], 1.0]]
ground_colors = [(85, 60, 30, 32), (31, 22, 11, 238)]
team_one_spawn_area = [((100, 110, 220), (20, 30, 40))]
team_two_spawn_area = [((400, 410, 221), (10, 20, 30))]
team_one_spawn = [(101,111,222)]
team_one_base_point = (90, 100, 222)
team_one_base_w_h_d = (12, 14, 16)
team_one_min_destruction = 10
ctf_base_points = [(127,255,222),(384,255,222),(500,500,222)]
ctf_base_w_h_d = [(10,10,10),(12,14,16),(10,10,10)]
mh_base_points = [(200,210,220),(300,310,221)]
mh_base_w_h_d = [(6,8,10),(12,14,16)]
tc_base_points = [(230,240,220)]
tc_base_w_h_d = [(6,8,10)]
occupation_base_point = (400,250,220)
occupation_base_w_h_d = (20,30,40)
occupation_bomb_points = [(100,110,221)]
diamond_base_points = [(200,210,220),(300,310,221)]
diamond_base_w_h_d = [(6,8,10),(12,14,16)]
diamond_base_teams = [0,3]
diamond_base_capacity = [0,4]
ammo_crate_drop_points = [(10,20,30)]
cap_limit = 100
time_limit = 480
raise RuntimeError('must never execute')
"""
add('Legacy',{'Legacy.txt':legacy},modes,'legacy')
ugc=[dict(position=[100+i,110,230],mode=mode,item=item) for i,item in enumerate(C.UGC_TOOL_IMAGES.values()) for mode in ['nor','all','any','occupation','classic_ctf','hc_tdm','CUSTOM_TAG','']]
ugc += [dict(position=[1,2,3],item=item,color=[4,5,6]) for item in ['flare_block','flareblock','glowblock','static_flare']]
ugc += [None,{},dict(position=[],item='ugc_health_drop'),dict(position=['bad',0,0],item='ugc_ammo_drop'),dict(position=[1,2,3],item='unknown')]
add('UGC',{'UGC.ugc':dump(dict(ugc_entities=ugc))},modes,'ugc')
add('Layered',{'Layered.json':dump(dict(gravity=None,skybox_texture='ArcticBase.txt',team_one_spawn_area=[])),
 'Layered.ugc':'\ufeff'+dump(dict(skybox_name='User_Lunar.txt',ugc_entities=ugc[:20],ground_colors=[[1,2,3,4]])),
 'Layered.txt':legacy.replace('MayanJungle.txt','WW1.txt'), 'Layered.vxl.json':dump(dict(light_color=[1,2,3],gravity=2))},modes,'layered')
for suffix,text in [('empty','{}'),('badjson','{invalid'),('list','[]'),('comment','# comment only'),('unsafe',"gravity = __import__('os').system('echo BAD')\nname = 'Safe'\n"),('arithmetic',"gravity = 2-1\ntime_limit = +(300+180)\ncap_limit = 2**10\n"),('multiline',"team_one_spawn_area = [\n ((1,2,3), (4,6,8)),\n]\ngravity=1\n"),('bom','\ufeff'+legacy),('invalidformat',dump(dict(vxl_format='invalid'))),('nulls',dump(dict(name=None,ugc_entities=None,ambient_sounds=None)))]:
    add('Parse',{'Parse.txt':text},['tdm'],'parse_'+suffix)
rng=random.Random(168)
for index in range(240):
    values=[None,False,True,[],{},'bad',-3,0,0.4,1,8,9,'1.5',' 32 ','1_000',16777215,16777216]
    p={k:rng.choice(values) for k in ['gravity','fog_color','light_color','ambient_light_intensity','cap_limit','time_limit','team_one_min_destruction','team_two_min_destruction']}
    p.update(name=rng.choice([None,'',123,' a '*50,'🐱'*80]),ground_colors=[[rng.randrange(-1,257) for _ in range(rng.choice([2,3,4,5]))] for _ in range(36)],ambient_sounds=[[rng.choice(['em_river','amb_rural','../escape']),[[0.5,1.5,2.5],[-0.5,-1.5,-2.5]],rng.choice([0,1,4,5,'bad']),rng.choice([0,1,16,17])]])
    # Include independently malformed rows, parallel-array truncation and strict per-mode overrides.
    p.update(team_one_spawn_area=[None,[],[[10,20,30],[0,2,4]],[[100+index%5,110,220],[10,12,16]]],ctf_base_points=[[1,2,3],['bad',0,0],[400,410,220]],ctf_base_w_h_d=[[4,6,8],[4,6,8],[8,10,12]],diamond_base_points=[[10,20,30],[40,50,60]],diamond_base_w_h_d=[[2,4,6],[8,10,12]],diamond_base_teams=[rng.choice([0,2,3,4,'bad'])],diamond_base_capacity=[rng.choice([0,-3,5,'bad'])])
    add('Random',{'Random.json':dump(p)},[modes[index%len(modes)]],'coercion')
source_inputs['maps/retail_map_info.json']=hashlib.sha256((ROOT/'maps/retail_map_info.json').read_bytes()).hexdigest()
for value in ['nan','NaN','inf','-Infinity','+infinity','1__0','_10','10_','1_e2','1e_2','1_2.5_0','1.',' .5 ','1d','١٢','\u00a032\u00a0','0x10','+2','-2','1e309','1e-400','9'*80]:
    add('Numbers',{'Numbers.json':dump(dict(gravity=value,ambient_light_intensity=value,cap_limit=value,time_limit=value,team_one_min_destruction=value,diamond_base_points=[[1,2,3]],diamond_base_w_h_d=[[4,6,8]],diamond_base_capacity=[value]))},['tdm'],'numeric')
add('Unicode',{'Unicode.txt':"name = '\\u00a0Cat🐱\\u00a0'\nfog_color=69, 76, 39\ngravity=+0.4; time_limit=480\nskybox_name='\\u00a0Mayan' 'Jungle.txt\\u00a0'\n"},['tdm'],'legacy_unicode')
add('IntegerMath',{'IntegerMath.txt':"gravity = -9007199254740993+9007199254740994\ncap_limit = -9007199254740993+9007199254740994\n"},['tdm'],'legacy_integer_arithmetic')
expected=[]
with tempfile.TemporaryDirectory() as directory:
    root=pathlib.Path(directory)
    for index,name,files,active_modes,label in cases:
        folder=root/str(index);folder.mkdir()
        for file,text in files.items():(folder/file).write_text(text,encoding='utf-8')
        map_path=folder/(name+'.vxl')
        for mode in active_modes:
            try:
                metadata=M.load_map_metadata(map_path,mode);result=snapshot(metadata)
            except (TypeError,ValueError,OverflowError) as error:
                expected.append((index,mode,label,'error',type(error).__name__));continue
            expected.append((index,mode,label,'ok',dump(result),hashlib.sha256(canonical(result)).hexdigest()))
            code=M.canonical_mode_code(mode)
            if code not in mode_data.MODES or code.startswith('hc_'):continue
            rules=GameRules.server_defaults();cfg=NS(game_mode=code,map_name=name,default_map=name,server_name='Metadata oracle',steam_id=0,port=27015,same_team_collision=False,friendly_fire=False,game_rules=rules,score_limit=10,fog_color=(12,13,11))
            server=NS(config=cfg,world_manager=NS(map_name=name,map_file_crc=0xdeadbeef,map_metadata=metadata),teams={TEAM1:NS(name='Blue',color=(0,64,255),score=7),TEAM2:NS(name='Green',color=(255,32,0),score=-3)},mode=None,fog_color_override=None)
            packet_rows.append((index,mode,bytes(build_initial_info(server).generate()).hex(),bytes(build_state_data(server,7).generate()).hex()))
        for mode in modes:
            pools.append((index,mode,dump(M.retail_mode_pool(folder,mode))))
emit('metadata-inputs',inputs);emit('metadata-results',expected);emit('metadata-packets',packet_rows);emit('metadata-pools',pools)
provenance=dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',policy_substitutions=False,input_groups=len(inputs),metadata_vectors=len(expected),packet_pairs=len(packet_rows),pool_vectors=len(pools),source_files={name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in ['server/map_metadata.py','server/mode_data.py','server/hardcore.py','shared/constants.py','server/builders/initial_info.py','server/builders/state_data.py']},source_inputs=source_inputs,fixtures={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(OUT.glob('metadata-*.tsv.gz'))})
(OUT/'metadata-provenance.json').write_text(json.dumps(provenance,indent=2)+'\n')
