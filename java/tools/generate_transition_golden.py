"""Intact match transition/rollover/recovery helpers with controlled terminal services.

Eight map-screen cases execute real worker WorldManager loading; Java also tests
confined paths and actual VXL/metadata codecs.
This oracle calls _rollover directly with an isolated candidate as its normal input;
it does not replace policy methods. Mode lifecycle, peers, clocks, external services
and terminal overlay/audio/HUD IO are controlled.
"""
import asyncio,collections,hashlib,json,logging,pathlib,tempfile
from types import SimpleNamespace as N
from unittest.mock import patch
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from server.match import MatchTransitionService as Service
from server.team import Team
from server.config import ServerConfig
from server.audio import SND_BUILD,al_error_flush_bytes
from server.builders.initial_info import _STOCK_LEVEL_SCREENSHOT_NAMES
logging.disable(logging.CRITICAL);rows=[];sleep=asyncio.sleep
async def scenario(case):
 log=[];fault=case%8;all_peers=[]
 class Mode:
  def __init__(self,name):self.name=name
  async def cancel_end_sequence(self):log.append(self.name+':cancel')
  def begin_retirement(self,*,end_round):log.append(self.name+':retire:'+str(int(end_round)))
  async def on_player_leave(self,*args):
   log.append(self.name+':leave:'+str(args[0]))
   if fault==2 and (args[0]==99 or case>=16 and getattr(args[0],'id',None)==1):raise RuntimeError('leave failure')
  async def deactivate(self):
   log.append(self.name+':deactivate')
   if fault==3 and self.name=='old':raise RuntimeError('deactivate failure')
   if case==13 and self.name=='new':raise RuntimeError('aborted deactivate failure')
  async def on_mode_start(self):
   log.append(self.name+':start')
   if fault==5 and self.name=='new':raise RuntimeError('new start failure')
   if case==13 and self.name=='old':raise RuntimeError('restored start failure')
 class Peer:
  def __init__(self,i):self.i=i;self.in_game=i==0;self.map_sent=i<2;self.state_sent=i<2;self.player=None;self.disconnects=[]
  def arm_scene_transition(self):log.append(f'arm:{self.i}')
  def send(self,data):log.append(f'peer:{self.i}:'+bytes(data).hex())
  def disconnect(self,reason):self.disconnects.append(reason);log.append(f'disconnect:{self.i}:{reason}')
  async def reload_scene(self):
   log.append(f'reload:{self.i}');await sleep(0)
   if fault==6 and self.i==0:raise RuntimeError('reload failure')
   return not(fault==7 and self.i==1)
 class Actor:
  def __init__(self,i):self.id=i;self.team=2 if i==0 else 3
  def __str__(self):return str(self.id)
 for i in range(3 if case<8 or case>=16 else 0):
  peer=Peer(i)
  if case>=16:peer.player=Actor(i)
  all_peers.append(peer)
 old=Mode('old');world=N(name='old');candidate=N(name='new');config=ServerConfig();config.default_map='CustomMap' if case>=28 else 'London';config.default_mode='tdm';config.transition_grace_seconds=1 if case%2 else 0
 world.map_name=config.default_map
 s=N(config=config,mode=old,world_manager=world,fog_color_override='fog',connections={i:p for i,p in enumerate(all_peers)},players={},teams={},_mode_events=collections.deque([('on_player_leave',(99,)),('other',())]),_pending_ingame_packets=collections.deque(['old']))
 s.players={p.player.id:p.player for p in all_peers if p.player is not None};s.round_lifecycle=N(forget_player=lambda player:log.append('forget:'+str(player.id)))
 s.broadcast=lambda data:log.append('broadcast:'+bytes(data).hex());s.host=N(flush=lambda:log.append('flush'))
 s.vote_manager=N(cancel=lambda:log.append('vote-cancel'),consume_next_map=lambda:log.append('vote-consume'),clear_match_kicks=lambda:log.append('vote-kicks'),note_map_played=lambda name:log.append('played:'+name))
 async def plugin(name,*args):log.append('plugin:'+name+':'+str(args[0]))
 s.plugin_manager=N(call_event=plugin)
 async def prepare_bots():
  log.append('bots-prepare')
  if fault==1:raise RuntimeError('prepare failure')
 async def rebind():
  log.append('bots-rebind')
  if fault==4:raise RuntimeError('rebind failure')
 s.bots=N(prepare_for_game_transition=prepare_bots,rebind_after_match_transition=rebind)
 def reset():
  log.append('runtime-reset')
  if fault==0 and case>=8:raise RuntimeError('reset failure')
 s.reset_round_runtime=reset;s.terrain_repair=N(reset=lambda:log.append('repair-reset'));s._bind_world_mutation_journal=lambda:log.append('bind-journal')
 class Journal(list):
  def clear(self):log.append('journal-clear');super().clear()
 s._map_mutation_journal=Journal([1]);s._map_cell_journal=Journal([1])
 for team in [2,3]:
  s.teams[team]=Team(team,str(team),(0,0,0));s.teams[team].score=10
 for player in s.players.values():s.teams[player.team].add_player(player)
 async def timer(seconds):log.append('sleep:'+str(int(seconds)));await sleep(0)
 svc=Service(s)
 with patch('modes.get_mode_class',lambda code:lambda server:Mode('new')),patch('server.announcements.broadcast_overlay',lambda server,msg:log.append('notice:'+msg)),patch('server.hud_packets.reset_state',lambda server:log.append('hud-reset')),patch('asyncio.sleep',timer):
  if case<24:result=await svc._rollover(map_name='Other',mode_name='tdm',candidate_world=candidate)
  else:
   with tempfile.TemporaryDirectory() as directory:
    config.maps_path=directory;(pathlib.Path(directory)/'Other.vxl').write_bytes(bytes([0,239,239,0,1,2,3,4]))
    result=await svc.change_map_after_end_screen('Other',end_screen_seconds=[-2,0,7,130][case%4],headline_message_id=None if case%3==0 else 99)
 rows.append(dict(domain='map_screen' if case>=24 else 'rollover',case=case,log=log,ok=result.ok,reconnect=result.reconnect_required,message=result.message,map=s.config.default_map,mode='old' if s.mode is old else 'new',world='old' if s.world_manager is world else 'new',fog=s.fog_color_override,busy=svc.in_progress,pending=len(s._mode_events),packets=len(s._pending_ingame_packets),players=sorted(s.players),scores=[t.score for t in s.teams.values()]))
async def main():
 for case in range(32):await scenario(case)
asyncio.run(main())
for name in ['','  London.VXL  ','../London','a/b','.', '..','.vxl','a.vxl.vxl','Straße.vxl','path\\name','a.','London','\u2003London\u2003']:
 rows.append(dict(domain='normalize',input=name,output=Service._normalize_map_name(name)))
out=ROOT/'java/src/test/resources/golden/transition.json';out.write_text(json.dumps(dict(cases=rows),separators=(',',':'))+'\n')
catalog=ROOT/'java/src/main/resources/net/battlespades/catalog/transitions.toml';catalog.write_text('flushSound = '+str(SND_BUILD)+'\nscreenshots = '+json.dumps(sorted(_STOCK_LEVEL_SCREENSHOT_NAMES))+'\n')
files=['server/match/__init__.py','server/audio.py','server/builders/initial_info.py','server/connection.py','server/team.py','server/world_manager.py','server/config.py']
(out.parent/'transition-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',observations=dict(collections.Counter(r['domain'] for r in rows)),fixture_sha256=hashlib.sha256(out.read_bytes()).hexdigest(),catalog_sha256=hashlib.sha256(catalog.read_bytes()).hexdigest(),flush_bytes=al_error_flush_bytes().hex(),source_files_sha256={f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest() for f in files},policy_method_substitutions=False,scope=__doc__.strip()),indent=2)+'\n')
print('Transition source oracle',len(rows),'cases')
