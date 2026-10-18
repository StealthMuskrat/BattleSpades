"""Intact source music/ambience and next-map ballot policies with native packet bytes.

Clocks, random seeds, catalog inputs and actor/connection presence are controlled.
Candidate ranking/discovery, kick votes and full server bootstrap are outside scope.
No source policy method is replaced; asynchronous waiters use the real event method.
"""
import asyncio,hashlib,json,logging,pathlib,random,tempfile
from types import SimpleNamespace as N
from unittest.mock import patch
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from server import audio,voting
from shared import packet
from shared.bytes import ByteReader
from server.map_metadata import MapAmbientSound
logging.disable(logging.CRITICAL);rows=[];codec={}
def keep(data):
 data=bytes(data);kind={22:packet.CreateAmbientSound,24:packet.PlayAmbientSound,26:packet.PlayMusic,47:packet.GenericVoteMessage}.get(data[0])
 if kind:codec[data.hex()]=bytes(kind(ByteReader(data[1:])).generate()).hex()
 return data.hex()
for seed in [0,1,3,9]:
 s=N(config=N(mode_start_music=True),audio_music_family=None);wire=[];direct=[];s.broadcast=lambda data:wire.append(keep(data));steps=[]
 with patch.object(audio,'random',random.Random(seed)):
  for op,arg in [('gameplay',True),('ending',None),('ending',None),('timeout',None),('ending',None),('last',None),('last',None),('play','track_²'),('play','track_１２'),('stop',None),('gameplay',False),('direct','mainmenu')]:
   if op=='gameplay':s.config.mode_start_music=arg;result=audio.play_gameplay_music(s)
   elif op=='ending':result=audio.play_ending_music(s)
   elif op=='timeout':result=audio.play_timeout_music(s)
   elif op=='last':result=audio.play_last_man_music(s)
   elif op=='play':result=audio.play_music(s,arg,32)
   elif op=='stop':result=audio.stop_music(s)
   else:result=audio.play_music_to(N(send=lambda data:direct.append(keep(data))),arg)
   steps.append(dict(op=op,arg=arg,result=result,family=s.audio_music_family,wire=list(wire),direct=list(direct)))
 rows.append(dict(domain='music',seed=seed,steps=steps))
for case in range(3):
 definitions=[MapAmbientSound('amb_rural')]
 if case==1:definitions=[MapAmbientSound('amb_river',((12,24,100),(42,56,101)),.5,2),MapAmbientSound('amb_rural',(),.75,0)]
 if case==2:definitions=[MapAmbientSound('amb_rural')]*256
 wire=[];p=N(x=36.25,y=47.5,z=97.75,send=lambda data:wire.append(keep(data)))
 audio.send_map_ambient(N(world_manager=N(map_metadata=N(ambient_sounds=definitions))),p)
 rows.append(dict(domain='ambience',case=case,definitions=[dict(name=d.name,points=d.points,volume=d.volume,attenuation=d.attenuation) for d in definitions],wire=wire))
async def ballot_case(case):
 wire=[];direct=[];clock=N(mono=100.0);wait=None;returns=[]
 with tempfile.TemporaryDirectory() as directory:
  catalog=['London','Other','Arena'] if case!=10 else []
  for name in catalog:(pathlib.Path(directory)/(name+'.vxl')).write_bytes(b'')
  players={i:N(id=i,is_bot=i==2,connection=N(in_game=i!=3)) for i in range(6)};connections={i:p.connection for i,p in players.items() if i not in (2,4)}
  s=N(config=N(maps_path=directory,map_rotation=[],default_map='London'),players=players,connections=connections,broadcast=lambda data:wire.append(keep(data)))
  actions=[['start',['London','Other','Arena'],1000]]
  extra={0:[['cast',0,1],['cast',1,1],['cast',5,0]],1:[['cast',0,0],['cast',0,0],['cast',0,1],['wire',1,'Other'],['wire',1,voting._retail_dynamic_text('Other')],['cast',5,0]],2:[['cast',2,2],['cast',3,2],['cast',4,2],['replace',0],['stale',0,2],['cast',0,1],['mono',110],['tick',None]],3:[['cast',0,1],['cast',1,1],['forget',5]],4:[['cast',0,2],['mono',110],['cast',2,-1]],5:[['cast',0,1],['tick',1010]],6:[['wait'],['cancel'],['start',['Arena','Other'],1001],['cast',0,1],['cast',1,1],['cast',5,1]],7:[['wait'],['cast',0,1],['cast',1,1],['cast',5,0]],8:[['mono',110],['wait']],9:[['mono',110],['reveal']],10:[['cancel'],['start',['../bad','.',"O'Brien",'é{曲}','a\\b','extra'],1001],['cast',0,1],['cast',1,1],['cast',5,0]],11:[['cancel'],['start',['missing',' Other.VXL ','other','London','Arena'],1001],['reveal'],['mono',110],['tick',None]],12:[['cast',0,0],['cast',1,1],['forget',0],['forget',5]],13:[['cast',0,2],['cast',1,0],['cast',5,1],['consume'],['start',[],1001]],14:[['wire',0,"__import__('os').system('x')"],['cast',1,99],['mono',110],['tick',None]],15:[['cancel'],['start',['\n','\x00','x'*129,'Other'],1001],['cast',0,0],['cast',1,0],['cast',5,0]]}[case];actions+=extra
  stale={};steps=[]
  with patch.object(voting,'time',N(monotonic=lambda:clock.mono,time=lambda:1000.0)):
   manager=voting.VoteManager(s)
   for action in actions:
    op=action[0];result=None
    if op=='start':result=manager.start_map_vote(action[1],action[2])
    elif op=='cast':manager._cast_index(players[action[1]],action[2])
    elif op=='stale':manager._cast_index(stale[action[1]],action[2])
    elif op=='wire':manager.cast_wire_candidate(players[action[1]],action[2])
    elif op=='mono':clock.mono=action[1]
    elif op=='tick':manager.tick(action[1])
    elif op=='replace':
     old=players[action[1]];stale[action[1]]=old;players[action[1]]=N(id=old.id,is_bot=False,connection=old.connection)
    elif op=='forget':manager.forget_player(action[1])
    elif op=='cancel':manager.cancel()
    elif op=='consume':result=manager.consume_next_map()
    elif op=='reveal':manager.reveal_to(N(send=lambda data,**kw:direct.append(keep(data))))
    elif op=='wait':wait=asyncio.create_task(manager.wait_for_map_result());await asyncio.sleep(0)
    steps.append(dict(action=action,result=result,active=manager.active,candidates=manager.candidates,votes=manager.votes.copy(),next=manager.next_map,wire=list(wire),direct=list(direct)))
   if wait is not None:
    assert not manager.active or case==6
    returns.append(await wait)
  rows.append(dict(domain='ballot',case=case,catalog=catalog,steps=steps,wait_results=returns))
async def main():
 for case in range(16):await ballot_case(case)
asyncio.run(main())
# Additional authored wire boundaries retain the native decode/re-encode quirks.
for name,seconds in [('曲',-1.25),('long',255.5)]:keep(audio._play_music_bytes(name,seconds))
for flags in range(4):
 p=packet.PlayAmbientSound();p.name='stream';p.looping=flags&1;p.positioned=bool(flags&2);p.volume=.5;p.time=1.25;p.loop_id=255;p.x=-.25;p.y=2.5;p.z=238;p.attenuation=.75;keep(p.generate())
keep(bytes.fromhex('2fff01ffff000005'))
rows.append(dict(domain='codecs',vectors=codec))
out=ROOT/'java/src/test/resources/golden/round-media.json';out.write_text(json.dumps(dict(cases=rows),separators=(',',':'))+'\n')
cat=ROOT/'java/src/main/resources/net/battlespades/catalog/round-media.toml';cat.write_text('gameplay = '+json.dumps(audio.GAMEPLAY_TRACKS)+'\nending = '+json.dumps(audio.GAME_ENDING_TRACKS)+'\nmapVoteSeconds = '+str(voting.MAP_VOTE_DURATION)+'\nfamilyDigits = '+json.dumps([cp for cp in range(0x110000) if chr(cp).isdigit()])+'\n')
files=['server/audio.py','server/voting.py','server/map_metadata.py','server/announcements.py','server/handlers/social.py','shared/packet.pyx']
(out.parent/'round-media-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',cases=len(rows),native_codec_vectors=len(codec),fixture_sha256=hashlib.sha256(out.read_bytes()).hexdigest(),catalog_sha256=hashlib.sha256(cat.read_bytes()).hexdigest(),policy_method_substitutions=False,source_files_sha256={f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest() for f in files},scope=__doc__.strip()),indent=2)+'\n')
print('Round media source oracle',len(rows),'cases;',len(codec),'distinct native codec vectors')
