"""Unchanged Team, EscortTracker and RoundCombatStats over controlled identity/coordinate inputs."""
import gzip,hashlib,io,json,math,pathlib,random,struct
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server.team import Team
from server.combat_scores import EscortTracker,RoundCombatStats
bits=lambda v:struct.pack('>d',float(v)).hex();vec=lambda v:','.join(map(bits,v));rows=[]
class Actor:
 def __init__(self,key,id,team):self.key=key;self.id=id;self.team=team;self.alive=True;self.spawned=True;self.x=36.;self.y=36.;self.z=97.
def ids(values):return ','.join(str(p.key) for p in values) or '-'
def stats(s):return ';'.join([','.join(f'{k}:{v}' for k,v in s.awards.items()) or '-',','.join(f'{k}:{bits(v)}' for k,v in s.measures.items()) or '-',bits(s.last_airstrike_survived_at)])
for trace in range(16):
 actors=[Actor(i,[0,1,2,0][i],2 if i!=2 else 3) for i in range(4)];team=Team([2,3,0,1][trace%4],'Blue',(17,29,251));tracker=EscortTracker([-2.,0.,3.,5.][trace%4],[-1.,0.,.5,2.][trace%4]);state=RoundCombatStats()
 for step in range(128):
  op=(step+trace)%12;a=actors[(step//3+trace)%4];value=step*3-100;at=(step/8,trace+.5,step%240+.25);result='-'
  if op==0:team.add_player(a)
  elif op==1:team.remove_player(a)
  elif op==2:team.add_spawn_point(*at)
  elif op==3:result=vec(team.get_spawn_point())
  elif op==4:team.locked_score=bool((step//12+trace)%2);team.add_score(value)
  elif op==5:team.add_capture()
  elif op==6:team.add_kill()
  elif op==7:team.reset()
  elif op==8:team.set_intel_position(*at)
  elif op==9:team.pick_up_intel(a)
  elif op==10:team.drop_intel(*at)
  else:team.return_intel(at)
  snapshot=';'.join([str(team.color_int),str(team.score),str(team.captures),str(team.kills),str(team.spawn_index),str(int(getattr(team,'locked_score',False))),ids(team.players),'-' if team.intel_position is None else vec(team.intel_position),'-' if team.intel_holder is None else str(team.intel_holder.key),'|'.join(vec(p) for p in team.spawn_points) or '-'])
  rows.append(('team',trace,step,op,a.key,value,vec(at),result,snapshot))
  # Each query observes the actual object's identity, including two objects sharing id zero.
  carrier=actors[0];candidate=actors[1+step%3];distance=[0.,2.,3.,3.5,5.,7.,math.nextafter(5.,math.inf)][(step+trace)%7];candidate.x=36.+distance;candidate.y=36.;candidate.z=97.;candidate.alive=candidate.spawned=(step%9!=0)
  action=step%17
  if action==0:tracker.reset()
  elif action==1:tracker.forget_carrier(carrier)
  order=[actors[(step+i)%4] for i in range(4)];order.append(order[0]);result=ids(tracker.escorts(carrier,order));rows.append(('escort',trace,step,candidate.key,vec((candidate.x,candidate.y,candidate.z)),int(candidate.alive),action,ids(order),result))
  stat=(step+trace)%30;kind=step%3;amount=[-2.,0.,.125,.5,1.5,3.999999999999,100000.25][(step//3+trace)%7]
  if kind==0:state.increment(stat,int(amount))
  elif kind==1:state.raise_to(stat,int(amount))
  else:state.accumulate(stat,amount)
  rows.append(('stats',trace,step,kind,stat,bits(amount),stats(state)))
rng=random.Random(682516)
for case in range(8192):
 value=[2.675,-2.675,.005,-.005,36.005,-0.,1e308,1e-308][case%8] if case<512 else math.ldexp(rng.uniform(-1,1),rng.randrange(-500,900));digits=2+case%2
 rows.append(('round',case,bits(value),digits,bits(round(value,digits))))
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
p=OUT/'match-state.tsv.gz';p.write_bytes(buffer.getvalue());(OUT/'match-state-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',team_operations=2048,escort_queries=2048,round_stat_operations=2048,rounding_cases=8192,source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ['server/team.py','server/combat_scores.py','modes/objective_guard.py']},fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),method_substitutions=False,scope='Actual unchanged Team, EscortTracker, RoundCombatStats and CPython round; controlled plain identity actors, coordinates and lifecycle flags. Includes duplicate identities/numeric-id reuse, ordered hysteresis, score locks/reset/spawn rotation/intel state. Typed Java counters cover recorded signed-64-bit inputs, not arbitrary Python integer/object conversion.'),indent=2)+'\n');print('Match state:',len(rows),'rows;',p.stat().st_size,'bytes')
