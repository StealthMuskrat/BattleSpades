"""Intact original A2S/population/tags and runtime metrics reference vectors."""
import json,pathlib,struct
from types import SimpleNamespace as NS
from unittest.mock import patch
ROOT=pathlib.Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
from server.config import ServerConfig
from server import a2s_query as A
from server.metrics import RuntimeMetrics
from server.steam_master import build_game_tags,server_population
config=NS(game_mode='cctf',port=32887,max_players=4,score_limit=999,steam_id=90087911866072064,steam=NS(protocol_version=168,playlist_id=8,game_version='1.0.0.0'))
config.server_name='Café 🎮';config.map_name='Configured';config.join_password='secret';config.friendly_fire=True;config.fall_damage=False;config.respawn_time=5.9;config.team1_name='Bleu';config.team2_name='Grün';config.steam.region='eu';config.steam.texture_skin='';config.steam_relay_tags=['relay=123','x'*128]
p0=NS(id=7,name='Zoë',score=2**40,is_bot=False);p1=NS(id=2,name='',score=-2**40,is_bot=True)
server=NS(config=config,players={7:p0,2:p1},connections={0:NS(player=p0),1:NS(player=None,reserved_player_id=1)},world_manager=NS(map_name='Live Map'),mode=NS(score_limit=10),teams={2:NS(score=4),3:NS(score=5)},steam_master=None)
h=A.A2SHandler(server);h.challenge=0x12345678
prefix=b'\xff'*4;query=prefix+b'TSource Engine Query\0';valid=struct.pack('<i',h.challenge)
requests=[b'HELLO',b'HELLOLAN',b'junk',prefix+b'T',query,query+valid,query+b'\0'*4,prefix+b'U',prefix+b'U'+valid,prefix+b'V'+valid,prefix+b'W',prefix+b'?',prefix+b'Tbad',prefix+b'V'+b'\0'*3,query+b'x',query+valid+b'extra']
with patch.object(A.time,'monotonic',return_value=100.):
 rows=[dict(request=d.hex(),response=(response.hex() if (response:=h.handle_packet(d,('127.0.0.1',1))) else None)) for d in requests]
with patch.object(A.time,'monotonic',return_value=103.25):duration=h._make_player_response().hex()
server.players[7]=NS(id=7,name='New',score=3,is_bot=False)
with patch.object(A.time,'monotonic',return_value=104.):replacement=h._make_player_response().hex()
tags=[]
for mode in ['tdm','cctf','zombie','hc_tdm','dem','mafia']:
 config.game_mode=mode
 # game_mode is a normalized property of default_mode.
 base=build_game_tags(config);extra=base+';gamemode='+A.get_mode_data(config.game_mode).code
 tags.append(dict(mode=config.game_mode,value=extra if len(extra.encode())<128 else base))
metrics=RuntimeMetrics();snapshots=[]
for samples in [[],[2,8],[4,1,7],[9,3,8,2],[float(i) for i in range(36005)]]:
 metrics.reset_timing_samples()
 for n in samples:metrics.record_tick(n)
 metrics.record_subsystem('z',3);metrics.record_subsystem('a',1);metrics.record_subsystem('a',2)
 metrics.record_world_packet(9,3);metrics.record_world_delivery(2,99);snapshots.append(metrics.snapshot())
metrics.reset_timing_samples();snapshots.append(metrics.snapshot())
result=dict(requests=rows,duration=duration,replacement=replacement,tags=tags,metrics=snapshots)
(ROOT/'java/src/test/resources/golden/runtime-services.json').write_text(json.dumps(result,indent=2)+'\n')
print(len(rows),'wire requests;',len(tags),'mode tags;',len(snapshots),'metric windows')
