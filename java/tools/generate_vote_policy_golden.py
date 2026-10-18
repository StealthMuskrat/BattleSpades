"""Intact VoteManager discovery/fit/rotation/kick/arbitration policies and real native packets.

Filesystem catalogs/nav bytes, clocks, random seeds and actor/terminal disconnect
services are controlled. No policy method is replaced. No supported-client claim.
"""
import hashlib,json,logging,pathlib,random,struct,tempfile,zlib
from types import SimpleNamespace as N
from unittest.mock import patch
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from server import voting
logging.disable(logging.CRITICAL);rows=[]
def nav(width=8,height=8,dry=32,tail=True):
 n=width*height;payload=bytes(2*n)+bytes([2]*dry+[1]*(n-dry))+(bytes(17*n) if tail else b'');c=zlib.compress(payload)
 return struct.pack('<8sHHHH16sII',b'BSNAV01\0',1,width,height,238,bytes(16),len(payload),len(c))+c
navs=[None,b'',b'bad'+bytes(37),nav()[:20],nav()[:40]+b'bad',nav(tail=False),nav()[:-3],nav(dry=0),nav(dry=64),struct.pack('<8sHHHH16sII',b'BSNAV01\0',1,65535,65535,238,bytes(16),0,0)+zlib.compress(b'')]
with tempfile.TemporaryDirectory() as directory:
 for case,data in enumerate(navs):
  name='Nav'+str(case)
  if data is not None:(pathlib.Path(directory)/(name+'.botnav')).write_bytes(data)
  rows.append(dict(domain='navigation',case=case,data=None if data is None else data.hex(),area=voting.map_playable_area(directory,name)))
retail={'maps':{'Tiny':dict(max_players=2,release=True,invalid_modes=[]),'Small':dict(max_players=6,release=True,invalid_modes=['ctf']),'Medium':dict(max_players=12,release=True,invalid_modes=[]),'Large':dict(max_players=32,release=False,invalid_modes=[]),'Huge':dict(max_players=32,release=True,mafia=True,invalid_modes=[])},'playlists':{'tdm':dict(maps=['Small','Tiny','Large','Huge','Medium'],modes=['tdm'],classic=False,mafia=False),'ctf':dict(maps=['Small','Medium','Tiny'],modes=['ctf'],classic=False,mafia=False)}}
for case in range(12):
 with tempfile.TemporaryDirectory() as directory:
  names=['Tiny','Small','Medium','Large','Huge','Custom'];sizes=[250,450,1000,2400,3900,None]
  if case==11:names+=['é','𐀀','\uffff'];sizes +=[None]*3
  for name,size in zip(names,sizes):
   (pathlib.Path(directory)/(name+'.vxl')).write_bytes(b'')
   if size is not None:(pathlib.Path(directory)/(name+'.botnav')).write_bytes(nav(64,64,size))
  (pathlib.Path(directory)/'retail_map_info.json').write_text(json.dumps(retail))
  requested=['Custom','small','missing','Tiny','Medium','Huge'] if case in (1,2,7,8) else []
  overrides={'Tiny':'large','SMALL':1200,'Custom':'unknown'} if case==5 else {}
  humans=[0,2,4,8,12,4,8,4,8,2,1,2][case];bots=case%3;current='Small' if case not in (3,10) else 'missing';mode='classic_ctf' if case==8 else 'ctf' if case in (6,7) else 'tdm';stock=['Custom','Huge'] if case==4 else ['absent'] if case==9 else []
  config=N(maps_path=directory,map_rotation=requested,map_rotation_shuffle=case in (2,3),map_size_overrides=overrides,map_vote_size_fit=case!=10,map_vote_area_per_player=100.0,map_vote_min_area=200.0,map_vote_recent_exclude=0 if case==6 else 2,map_vote_bot_weight=.5,map_vote_retail_max_players=case!=3,default_map=current,game_mode=mode)
  players={i:N(id=i,is_bot=i>=humans) for i in range(humans+bots)};s=N(config=config,players=players,connections={},mode=N(stock_maps=stock),broadcast=lambda data:None)
  with patch.object(voting,'random',random.Random(100+case)):
   manager=voting.VoteManager(s)
  history=[' Tiny ','TINY','Large','Medium','Tiny','Huge','Small','Custom'] if case not in (0,11) else []
  for name in history:manager.note_map_played(name)
  available=manager._mode_available_maps();ranked=manager.rank_map_candidates(available,current)
  rows.append(dict(domain='catalog',case=case,names=names,sizes=sizes,retail=json.dumps(retail),requested=requested,shuffle=config.map_rotation_shuffle,overrides=overrides,humans=humans,bots=bots,current=current,mode=mode,stock=stock,settings=dict(sizeFit=config.map_vote_size_fit,perPlayer=100.0,minimum=200.0,recent=config.map_vote_recent_exclude,botWeight=.5,caps=config.map_vote_retail_max_players),history_inputs=history,available=manager._available_maps,mode_available=available,areas={name:manager.map_area(name) for name in names},history=list(manager.map_history),ranked=ranked))
for case in range(26):
 with tempfile.TemporaryDirectory() as directory:
  for name in ['First','Other','Third']:(pathlib.Path(directory)/(name+'.vxl')).write_bytes(b'')
  wire=[];direct=[];disconnected=[];clock=N(mono=100.0,wall=1000.0);stale={};manager=None
  def actor(i):
   p=N(id=i,team=2 if i<4 else 0 if i==5 else 3,name=('S{é}' if i==0 else "T'曲" if i==1 else 'P'+str(i)),is_bot=i==4,protected=False,fail_host=False,host='10.0.0.'+str(i+1));p.connection=N(in_game=i!=6,peer=N(address=p.host+':1234'))
   p.send=lambda data,**kw:direct.append([p.id,bytes(data).hex()])
   def disconnect(reason=0):
    disconnected.append([p.id,reason]);p.connection.in_game=False;s.players.pop(p.id,None);manager.forget_player(p.id)
   p.disconnect=disconnect;return p
  players={i:actor(i) for i in range(7)}
  def is_host(p):
   if p.fail_host:raise RuntimeError('advisory host lookup failed')
   return p.protected
  config=N(maps_path=directory,map_rotation=[],default_map='First',game_mode='tdm',votekick_min_team_players=3,votekick_cooldown_seconds=300.0,votekick_cancelled_cooldown_seconds=45.0,game_rules={'RULE_VOTES_REQUIRED_FOR_KICK':.5})
  s=N(config=config,players=players,connections={i:p.connection for i,p in players.items() if i!=4},mode=N(is_host=is_host,stock_maps=[]),broadcast=lambda data:wire.append(bytes(data).hex()))
  actions={0:[['start',0,1,0],['cast',2,0]],1:[['start',0,1,1],['cast',2,0]],2:[['start',0,1,2],['cast',2,0]],3:[['start',5,1,0]],4:[['start',0,0,0]],5:[['set',1,'protected',True],['start',0,1,0]],6:[['set',1,'fail_host',True],['start',0,1,0],['cancel',False,None]],7:[['map'],['start',0,1,0],['round_end']],8:[['start',0,1,0],['start',2,1,0],['round_end']],9:[['set',2,'is_bot',True],['set',3,'is_bot',True],['start',0,1,0]],10:[['start',0,1,0],['cancel',True,1000],['replace',0],['start',0,1,0],['wall',1045],['start',0,1,0]],11:[['start',0,1,0],['cast',2,1],['mono',130],['tick',None]],12:[['start',0,1,0],['cast',1,0],['cast',4,0],['cast',6,0],['cast',2,1],['cast',2,0]],13:[['replace',0],['stale_start',0,1,0]],14:[['start',0,1,0],['forget',1]],15:[['start',0,1,0],['forget',0]],16:[['start',0,1,0],['cast',2,1],['forget',2],['reveal']],17:[['start',0,1,0],['wire',2,'yes'],['wire',2,voting._retail_localised_text('KICK_YES')]],18:[['start',0,1,0],['cancel',False,None],['wall',1299.1],['start',0,1,0],['wall',1300],['start',0,1,0]],19:[['set',1,'is_bot',True],['start',0,1,0]],20:[['set',0,'host',None],['start',0,1,0],['cancel',False,None],['forget',0],['start',0,1,0]],21:[['start',0,1,0],['cast',2,0],['reset']],22:[['start',0,1,0],['tick',1030]],23:[['start',0,1,99],['start',4,1,0],['start',6,1,0]],24:[['set',0,'name','\x00'+('é'*70)],['start',0,1,0],['reveal'],['cancel',True,None]],25:[['start',0,1,0],['cast',2,1],['cast',3,1],['cast',5,1],['mono',130],['reveal']]}[case]
  with patch.object(voting,'time',N(monotonic=lambda:clock.mono,time=lambda:clock.wall)):
   manager=voting.VoteManager(s);steps=[]
   for action in actions:
    op=action[0];result=None
    if op in ('start','stale_start'):result=manager.start_kick((stale if op=='stale_start' else players)[action[1]],players[action[2]],action[3],clock.wall)
    elif op=='cast':manager._cast_index(players[action[1]],action[2])
    elif op=='wire':manager.cast_wire_candidate(players[action[1]],action[2])
    elif op=='set':
     p=players[action[1]];setattr(p,action[2],action[3])
     if action[2]=='host':p.connection.peer=None if action[3] is None else N(address=action[3]+':1234')
    elif op=='replace':
     old=players[action[1]];stale[old.id]=old;p=actor(old.id);p.host=old.host;p.connection=old.connection;players[p.id]=p
    elif op=='cancel':manager.cancel(by_starter=action[1],now=action[2])
    elif op=='forget':manager.forget_player(action[1])
    elif op=='tick':manager.tick(action[1])
    elif op=='mono':clock.mono=action[1]
    elif op=='wall':clock.wall=action[1]
    elif op=='map':result=manager.ensure_map_vote(clock.wall)
    elif op=='round_end':result=manager.ensure_round_end_map_vote(clock.wall)
    elif op=='reset':manager.cancel();manager.consume_next_map();manager.clear_match_kicks()
    elif op=='reveal':manager.reveal_to(N(send=lambda data,**kw:direct.append([-1,bytes(data).hex()])))
    steps.append(dict(action=action,result=result,active=manager.active,kind=manager.kind,starter=manager.starter_id,target=manager.target_id,votes=manager.votes.copy(),cooldowns={str(k):v for k,v in manager._cooldown_until.items()},match_kicks=manager._match_kicks.copy(),next=manager.next_map,wire=list(wire),direct=list(direct),disconnected=list(disconnected)))
  rows.append(dict(domain='kick',case=case,steps=steps))
fits=[]
for area,players in [(None,0),(0,0),(-1,0),(175,1),(176,1),(300,1),(301,1),(100,0),(262144,1000),(24000,1)]:fits.append(dict(area=area,players=players,tier=voting.map_fit_tier(area,players,per_player=100,min_area=100)))
rows.append(dict(domain='fit',vectors=fits))
out=ROOT/'java/src/test/resources/golden/vote-policy.json';out.write_text(json.dumps(dict(cases=rows),separators=(',',':'))+'\n')
files=['server/voting.py','server/map_metadata.py','server/bans.py','server/announcements.py','server/handlers/social.py','shared/packet.pyx']
(out.parent/'vote-policy-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',cases=len(rows),domains={k:sum(r['domain']==k for r in rows) for k in set(r['domain'] for r in rows)},fixture_sha256=hashlib.sha256(out.read_bytes()).hexdigest(),policy_method_substitutions=False,source_files_sha256={f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest() for f in files},scope=__doc__.strip()),sort_keys=True,indent=2)+'\n');print('Vote policy source oracle',len(rows),'cases')
