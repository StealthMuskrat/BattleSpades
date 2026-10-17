"""Unchanged original authoritative hit selection and dynamic-target helpers, controlled domain registries."""
import gzip,hashlib,io,json,pathlib,random,struct
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server.combat_runtime import CombatSystem
bits=lambda x:struct.pack('>d',float(x)).hex()
vec=lambda v:','.join(map(bits,v))
rows=[];rng=random.Random(179285)
for case in range(6000):
 origin=(20.,20.,20.);direction=(1.,0.,0.);maximum=rng.choice([0.,2.,10.,100.]);block=None if case%3 else (rng.randrange(20,45),20,20);ff=bool(case%2);attacker=NS(id=0,team=2,alive=True,spawned=True);players={0:attacker};p_rows=[]
 for id in range(1,4):
  at=(rng.randrange(18,45)+rng.choice([0.,.25,.5]),20.+rng.choice([0.,.25,2.]),19.5+rng.choice([0.,.25,2.]));live=bool(rng.randrange(5));team=2 if id==1 else 3
  players[id]=NS(id=id,team=team,alive=live,spawned=live,x=at[0],y=at[1],z=at[2],class_id=0,orientation=(1.,0.,0.),input=NS(crouch=False));p_rows.append(str(id)+','+str(int(live))+','+vec(at))
 entities=[];e_rows=[]
 for id in range(3):
  at=(float(rng.randrange(18,45)),20.+rng.choice([0.,.25,2.]),20.+rng.choice([0.,.25,2.]));radius=rng.choice([0.,.25,1.,2.]);alive=bool(rng.randrange(4));damage=bool(rng.randrange(4));behavior=NS(hit_radius=radius,takes_damage=damage,get_hit_center=lambda e:e.center);entity=NS(entity_id=id,center=at,alive=alive,behavior=behavior);entities.append(entity);e_rows.append(','.join([str(id),str(int(alive)),str(int(damage)),bits(radius),vec(at)]))
 corpses=[];c_rows=[]
 for id in range(2):
  at=(float(rng.randrange(18,45)),20.+rng.choice([0.,.25,2.]),20.+rng.choice([0.,.25,2.]));corpses.append(NS(id=id,x=at[0],y=at[1],z=at[2],orientation=(1.,0.,0.)));c_rows.append(str(id)+','+vec(at))
 server=NS(players=players,config=NS(friendly_fire=ff),world_manager=NS(raycast=lambda *args:block),entity_registry=NS(all=lambda:entities),corpse_lifecycle=NS(iter_hittable=lambda:corpses));combat=CombatSystem(server);hit=combat._trace_authoritative_hit(attacker,origin,direction,maximum)
 output='-'
 if hit is not None:
  kind,target,head,position,part=hit;identity=','.join(map(str,target)) if kind=='block' else str(target.entity_id if kind=='entity' else target.id);output='|'.join([kind,identity,str(int(head)),'-' if part is None else str(part),vec(position)])
 rows.append((str(case),bits(maximum),'-' if block is None else ','.join(map(str,block)),str(int(ff)),';'.join(p_rows),';'.join(e_rows),';'.join(c_rows),output))
raw=('\n'.join('\t'.join(r) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
path=OUT/'hit-trace.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'hit-trace-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/combat_runtime.py','server/lag_compensation.py','server/mode_controls.py')},method_substitutions=False,scope='Unchanged authoritative hit selection and player/entity/corpse geometry. Controlled live/dead and enemy domain inputs; controlled terrain ray result isolates hit selection (separate geometry and native-query evidence covers traversal). No damage/effects or concrete registry integration proof.'),indent=2)+'\n');print('hit trace operations',len(rows),'bytes',path.stat().st_size)
