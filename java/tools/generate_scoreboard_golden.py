"""Unchanged scoreboard, real round award state/random stream and native scoreboard codecs."""
import gzip,hashlib,io,json,pathlib,random,struct
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server import scoreboard
from server.combat_scores import RoundCombatStats
from server.profile_stats import ProfileStats
from server.team import Team
from shared.packet import GameStats,ShowTextMessage
from shared.bytes import ByteReader
bits=lambda v:struct.pack('>d',float(v)).hex();rows=[];fault_count=0;stats_faults=0
class Actor:
 pass
for trace in range(16):
 wire=[];events=[];fail=[False];server=NS(players={},teams={2:Team(2,'Blue',(1,2,3)),3:Team(3,'Green',(4,5,6))},config=NS(default_mode='tdm',ugc_runtime=False),mode=NS(ended=False),_game_stats_rng=random.Random(816700+trace),_stopping=False)
 actors=[]
 for key in range(4):
  p=Actor();p.key=key;p.id=[0,1,2,0][key];p.team=3 if key==2 else 2;p.score=key*25;p.kills=key;p.deaths=key;p.captures=1;p.kill_streak=2;p.round_combat_stats=RoundCombatStats();p.damage_contributions={1:'controlled'};p.profile_stats=ProfileStats();p.connection=NS(server=server);p.is_bot=trace%3==0;actors.append(p)
 server.players={0:actors[0],1:actors[1],2:actors[2]}
 def send(data,tag):
  wire.append(tag+':'+bytes(data).hex())
  if fail[0]:raise RuntimeError('send-fault')
 def broadcast(data,**kw):send(data,'B'+str(int(kw.get('reliable',True)))+','+str(kw.get('known_player_id','-')))
 server.broadcast=broadcast
 class Peer:
  def __init__(self,key):self.key=key;self.in_game=True;self.known_player_lives=None
  def send(self,data,**kw):assert kw.get('reliable') is True;send(data,'P'+str(self.key))
 peers=[Peer(i) for i in range(3)];server.connections={i:p for i,p in enumerate(peers)}
 server.revival_master=NS(reset_scoreboard_baselines=lambda:events.append('baseline'),schedule_round_results=lambda winner:events.append('result:'+str(winner)))
 for step in range(128):
  wire.clear();events.clear();op=(step+trace)%12;actor=actors[(step//3+trace)%4];value=step*31-trace*7-100;reason=[None,0,1,100,202,220][step%6];seconds=[float('nan'),float('inf'),float('-inf'),-3.,0.,59.999,3600.,5400.,2.675][(step//4+trace)%9];server.mode.ended=step%9==0;server._stopping=step%23==0
  known=[None,{0},{0,1,2}][(step//5+trace)%3]
  for peer in peers:peer.known_player_lives=known;peer.in_game=(peer.key!=2 or step%4!=0)
  server.connections=None if step%19==0 else {i:p for i,p in enumerate(peers)}
  fail[0]=(step+trace)%17==0;result='-';outcome='ok'
  try:
   if op==0:actor.score=value;scoreboard.send_player_score(server,actor,reason=reason)
   elif op==1:
    team=server.teams[2+step%2];team.score=value;scoreboard.send_team_score(server,team,reason=reason)
   elif op==2:scoreboard.send_round_timer(server,seconds,reliable=step%2==0)
   elif op==3:scoreboard.broadcast_game_stats(server,winner=2+step%2)
   elif op==4:scoreboard.reveal_to(server,peers[step%3])
   elif op==5:scoreboard.reset_round_scores(server)
   elif op==6:
    kind=step//12%5
    if kind==0:scoreboard.show_game_stats(server)
    elif kind==1:scoreboard.force_show_scores(server,step%2==0)
    elif kind==2:scoreboard.send_lock_team(server,[-1,0,1,2,3,255][step%6],step%2==0)
    elif kind==3:scoreboard.send_team_lock_class(server,[-1,0,1,2,3,255][step%6],step%2==0)
    else:scoreboard.send_map_ended(server)
   elif op==7:scoreboard.send_show_text_message(server,step%15-3,seconds)
   elif op==8:
    if step%3==0:server.players[0]=actors[3]
    elif step%3==1:server.players.pop(0,None)
    else:server.players[0]=actors[0]
    actors[0].score=value;scoreboard.send_player_score(server,actors[0],reason=reason)
   elif op==9:
    for i,p in enumerate(actors):
     for award in range(30):p.round_combat_stats.awards[award]=(step+award+i*3)%9
     p.round_combat_stats.measures['shots']=float((step+i*7)%40)
    result='|'.join(f'{pid},{award}' for pid,award in scoreboard._team_awards(server,2)) or '-'
   elif op==10:server.teams[2].add_capture();server.teams[3].add_kill();scoreboard.send_team_score(server,server.teams[2])
   else:actor.score=value;scoreboard.send_player_score(server,actor)
  except RuntimeError as e:outcome=str(e);fault_count+=1;stats_faults+=op==3
  cache='|'.join(bytes(p).hex() for p in getattr(server,'_game_stats_packets',())) or '-'
  state=';'.join([str(int(getattr(server,'_game_stats_sent',False))),cache,','.join(f'{id}:{p.key}' for id,p in server.players.items()),','.join(str(p.profile_stats.last_score) for p in actors),'|'.join(','.join(map(str,[p.score,p.kills,p.deaths,p.captures,p.kill_streak,int(bool(p.damage_contributions))]))+':'+','.join(f'{k}={v}' for k,v in p.round_combat_stats.awards.items())+':'+','.join(f'{k}={bits(v)}' for k,v in p.round_combat_stats.measures.items()) for p in actors),','.join(str(server.teams[t].score) for t in (2,3))])
  rows.append(('op',trace,step,op,actor.key,value,'-' if reason is None else reason,bits(seconds),int(fail[0]),int(server.mode.ended),int(server._stopping),'-' if known is None else ','.join(map(str,sorted(known))),int(server.connections is None),result,outcome,'|'.join(wire) or '-',','.join(events) or '-',state))
for case in range(512):
 p=GameStats();p.noOfStats=-3 if case%31==0 else case%8;p.team_id=(case*10000019+2147483648)%4294967296-2147483648;p.player_ids=[(case*71+i*10000019+2147483648)%4294967296-2147483648 for i in range(max(0,p.noOfStats))];p.types=[case+i-256 for i in range(max(0,p.noOfStats))];raw=bytes(p.generate());decoded=GameStats(ByteReader(raw[1:]));rows.append(('codec',case,p.noOfStats,p.team_id,','.join(map(str,p.player_ids)) or '-',','.join(map(str,p.types)) or '-',raw.hex(),bytes(decoded.generate()).hex()))
for case in range(512):
 p=ShowTextMessage();p.message_id=case%256;p.duration=(case*73%64000-32000+.5)/64;raw=bytes(p.generate());decoded=ShowTextMessage(ByteReader(raw[1:]));rows.append(('text',case,p.message_id,bits(p.duration),raw.hex(),bytes(decoded.generate()).hex(),bits(decoded.duration)))
assert fault_count>0 and stats_faults>0,(fault_count,stats_faults)
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
p=OUT/'scoreboard.tsv.gz';p.write_bytes(buffer.getvalue());(OUT/'scoreboard-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',scoreboard_operations=2048,native_game_stats_cases=512,native_text_cases=512,send_faults=fault_count,stats_send_faults=stats_faults,source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ['server/scoreboard.py','server/team.py','server/combat_scores.py','server/profile_stats.py','shared/packet.pyx','server/connection.py']},fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),method_substitutions=False,scope='Actual unchanged scoreboard helpers, actual RoundCombatStats/Team/ProfileStats and seeded Python random stream/native codecs. Controlled roster identity, counters, mode phase, peers/known-id sets, terminal sends and result-bridge IO; includes pre-send profile-baseline ordering. Profile counter engine and external persistence/result bridge remain separate required Java ports; no full profile-engine equivalence claimed.'),indent=2)+'\n');print('Scoreboard',len(rows),'rows; faults',fault_count,'stats faults',stats_faults,'bytes',p.stat().st_size)
