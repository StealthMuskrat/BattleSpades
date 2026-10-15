"""Execute source rules/class selection and common scene builders; no Java expected values."""
import gzip,json,pathlib,random,struct,sys
from types import SimpleNamespace as NS
ROOT=pathlib.Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
from server.game_rules import GameRules,RULE_DEFINITIONS
from server.class_selection import normalize_class_selection
from server.class_data import rule_speed_scale,get_movement
from server.builders.initial_info import build_initial_info
from server.builders.state_data import build_state_data
from server.map_metadata import DEFAULT_SKYBOX_NAME,normalize_skybox_name
from server import mode_data
from shared import constants as C
from aoslib import world,vxl
OUT=ROOT/'java/src/test/resources/golden'
def dump(v):return json.dumps(v,ensure_ascii=False,separators=(',',':'))
def typed(v):return ('bool:'+str(int(v))) if isinstance(v,bool) else ('int:'+str(v)) if isinstance(v,int) else 'float:'+float(v).hex()
rows=[]
for key,d in RULE_DEFINITIONS.items():
 for raw in [*d.choices,'ON','OFF','TRUE','FALSE','50%','100%','1','1.0','invalid',False,True,0,1,2,0.5,9999]:
  rules=GameRules.server_defaults()
  try:rules.apply({key:raw});expected=typed(rules.get(key))
  except ValueError:expected='error'
  rows.append(key+'\t'+dump(raw)+'\t'+expected)
(OUT/'rules.tsv').write_text('\n'.join(rows)+'\n')
rng=random.Random(168);rows=[]
for cid in range(-1,20):
 for variant in range(16):
  selected=[rng.randrange(75) for _ in range(16)] if variant>1 else [] if variant==0 else list(range(75))
  disabled=[i for i in range(75) if rng.random()<0.15] if variant%3==0 else []
  fallback=[None,0,12,255][variant%4]
  prefabs=['pillbox','PILLBOX','invalid',*[str(v) for names in C.PREFAB_LISTS.values() for v in names]][:40]
  ugc=list(range(30))
  selection=normalize_class_selection(cid,selected,prefabs,ugc,fallback_class_id=fallback,disabled_tools=disabled)
  rows.append('\t'.join([str(cid),dump(selected),dump(prefabs),dump(ugc),dump(disabled),str(-1 if fallback is None else fallback),str(selection.class_id),dump(selection.loadout),dump(selection.prefabs),dump(selection.ugc_tools)]))
(OUT/'selections.tsv').write_text('\n'.join(rows)+'\n')
rows=[]
for cid in range(18):
 for mode in ['tdm','zom']:
  for speed in [0.5,1.0,1.5,2.0]:
   for water in [False,True]:
    rules=GameRules.server_defaults();rules.apply({'RULE_CHARACTER_SPEED':speed,'RULE_CLASS_SPEED':2.0,'RULE_ENABLE_FALL_ON_WATER_DAMAGE':water})
    cfg=NS(game_mode=mode,game_rules=rules);scale=rule_speed_scale(cfg,cid);m=get_movement(cid)
    for control in ['walk','sprint','sneak']:
     sim=world.World(vxl.VXL(-1,b'',0,2));p=world.Player(sim)
     p.set_class_accel_multiplier(m.accel_multiplier*scale);p.set_class_sprint_multiplier(m.sprint_multiplier*scale);p.set_class_crouch_sneak_multiplier(m.crouch_sneak_multiplier*scale)
     p.set_position(100.5,100.5,100);p.set_walk(True,False,False,False);p.sprint=control=='sprint';p.sneak=control=='sneak'
     for _ in range(12):p.update(1/60,())
     expected=[p.position.x,p.position.y,p.position.z,p.velocity.x,p.velocity.y,p.velocity.z]
     rows.append('\t'.join([str(cid),mode,str(speed),str(int(water)),control,scale.hex(),*[float(v).hex() for v in expected]]))
(OUT/'rule-movement.tsv').write_text('\n'.join(rows)+'\n')
rows=[]
# Use common source builders before active-mode amendments; each input is present.
for code in [c for c in mode_data.MODES if not c.startswith('hc_')]:
 for variant in range(3):
  rules=GameRules.server_defaults()
  if variant:rules.apply({'RULE_CHARACTER_SPEED':1.5,'RULE_CLASS_SPEED':2.0,'RULE_ENABLE_CLASS_COMMANDO':False,'RULE_ENABLE_WEAPON_MINIGUN':False,'RULE_ENABLE_GRAVESTONES':False,'RULE_ENABLE_FALL_ON_WATER_DAMAGE':False,'RULE_ENABLE_FLARE_BLOCKS':False})
  cfg=NS(game_mode=code,map_name='ArcticBase',default_map='ArcticBase',server_name='Configured oracle',steam_id=90087911866072064,port=27015,same_team_collision=bool(variant),friendly_fire=bool(variant),game_rules=rules,score_limit=10,fog_color=(12,13,11))
  env=NS(fog_color=None,gravity=1.0,light_color=None,light_direction=None,back_light_color=None,back_light_direction=None,ambient_light_color=None,ambient_light_intensity=None,ground_colors=[])
  if variant==2:
   env=NS(fog_color=(69,76,39),gravity=26/64,light_color=(236,244,203),light_direction=(-0.7,0.3,0),back_light_color=(15,20,10),back_light_direction=(0,0.7,0.3),ambient_light_color=(15,30,10),ambient_light_intensity=0.3,ground_colors=[(18,52,86,255)])
  server=NS(config=cfg,world_manager=NS(map_name='ArcticBase',map_file_crc=0xdeadbeef,map_metadata=env),teams={0:NS(name='Blue',color=(0,64,255),score=7),1:NS(name='Green',color=(255,32,0),score=-3)},mode=NS(score_limit=200) if variant==2 else None,fog_color_override=(1,2,3) if variant==2 else None)
  # Source internal team ids are 0/1; StateData uses the map's team objects directly.
  from server.game_constants import TEAM1,TEAM2
  server.teams={TEAM1:NS(name='Blue',color=(0,64,255),score=7),TEAM2:NS(name='Green',color=(255,32,0),score=-3)}
  if variant==2:server.steam_master=NS(steam_id=123456789,public_ip=0xd5986593,query_active=False)
  rows.append('\t'.join([code,str(variant),bytes(build_initial_info(server).generate()).hex(),bytes(build_state_data(server,7).generate()).hex()]))
(OUT/'scene-builders.tsv').write_text('\n'.join(rows)+'\n')
rows=[]
for name in [None,'','User_Grassland.txt',' ArcticBase.txt ','path/foo.txt','../escape.txt','x.exe','X'*64+'.txt','X'*63+'.txt','a..b.txt','skybox_day','A\x00.txt','A\\B.txt','a\n.txt']:
 expected=normalize_skybox_name(name)
 rows.append(dump(name)+'\t'+dump(expected))
(OUT/'skyboxes.tsv').write_text('\n'.join(rows)+'\n')
print('rules',len((OUT/'rules.tsv').read_text().splitlines()),'selections',len((OUT/'selections.tsv').read_text().splitlines()),'movement profiles',len((OUT/'rule-movement.tsv').read_text().splitlines()),'scene builders',len((OUT/'scene-builders.tsv').read_text().splitlines()))
