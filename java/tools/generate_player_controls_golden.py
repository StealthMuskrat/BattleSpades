"""Intact team/equipment/palette handlers with original Player/native life and Team methods.
Only mode authorization, clocks, IO and deployable cleanup boundaries are controlled.
No team/class/color handler, life method or normalization policy is substituted.
"""
import asyncio,hashlib,json,logging,pathlib,time
from types import SimpleNamespace as N,MethodType
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from aoslib import vxl,world
from server.player import Player
from server.team import Team
from server.connection import Connection
from server.game_rules import GameRules
from server.class_selection import normalize_server_selection
from server.handlers import team,equipment
from server.roster import build_create_player
from shared import packet
logging.disable(logging.CRITICAL)
clock=[100.,1000.];original=time.monotonic,time.time;time.monotonic=lambda:clock[0];time.time=lambda:clock[1]
rows=[]
async def scenario(case,domain,actions,rule_inputs=None,auto=False,assign=False):
 clock[:]=[100.,1000.];wire=[];direct=[];log=[];allow=[True];locked=[False];selection_allow=[True]
 rules=GameRules.server_defaults()
 if rule_inputs:rules.apply(rule_inputs)
 config=N(game_rules=rules,game_mode='tdm',default_mode='tdm',respawn_time=1.5,auto_balance=auto,balance_threshold=2,team_change_cooldown=5.,ugc_runtime=case==23,ugc_prefabs=['Construct'])
 native_map=vxl.VXL(-1,b'',0,2);native_world=world.World(native_map)
 mode=N(allows_team_change=lambda p,t:allow[0],respawn_time_for=lambda p:1.5)
 def configure(probe):probe.team1_locked=probe.team2_locked=locked[0]
 mode.configure_state_data=configure
 if assign:mode.prepare_join_team=lambda t:t
 # A custom class hook is a controlled mode proposal, not a replaced handler policy.
 mode.allows_class_selection=lambda p,s:selection_allow[0]
 server=N(config=config,mode=mode,players={},connections={},teams={i:Team(i,'Team',(18,52,86)if i==2 else(50,60,70))for i in (2,3)},world_manager=N(world=native_world),round_lifecycle=N(remove_owned_deployables=lambda p:log.append(['retire',p.id,p.team])))
 def broadcast(data,**kw):wire.append([bytes(data).hex(),None if kw.get('exclude')is None else kw['exclude'].id])
 server.broadcast=broadcast
 def event(name,*args):
  if name=='on_player_team_change':log.append(['changed',args[0].id,args[1],args[2]])
 server.queue_mode_event=event
 for i in range(5):
  conn=N(server=server,in_game=True,known_player_lives={},known_player_deaths={},known_corpse_cleanups={})
  p=Player(i,'P'+str(i),2 if i!=1 else 3,6,conn);conn.player=p;conn.send=lambda data,*args,**kw:direct.append(bytes(data).hex());conn._balance_join_team=MethodType(Connection._balance_join_team,conn)
  p.apply_class_selection(normalize_server_selection(config,13 if case==23 else 0));p.spawn(36,36,97);server.players[i]=p;server.connections[i]=conn;server.teams[p.team].add_player(p)
 p=server.players[0];wire.clear();direct.clear()
 steps=[]
 for action in actions:
  op=action[0];result=None
  if op=='team':result=team.change_team(server,p,action[1],explain=action[2],force=action[3])
  elif op=='wireteam':await team.handle_change_team(server,p,N(team=action[1]))
  elif op=='join':await team.handle_spectator_rejoin(server,p,N(team=action[1]))
  elif op=='class':await equipment.handle_change_class(server,p,N(class_id=action[1]))
  elif op=='loadout':await equipment.handle_set_class_loadout(server,p,N(class_id=action[1],loadout=action[2],prefabs=action[3],ugc_tools=[],instant=action[4]))
  elif op=='palette':await team.handle_set_color(server,p,N(value=action[1]))
  elif op=='advance':clock[0]+=action[1];clock[1]+=action[1];await asyncio.sleep(0);await asyncio.sleep(0)
  elif op=='spawn':p.apply_pending_selection();p.spawn(36,36,97);p.death_time=0.
  elif op=='die':p.die(kill_type=0)
  elif op=='hit':p._last_combat_damage_at=clock[0];p._last_damage_source_id=1
  elif op=='allow':allow[0]=action[1];locked[0]=action[2]
  elif op=='selection_allow':selection_allow[0]=action[1]
  elif op=='depart':server.players.pop(p.id)
  else:raise AssertionError(op)
  pending=p.pending_selection
  selection=lambda s:None if s is None else [s.class_id,list(s.loadout),list(s.prefabs),list(s.ugc_tools)]
  steps.append(dict(action=action,result=result,team=p.team,alive=p.alive,spawned=p.spawned,death_time=p.death_time,color=p.block_color,selection=[p.class_id,list(p.loadout),list(p.prefabs),list(p.ugc_tools)],pending=selection(pending),kills=[server.players[1].kills,p.deaths],log=list(log),wire=list(wire),direct=list(direct),teams={str(k):[x.id for x in t.players]for k,t in server.teams.items()}))
 rows.append(dict(case=case,domain=domain,actions=steps,rules=rule_inputs or{},auto=auto,assign=assign,ugc=case==23))
async def main():
 cases=[('team', [['team',3,False,False],['team',2,True,False],['advance',5.001],['team',2,False,False]]),('team',[['team',0,False,False],['join',3],['spawn'],['team',2,False,False]]),('team',[['team',2,True,False]]),('team',[['allow',False,False],['team',3,False,False],['allow',False,True],['team',3,True,True]]),('team',[['team',0,False,True]]),('team',[['team',3,False,False],['spawn'],['team',2,False,False],['team',2,False,True]]),('team',[['hit'],['team',3,False,False]]),('team',[['wireteam',255]]),('class',[['class',1],['class',2],['spawn'],['class',3],['advance',3.001],['class',4]]),('class',[['loadout',1,[5,6,23],[],1],['class',1],['spawn']]),('class',[['class',1],['loadout',1,[],[],0],['spawn']]),('class',[['class',0],['loadout',0,[],[],1]]),('class',[['selection_allow',False],['class',1],['loadout',2,[],[],1]]),('class',[['class',255],['loadout',255,[],[],0]]),('class',[['hit'],['class',1]]),('class',[['die'],['loadout',1,[],[],1],['class',1],['spawn']]),('class',[['loadout',12,[],['prefab_superdome','bad','prefab_platform'],1],['class',12],['spawn']]),('palette',[['palette',0x123456],['advance',.04],['palette',0xabcdef],['palette',0x321456],['advance',.07],['advance',.11],['palette',0x102030]]),('palette',[['palette',0x123456],['advance',.04],['palette',0xabcdef],['die'],['advance',.07]]),('palette',[['palette',0x123456],['advance',.04],['palette',0xabcdef],['depart'],['advance',.07]])]
 for i,(domain,actions)in enumerate(cases):
  await scenario(i,domain,actions,{'RULE_ENABLE_SPECTATORS':False}if i==4 else None,auto=i==5)
 await scenario(20,'team',[['team',2,False,False],['team',3,False,False],['spawn'],['team',2,False,False]],auto=True,assign=True)
 await scenario(21,'palette',[['palette',0xabcdef]],{'RULE_ENABLE_COLOUR_PICKER':False})
 await scenario(22,'class',[['class',1],['loadout',1,[],[],1]],{'RULE_ENABLE_CLASS_MARKSMAN':False})
 await scenario(23,'class',[['loadout',13,[],['Construct'],1],['class',1],['loadout',13,[],[],0]])
try:asyncio.run(main())
finally:time.monotonic,time.time=original
out=ROOT/'java/src/test/resources/golden/player-controls.json';out.write_text(json.dumps(dict(cases=rows),separators=(',',':'))+'\n')
files=['server/handlers/team.py','server/handlers/equipment.py','server/player.py','server/team.py','server/connection.py','server/class_selection.py']
(out.parent/'player-controls-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',cases=len(rows),fixture_sha256=hashlib.sha256(out.read_bytes()).hexdigest(),policy_method_substitutions=False,source_files_sha256={f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest()for f in files},scope=__doc__.strip()),sort_keys=True,indent=2)+'\n');print('Player controls source oracle',len(rows),'cases')
