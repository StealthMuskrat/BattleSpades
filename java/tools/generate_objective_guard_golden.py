"""Unchanged objective guard functions over the original native VXL and WorldManager."""
import gzip,hashlib,io,json,math,pathlib,struct
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from aoslib import vxl
from server.world_manager import WorldManager
from server.game_rules import GameRules
from modes import objective_guard as guard
from server import escape_watch
bits=lambda v:struct.pack('>d',float(v)).hex();vec=lambda v:','.join(map(bits,v));rows=[];reasons={}
class Actor:
 def __init__(self):self.id=0;self.position=(36.5,36.5,97.75);self.orientation=(1.,0.,0.)
for trace in range(16):
 raw=(OUT/'world/empty.vxl').read_bytes();wm=WorldManager(NS(game_rules=GameRules.server_defaults(),game_mode='tdm'));wm.map=vxl.VXL(None,raw,len(raw));server=NS(world_manager=wm,config=NS(objective_pickup_requires_los=trace%8!=7));p=Actor()
 def edit(x,y,z,solid):wm.set_block(x,y,z,solid,0x808080);rows.append(('edit',trace,x,y,z,int(solid)))
 kind=trace%8
 if kind in (0,1,2,6,7):
  for x in range(33,43):
   for y in range(33,43):edit(x,y,100,True)
 if kind==1:edit(37,36,99,True)
 if kind in (2,7):
  extent=1 if kind==2 else 4
  for x in range(37-extent,38+extent):
   for y in range(36-extent,37+extent):
    for z in range(96,101):
     if z in (96,100) or x in (37-extent,37+extent) or y in (36-extent,36+extent):edit(x,y,z,True)
 if kind==3:edit(37,36,110,True)
 if kind==4:edit(40,36,100,True)
 if kind==6:edit(37,36,0,True)
 for case in range(96):
  if case%12==0:edit(37+case%3,36,99 if case%24==0 else 100,case%36==0)
  player_space=bool(case%2);x,y=[(37.5,36.5),(37.,36.),(37.25,36.875),(-1.,36.),(511.9,511.9)][(case//4+trace)%5];surface=[100.,100.+1e-7,100.-1e-7,98.,101.,239.,-1.][(case//3+trace)%7];position=(x,y,surface-2.25 if player_space else surface)
  reason=guard.ground_objective_trapped(wm,position,player_space=player_space);reasons[reason or 'clear']=reasons.get(reason or 'clear',0)+1
  settled=guard.resettle_surface(wm,position,player_space=player_space);visible=guard.pickup_line_of_sight(server,p,position,player_space=player_space,lift=.5)
  rows.append(('query',trace,case,int(player_space),vec(position),reason or '-',vec(settled),int(visible)))
 # Identity-sensitive AFK book and real unchanged escape-watch lookup; no watch policy replaced.
 server._escape_watch=escape_watch.EscapeWatchState(0);timer=guard.TrapTimer();now=100.
 for step in range(128):
  if step%29==0:p=Actor()
  position=[(36.,36.,97.),(36.0001,36.,97.),(36.005,36.,97.),(36.006,36.,97.),(2.675,-2.675,97.)][(step//13+trace)%5];p.position=position;p.orientation=[(1.,0.,0.),(0.,1.,0.),(-1.,0.,0.)][step//41%3];now+=[0.,.25,1.,10.,-1.][(step+trace)%5]
  afk=[60.,0.,-1.,1.][trace%4];server.config.objective_afk_seconds=afk;server._escape_watch.tracks[0]=escape_watch._Track(p,flagged='embedded' if step%11==0 else None)
  idle=guard.idle_seconds(server,p,now);eligible=guard.presence_eligible(server,p,now);key=step%3;trapped=step%7!=0;seconds=[0.,.5,5.,-1.][step%4];due=timer.due(key,trapped,now,seconds)
  if step%31==0:timer.clear()
  rows.append(('presence',trace,step,vec(position),vec(p.orientation),bits(now),bits(afk),int(step%11==0),bits(idle),int(eligible),key,int(trapped),bits(seconds),int(due),int(step%29==0),int(step%31==0)))
assert reasons.get('sealed',0)>0 and reasons.get('buried',0)>0 and reasons.get('floating',0)>0 and reasons.get('clear',0)>0,reasons
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
p=OUT/'objective-guard.tsv.gz';p.write_bytes(buffer.getvalue());(OUT/'objective-guard-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',terrain_queries=1536,presence_timer_operations=2048,trapped_reasons=reasons,source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ['modes/objective_guard.py','server/combat_runtime.py','server/world_manager.py','aoslib/vxl.pyx','server/escape_watch.py']},fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),method_substitutions=False,scope='Actual unchanged guard, LOS traversal, native WorldManager/VXL get_z and escape-watch lookup; controlled voxel edits, identity/coordinate/config/clock inputs. Buried/sealed/floating/open cases, native-start-column resettling, both coordinate spaces, rounded AFK state, identity reuse and TrapTimer. Full escape detection and concrete-mode scheduling remain separate.'),indent=2)+'\n');print('Objective guard',len(rows),'rows;',reasons,'bytes',p.stat().st_size)
