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
