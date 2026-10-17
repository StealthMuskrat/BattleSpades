"""Unchanged original accepted-trigger aggregation, seed skew and achievement-hook boundaries."""
import gzip,hashlib,io,json,logging,pathlib,random,struct,sys
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server.combat_runtime import CombatSystem
from server.player import Player
from server import anticheat,achievements
logging.disable(logging.CRITICAL);bits=lambda x:struct.pack('>d',float(x)).hex()
def state(p):
 s=getattr(p,'anticheat_stats',{});return ','.join(str(s.get(k,0)) for k in ['shots','hits','headshots','pellet_hits'])+'|'+(';'.join(str(k)+','+','.join(str(v[n]) for n in ['shots','hits','headshots']) for k,v in sorted(s.get('weapons',{}).items())) or '-')+'|'+(';'.join(str(k)+','+str(v) for k,v in sorted(s.get('pellet_seeds',{}).items())) or '-')+'|'+(';'.join(k+'='+str(v) for k,v in sorted(getattr(p,'anticheat_counts',{}).items())) or '-')
rows=[]
for group in range(16):
 players=[Player(i,'Tally',2,6,None) for i in range(2)];combat=CombatSystem(NS());rng=random.Random(179289+group)
 for p in players:p.spawn(36,36,97);p.set_tool(6,raw=True)
 for index in range(700):
  id=rng.randrange(2);op=rng.choices(['begin','hit','end','seed','tool','bot','spawn'],[2,4,2,10,1,1,1])[0];arg=str(id);reports=[];resolved=[]
  if op=='hit':arg+=','+str(rng.randrange(2))
  elif op=='seed':arg+=','+str(rng.choice([255,255,255,rng.randrange(-512,512)]))
  elif op=='tool':arg+=','+str(rng.choice([6,9,52,60]))
  elif op=='bot':arg+=','+str(rng.randrange(2))
  def observe(frame,event,arg):
   if event!='call':return
   f=frame.f_locals
   if frame.f_code is anticheat.report.__code__:reports.append(f['kind']+','+','.join(k+'='+('n'+bits(v) if isinstance(v,float) else 'i'+str(v)) for k,v in sorted(f['detail'].items())))
   if frame.f_code is achievements.shot_resolved.__code__ and f.get('method')=='shot_resolved':resolved.append(','.join(map(str,(f['args'][0].id,f['args'][1],int(f['args'][2])))))
  a=arg.split(',');p=players[id];sys.setprofile(observe)
  try:
   if op=='begin':combat._begin_shot_tally(p)
   elif op=='hit':combat._note_player_hit(p,bool(int(a[1])))
   elif op=='end':combat._end_shot_tally(p)
   elif op=='seed':combat._observe_pellet_seed(p,NS(seed=int(a[1])))
   elif op=='tool':p.set_tool(int(a[1]),raw=True)
   elif op=='bot':p.is_bot=bool(int(a[1]))
   elif op=='spawn':p.spawn(36,36,97)
  finally:sys.setprofile(None)
  rows.append((str(group),op,arg,state(players[0])+'#'+state(players[1]),';'.join(reports) or '-',';'.join(resolved) or '-'))
raw=('\n'.join('\t'.join(r) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
path=OUT/'shot-tally.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'shot-tally-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),groups=16,fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/combat_runtime.py','server/achievements.py','server/anticheat.py')},method_substitutions=False,scope='Unchanged trigger tally, per-tool head/pellet aggregates, seed-mask/skew/chi-square and source achievement-hook call boundaries. Controlled actual Player tool/bot/spawn inputs; profiles observe diagnostics/callbacks without replacing functions. No achievement-rule or persistence proof.'),indent=2)+'\n');print('shot tally operations',len(rows),'bytes',path.stat().st_size)
