"""Intact original-engine blast, terrain, crate, demolition and authored-volume traces."""
import json,pathlib
from types import SimpleNamespace as NS
ROOT=pathlib.Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
from shared import constants as C
from server.map_metadata import AchievementRegion
from server.game_constants import WEAPON_PROFILES
from tests.achievement_helpers import make_server,make_player,kill
class World:
 def __init__(self,regions,solid,official):self.solid=set(solid);self.map_metadata=NS(official_map=official,achievement_regions=regions)
 def get_solid(self,x,y,z):return (int(x),int(y),int(z)) in self.solid
ci=lambda name:int(getattr(C,name))
D=ci('ACH_BLOCK_DESTROY_REGION');K=ci('ACH_KILL_REGION');GRENADE=ci('GRENADE_KILL');MINE=ci('LANDMINE_KILL');DYNAMITE=ci('DYNAMITE_KILL');TURRET=ci('ROCKET_TURRET_KILL');ROCKET=ci('ROCKET_KILL');DRILL=ci('DRILL_KILL')
R=['map_isleofdoom_destroy',D,[40.,41.,40.,41.,90.,91.],None,1]
R2=['map_isleofdoom_destroy',D,[43.,44.,40.,41.,90.,91.],None,1]
A=[[40,40,90],[41,41,91]];B=[[43,40,90],[44,41,91]]
def block(player,removed=(),collapsed=(),chunks=(),tool=None):return ['blocks',player,list(removed),list(collapsed),list(chunks),tool]
def blast(kind,victims,origin=None):return [['begin',0,kind,origin],*[['kill',0,i,kind] for i in victims],['end']]
def turret(index,blocks=0,hurt=False,kill_target=False):return [['projectile','rocket_turret_rocket',0,None,TURRET,index,1],['begin',0,TURRET,[10,10,97]],*([['damage',1,0,TURRET]] if hurt or kill_target else []),*([['kill',0,1,TURRET]] if kill_target else []),*([block(0,[[x,0,1] for x in range(blocks)])] if blocks else []),['end']]
cases=[]
def case(name,steps,regions=(),solid=(),official=False,mode='tdm',bots=(),count_bots=True):cases.append(dict(name=name,steps=steps,regions=list(regions),solid=list(solid),official=official,mode=mode,bots=list(bots),count_bots=count_bots))
case('one_explosion',blast(GRENADE,[1,2])+blast(GRENADE,[1,2])+blast(ROCKET,[1,2,3]))
case('nested_explosions',[['begin',0,GRENADE,None],['kill',0,1,GRENADE],['begin',0,ROCKET,None],['kill',0,2,ROCKET],['end'],['kill',0,2,GRENADE],['kill',0,3,GRENADE],['end']])
case('buried_mines',sum((blast(MINE,[1],[40,40,90]) for _ in range(10)),[]),solid=A)
case('unburied_mine',blast(MINE,[1],[40,40,90]))
case('below_feet',[['begin',0,DYNAMITE,[10,10,99.74]],['damage',1,0,DYNAMITE],['kill',0,1,DYNAMITE],['end'],['begin',0,DYNAMITE,[10,10,99.75]],['damage',1,0,DYNAMITE],['kill',0,1,DYNAMITE],['end'],['begin',0,DYNAMITE,[10,10,100]],['damage',1,0,DYNAMITE],['end'],['kill',0,1,ci('FALL_KILL')]])
case('turret_identity',sum((turret(0,kill_target=True) for _ in range(9)),[])+turret(1,kill_target=True)+turret(0,kill_target=True))
case('turret_evasion',turret(0,60)+turret(0,60,hurt=True)+turret(0,39)+turret(0,1))
case('turret_reset',turret(0,99)+[['match_start']]+turret(0,1))
case('structure_sizes',[block(0,[[0,0,1]],chunks=[49],tool=ci('MINIGUN_TOOL')),block(0,[[0,0,1]],chunks=[50],tool=ci('MINIGUN_TOOL')),['begin',0,GRENADE,None],block(0,[[0,0,1]],chunks=[99,100,101]),['end']])
case('wrong_structure_cause',[['begin',0,ROCKET,None],block(0,[[0,0,1]],chunks=[100],tool=ci('MINIGUN_TOOL')),['end']])
case('zombie_terrain',[['zombie_start'],block(0,[[0,0,1]],[[0,0,2],[0,0,3]])])
rifle=ci('RIFLE_TOOL');smg=ci('SMG_TOOL');capacity=int(WEAPON_PROFILES[rifle].reserve_ammo)
case('crate_health',[['health',0,1],['crate_health',0],['health',0,49],['crate_health',0]])
case('crate_match_scope',[['health',0,1],['crate_health',0],['match_start'],['health',0,1],['crate_health',0],['zombie_start'],['health',0,49],['crate_health',0]])
case('crate_ammo',[['weapon',0,rifle],['ammo',0,0],['crate_ammo',0,capacity-1],['crate_ammo',0,0],['crate_ammo',0,1]])
case('crate_weapon_scope',[['weapon',0,rifle],['ammo',0,0],['crate_ammo',0,capacity-1],['weapon',0,smg],['crate_ammo',0,1],['match_start'],['weapon',0,rifle],['crate_ammo',0,1]])
case('region_all_volumes',[block(0,A[:1]),block(1,A[1:]),block(0,B),['match_start'],block(0,A+B)],regions=[R,R2],solid=A+B,official=True)
case('region_shared_credit',[block(0,A[:1]),block(1,A[1:]),block(0,B[:1]),block(1,B[1:])],regions=[R,R2],solid=A+B,official=True)
case('region_resync',[block(0,A[:1]),['raw_remove',A[1:]],['tick',1]],regions=[R],solid=A,official=True)
case('region_unofficial',[block(0,A)],regions=[R],solid=A)
case('region_departure',[block(0,A[:1]),['depart',0],block(1,A[1:])],regions=[R],solid=A,official=True)
case('region_already_razed',[block(0,A)],regions=[R,R2],solid=A,official=True)
case('region_melee',[['kill',0,1,ci('WEAPON_KILL')],['kill',0,1,ci('MELEE_KILL')],['kill',0,1,ci('MELEE_KILL')]],regions=[['map_maya_kill',K,[9.,11.,9.,11.,96.,98.],2,2]],official=True)
case('region_rocket',[block(0,A[:1]),['begin',0,ROCKET,None],block(0,A[1:]),['end']],regions=[['map_moon_destroy',D,R[2],None,1]],solid=A,official=True)
case('region_zombie',[['zombie_start'],block(0,A)],regions=[['map_zombieisland_destroy',D,R[2],2,1]],solid=A,official=True)
case('airborne_rockets',[['airborne',0,True],*[['projectile','rocket',0,1,ROCKET,None,None] for _ in range(5)],['airborne',0,False],['projectile','rocket',0,1,ROCKET,None,None]])
case('excluded_bot_contact',[['airborne',0,True],['projectile','rocket',0,1,ROCKET,None,None]],bots=[1],count_bots=False)
base=[[x,y,100] for x in range(20,30) for y in range(10,20)]
case('demolition', [['begin',0,DRILL,None],block(0,base[:99]),['end'],block(0,base[99:]),['repair',0,25],['match_end',2]],mode='dem')
case('demolition_bot_final',[block(0,base[:99]),block(1,base[99:]),['match_end',2]],mode='dem',bots=[1])
output=[]
for data in cases:
 server=make_server(data['mode'],count_bot_kills=data['count_bots']);players=[make_player(server,i,team=2 if i==0 or data['name']=='demolition_bot_final' and i==1 else 3,bot=i in data['bots'],x=10.,y=10.,z=97.,ammo_reserve=0) for i in range(4)];engine=server.achievements
 definitions=[AchievementRegion(api,kind,tuple(bounds),team=team,kills=kills) for api,kind,bounds,team,kills in data['regions']];server.world_manager=World(definitions,map(tuple,data['solid']),official=data['official']);engine.match_started();turrets=[NS(),NS()];scopes=[];snapshots=[]
 if data['mode']=='dem':
  enemy=set(map(tuple,base));server.mode.enemy_objective_damage=lambda p,cells:(3,sum(tuple(c) in enemy for c in cells)) if p.team==2 and any(tuple(c) in enemy for c in cells) else None
 for step in data['steps']:
  op,*a=step
  if op=='begin':scopes.append(engine.blast_begin(players[a[0]],a[1],a[2]))
  elif op=='end':engine.blast_end(scopes.pop())
  elif op=='projectile':
   name,owner,contact,kind,turret_id,target=a;engine.projectile_exploding(NS(spec=NS(name=name,kill_type=kind),contact_player_id=contact,turret=None if turret_id is None else turrets[turret_id],turret_target_id=target),players[owner])
  elif op=='kill':kill(server,players[a[0]],players[a[1]],a[2])
  elif op=='damage':engine.damaged(players[a[0]],players[a[1]],1,a[2])
  elif op=='blocks':
   actor,removed,collapsed,sizes,tool=a;server.world_manager.solid.difference_update(map(tuple,removed+collapsed));engine.blocks_destroyed(players[actor],removed,collapsed,[[None]*n for n in sizes],shot_tool=tool)
  elif op=='match_start':engine.match_started()
  elif op=='match_end':engine.match_ended(a[0])
  elif op=='zombie_start':engine.zombie_round_started([],2,3)
  elif op=='health':players[a[0]].health=a[1]
  elif op=='weapon':players[a[0]].weapon=a[1]
  elif op=='ammo':players[a[0]].ammo_reserve=a[1]
  elif op=='airborne':players[a[0]].airborne=a[1]
  elif op=='crate_health':
   p=players[a[0]];before=engine.crate_baseline(p);p.health=100;engine.crate_collected(p,ci('MOST_HEALTH_CRATES_COLLECTED'),before)
  elif op=='crate_ammo':
   p=players[a[0]];before=engine.crate_baseline(p);p.ammo_reserve+=a[1];engine.crate_collected(p,ci('MOST_AMMO_CRATES_COLLECTED'),before)
  elif op=='raw_remove':server.world_manager.solid.difference_update(map(tuple,a[0]))
  elif op=='tick':engine.tick(a[0])
  elif op=='depart':server.players.pop(a[0])
  elif op=='repair':engine.add(players[a[0]],'demolition_repair_count',a[1])
  else:raise AssertionError(op)
  snapshots.append([dict(progress=engine.progress(engine.identity_for(p)),unlocked=sorted(engine.unlocked(engine.identity_for(p)))) if not p.is_bot else dict(progress={},unlocked=[]) for p in players])
 assert not scopes
 output.append(dict(data,snapshots=snapshots));engine.close()
(ROOT/'java/src/test/resources/golden/achievement-environment.json').write_text(json.dumps(output,indent=2)+'\n');print(len(output),'intact-engine environmental traces;',sum(len(r['steps']) for r in output),'owner boundaries')
