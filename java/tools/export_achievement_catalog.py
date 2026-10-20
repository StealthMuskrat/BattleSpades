"""Export original achievement/HUD data and intact-engine tier observations."""
import ast,json,pathlib,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
from shared.achievement_table import ACHIEVEMENTS
from shared import constants as C
from server.achievements import AchievementEngine,AchievementStore
from types import SimpleNamespace
source=ast.parse((ROOT/'server/achievements.py').read_text())
names=sorted({n.attr for n in ast.walk(source) if isinstance(n,ast.Attribute) and isinstance(n.value,ast.Name) and n.value.id=='C'})
hud=ast.parse((ROOT/'server/hud_packets.py').read_text())
icons=next(sorted(ast.literal_eval(n.value.args[0])) for n in hud.body if isinstance(n,ast.AnnAssign) and getattr(n.target,'id','')=='BILLBOARD_ICONS')
text='# Source data: 546c548780c400f21d73ca6713f86194ceb3c844\nicons = '+json.dumps(icons)+'\n\n[constants]\n'
for name in names:text+=name+' = '+json.dumps(getattr(C,name))+'\n'
for row in ACHIEVEMENTS:
 text+='\n[[achievement]]\n'+''.join(k+' = '+json.dumps(v,ensure_ascii=False)+'\n' for k,v in row._asdict().items() if v is not None)
(ROOT/'java/src/main/resources/net/battlespades/catalog/achievements.toml').write_text(text)
rows=[]
for stat in sorted({a.stat for a in ACHIEVEMENTS if a.stat}):
 tiers=sorted(a.threshold for a in ACHIEVEMENTS if a.stat==stat);server=SimpleNamespace(config=SimpleNamespace(achievements=SimpleNamespace(enabled=True,count_bot_kills=True),default_mode='tdm'),mode=SimpleNamespace(ended=False),players={},broadcast=lambda *a,**k:None)
 engine=AchievementEngine(server,store=AchievementStore(':memory:'),clock=lambda:1000.,monotonic=lambda:100.)
 assert engine.start();p=SimpleNamespace(id=0,name='Fixture',is_bot=False,connection=None);server.players[0]=p;last=0
 for target in sorted(set([0]+[max(0,t-1) for t in tiers]+tiers+[tiers[-1]+1])):
  unlocked=engine.add(p,stat,target-last);rows.append(dict(stat=stat,amount=target-last,value=target,new=unlocked,unlocked=sorted(engine.unlocked('name:fixture'))));last=target
 engine.close()
import tempfile,hashlib,importlib.util
spec=importlib.util.spec_from_file_location('legacy_achievement_export',ROOT/'java/tools/export_legacy_achievements.py');exporter=importlib.util.module_from_spec(spec);spec.loader.exec_module(exporter)
with tempfile.TemporaryDirectory() as directory:
 db=pathlib.Path(directory)/'old.sqlite';out=pathlib.Path(directory)/'portable.json';store=AchievementStore(db);store.open();store.add_counters([('aosplay:42','sniper_kill_count',25),('aosplay:42','spade_kill_count',3)]);store.unlock('aosplay:42','sniper_kill','Naïve 🎮',1000);store.take_unreported('aosplay:42',1001);store.unlock('aosplay:42','spade_kill','Naïve 🎮',1002);store.close();before=hashlib.sha256(db.read_bytes()).hexdigest();exporter.export(db,out);assert hashlib.sha256(db.read_bytes()).hexdigest()==before;legacy=json.loads(out.read_text())
# Real original engine traces for the hosted generic death and miss hooks.
from tests.achievement_helpers import make_server,make_player,kill,Clock
traces=[]
for name,tool,kind,count,bot,count_bots in [('spade',int(C.SPADE_TOOL),int(C.MELEE_KILL),3,False,True),('sniper',int(C.SNIPER_TOOL),int(C.HEADSHOT_KILL),25,False,True),('classic',int(C.RIFLE_TOOL),int(C.HEADSHOT_KILL),50,False,True),('miss',int(C.SNIPER_TOOL),int(C.WEAPON_KILL),6,False,True),('semi',int(C.SNIPER2_TOOL),int(C.WEAPON_KILL),3,False,True),('bot_included',int(C.SPADE_TOOL),int(C.MELEE_KILL),3,True,True),('bot_excluded',int(C.SPADE_TOOL),int(C.MELEE_KILL),3,True,False),('intermission',int(C.SPADE_TOOL),int(C.MELEE_KILL),3,False,True)]:
 clock=Clock();server=make_server(clock=clock,count_bot_kills=count_bots);killer=make_player(server,0,name='P0',tool=tool);victim=make_player(server,1,team=3,bot=bot);steps=[]
 if name=='intermission':server.achievements.zombie_round_finished(());steps.append({'round':False})
 for i in range(count):
  if name=='miss' and i==3:server.achievements.shot_resolved(killer,tool,False);steps.append({'miss':True})
  clock.now+=2;kill(server,killer,victim,kind);steps.append({'kill':kind,'advance':2})
 server.achievements.flush();traces.append(dict(name=name,tool=tool,bot=bot,count_bots=count_bots,steps=steps,progress=server.achievements.progress('name:p0'),unlocked=sorted(server.achievements.unlocked('name:p0'))));server.achievements.close()
from server import hud_packets
from shared.packet import MinimapBillboard
from shared.bytes import ByteReader
wire=[]
for i,icon in enumerate(icons):
 sent=[];connection=SimpleNamespace(in_game=True,player=SimpleNamespace(id=0),known_entity_ids=set(),send=lambda packet,**kwargs:sent.append(bytes(packet)))
 server=SimpleNamespace(connections={0:connection});ident=[-32768,-1,0,1,0x7000,32767][i%6];position=[30.5078125,-5.25,700.];color=(i*1234567)&0xffffff
 assert hud_packets.add_billboard(server,ident,i,icon,position,color=color)
 decoded=MinimapBillboard(ByteReader(sent[-1][1:]));assert decoded.entity_id==ident and decoded.key==i and decoded.icon_name==icon;wire.append(dict(id=ident,key=i,icon=icon,position=position,color=color,hex=sent[-1].hex(),decoded=[decoded.x,decoded.y,decoded.z]))
(ROOT/'java/src/test/resources/golden/achievement-tiers.json').write_text(json.dumps(dict(tiers=rows,legacy=legacy,combat=traces,wire=wire),indent=2)+'\n')
print(len(ACHIEVEMENTS),'achievements;',len(icons),'HUD icons;',len(rows),'intact-engine tier observations')
# Connected objective/sub-round rule traces, evaluated by the intact original owner.
objective_cases=[
 ('zombie_awards',False,[['zombie_start',[0]],*([['kill',0,1,0]]*3),['zombie_end',[2]],['kill',0,1,0]]),
 ('bot_competition',True,[['zombie_start',[0]],['kill',0,1,0],*([['kill',2,0,0]]*2),['zombie_end',[]]]),
 ('vip_awards',False,[['vip_start',[0,1]],*([['kill',0,2,0]]*2),['vip_end',[0,1],[2,3]]]),
 ('vip_round_reset',False,[['vip_start',[0,1]],['kill',0,2,0],['vip_start',[0,1]],['vip_end',[0,1],[2,3]]]),
 ('diamonds',False,[['carry','diamond_picked_up',0,1,10,False,5,1],['carry','diamond_cashed_in',0,None,10,False,None,2],['carry','diamond_picked_up',0,None,11,True,None,3],['carry','diamond_cashed_in',0,None,11,True,None,2],['carry','diamond_cashed_in',0,None,12,True,None,2],['carry','diamond_cashed_in',0,None,13,True,None,2]]),
 ('diamond_retake',False,[['carry','diamond_picked_up',0,1,10,False,5.01,1],['carry','diamond_picked_up',2,None,10,True,None,2],['carry','diamond_cashed_in',2,None,10,False,None,3]]),
 ('bomb_attack',False,[['carry','bomb_picked_up',0,None,10,True,None,2],['carry','bomb_dropped',0,None,10,True,None,2],['carry','bomb_detonated',0,None,10,True,None,2],['match_end',2]]),
 ('bomb_intercept',False,[['carry','bomb_picked_up',0,None,10,True,None,2],['carry','bomb_carrier_killed',1,0,10,False,None,3],['carry','bomb_detonated',None,None,10,False,None,1]]),
 ('bomb_recovered',False,[['carry','bomb_carrier_killed',1,0,10,False,None,3],['carry','bomb_picked_up',0,None,10,False,None,2],['carry','bomb_detonated',None,None,10,False,None,1]]),
 ('intel_return',False,[['intel_kill',0,1,2,0],['intel_return',2],['intel_return',2],['intel_kill',0,1,2,0],['intel_pickup',2],['intel_return',2]]),
 ('intel_transition',False,[['intel_kill',0,1,2,int(C.TEAM_CHANGE_KILL)],['intel_return',2]]),
 ('hill_awards',False,[['hill_score',0],['hill_contest',[0],True],['hill_contest',[1],False],['hill_kill',0,1,True,True],['hill_expired',[0,1]],['move',1,50,50,97],['tick',1]]),
 ('hill_shared',False,[['hill_score',0],['hill_score',1],['hill_expired',[0,1]],['dead',1],['tick',.5],['tick',.5]]),
 ('match_reset',False,[['carry','diamond_picked_up',0,1,10,False,2,1],['hill_score',0],['intel_kill',0,1,2,0],['match_start'],['carry','diamond_cashed_in',0,None,10,False,None,2],['intel_return',2],['hill_expired',[]]]),
]
objectives=[]
for name,bot,steps in objective_cases:
 server=make_server();players=[make_player(server,i,team=2 if i==0 else 3,bot=bot and i==2,x=10.,y=10.,z=97.) for i in range(3)];engine=server.achievements
 zone=SimpleNamespace(index=0,contains=lambda p:5<=p[0]<=15 and 5<=p[1]<=15 and 90<=p[2]<=100)
 for step in steps:
  op,*a=step
  if op=='zombie_start':engine.zombie_round_started([players[i] for i in a[0]],2,3)
  elif op=='zombie_end':engine.zombie_round_finished([players[i] for i in a[0]])
  elif op=='vip_start':engine.vip_round_started({2:players[a[0][0]],3:players[a[0][1]]})
  elif op=='vip_end':engine.vip_round_finished(SimpleNamespace(vips={2:players[a[0][0]],3:players[a[0][1]]},vip_alive={i:True for i in a[1]}))
  elif op=='kill':kill(server,players[a[0]],players[a[1]],a[2])
  elif op=='match_start':engine.match_started()
  elif op=='match_end':engine.match_ended(a[0])
  elif op=='carry':
   event,p,other,serial,flag,seconds,team=a;p=None if p is None else players[p];other=None if other is None else players[other]
   if event=='diamond_picked_up':engine.diamond_picked_up(p,serial,fresh_seconds=seconds,found_by=other,last_team=team)
   elif event=='diamond_cashed_in':engine.diamond_cashed_in(p,serial,flag)
   elif event=='bomb_picked_up':engine.bomb_picked_up(p,serial,from_spawn=flag,attacker=p.team==2)
   elif event=='bomb_dropped':engine.bomb_dropped(p,serial,in_base=flag)
   elif event=='bomb_carrier_killed':engine.bomb_carrier_killed(p,other,serial)
   elif event=='bomb_detonated':engine.bomb_detonated(serial,inside=flag,planter=p)
  elif op=='intel_kill':engine.intel_carrier_killed(players[a[0]],players[a[1]],a[2],a[3])
  elif op=='intel_return':engine.intel_returned(a[0])
  elif op=='intel_pickup':engine.intel_picked_up(a[0])
  elif op=='hill_score':engine.hill_scored(0,players[a[0]])
  elif op=='hill_contest':engine.hill_contest_won([players[i] for i in a[0]],a[1])
  elif op=='hill_kill':engine.hill_kill(players[a[0]],players[a[1]],killer_in_hill=a[2],victim_in_hill=a[3])
  elif op=='hill_expired':engine.hill_expired(zone,{t:[i for i in a[0] if players[i].team==t] for t in [2,3]})
  elif op=='move':players[a[0]].x,players[a[0]].y,players[a[0]].z=a[1:]
  elif op=='dead':players[a[0]].alive=False
  elif op=='tick':engine.tick(a[0])
  else:raise AssertionError(op)
 engine.flush();objectives.append(dict(name=name,bot=bot,steps=steps,players=[dict(progress=engine.progress(engine.identity_for(p)),unlocked=sorted(engine.unlocked(engine.identity_for(p)))) if not p.is_bot else dict(progress={},unlocked=[]) for p in players]));engine.close()
(ROOT/'java/src/test/resources/golden/achievement-objectives.json').write_text(json.dumps(objectives,indent=2)+'\n')
print(len(objectives),'intact-engine objective/sub-round traces')
