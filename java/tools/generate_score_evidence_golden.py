"""Unchanged retail profile, combat evidence and objective-score policies over controlled actor/mode inputs."""
import gzip,hashlib,io,json,math,pathlib,re,struct
from types import SimpleNamespace as N
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from server import profile_stats as P,combat_scores as B
import shared.constants as C
import shared.constants_gamemode as CG
bits=lambda v:struct.pack('>d',float(v)).hex()
vec=lambda v:','.join(map(bits,v))
rows=[]
# Data only: original profile class/tool/death maps and aggregate membership.
wanted=set(re.findall(r'"([A-Z][A-Z0-9_]+)"',''.join(p.read_text() for p in (ROOT/'java/src/main/java/net/battlespades/match').glob('*.java'))))|{f'COMBAT_{n}INAROW_TOTAL' for n in (5,10,15)}
constants={n:int(getattr(C,n)) for n in sorted(wanted) if hasattr(C,n) and isinstance(getattr(C,n),int)}
constants.update(ASSIST_THRESHOLD=int(100*CG.GENERIC_ASSIST_PERCENTAGE/100),ASSIST_SCORE=int(CG.GENERIC_SCORE_ASSIST))
lines=['# Exported source data; do not edit.','[constants]']+[f'{n} = {v}' for n,v in sorted(constants.items())]
lines+=['[class_kill]']+[f'"{cls}_{tool}" = {getattr(C,prefix+"_"+label+"_KILLS")}' for cls,prefix in P._CLASS_PREFIX.items() for tool,label in P._TOOL_LABELS.items() if hasattr(C,prefix+'_'+label+'_KILLS')]
lines+=['[kill_tool]']+[f'k{k} = {v}' for k,v in P._KILL_TO_TOOL.items()]
lines+=['[map_stat]']+[f'"{k}" = {v}' for k,v in P._MAP_STATS.items()]
for k,v in C.SCORE_REASONS_FOR_TOTALS.items():lines+=['[[aggregate]]',f'id = {k}','members = '+str(v)]
catalog=ROOT/'java/src/main/resources/net/battlespades/catalog/match.toml';catalog.write_text('\n'.join(lines)+'\n')
def prof(p):
 s=getattr(p,'profile_stats',None)
 if s is None:return '-'
 return ';'.join([','.join(f'{k}:{a}:{b}' for k,(a,b) in s.values.items()) or '-',','.join(f'{k}:{bits(v)}' for k,v in s.remainder.items()) or '-',','.join(map(str,[s.shot_serial,s.hit_serial,s.last_score,int(s.activity_seen),s.human_opponents,s.bot_opponents])),','.join(map(bits,[s.connected_seconds,s.active_seconds,s.idle_seconds,s.afk_seconds,s.opponent_sample_seconds])),vec(s.last_position) if s.last_position is not None else '-',vec(s.last_orientation) if s.last_orientation is not None else '-'])
def roundstate(p):
 s=getattr(p,'round_combat_stats',None)
 if s is None:return '-'
 return ';'.join([','.join(f'{k}:{v}' for k,v in s.awards.items()) or '-',','.join(f'{k}:{bits(v)}' for k,v in s.measures.items()) or '-',bits(s.last_airstrike_survived_at),vec(s.last_position) if s.last_position is not None else '-'])
def broadcast(data,**kwargs):
 sent.append(data.hex())
 if fault:raise RuntimeError("controlled terminal send fault")
def setup():
 server=N(players={},config=N(ugc_runtime=False,default_mode='tdm',default_map='ww2'),mode=N(ended=False,retiring=False,generic_scoring_enabled=True,spec=N(solo=False)),world_manager=N(map_name=''),broadcast=broadcast)
 actors=[N(id=i,team=[2,2,3,0][i],alive=i!=3,spawned=i!=3,is_bot=False,x=36.,y=36.,z=97.,position=(36.,36.,97.),orientation=(1.,0.,0.),tool=6,class_id=0,health=100,score=0,kill_streak=0,replication_generation=1,connection=N(server=server),round_combat_stats=B.RoundCombatStats()) for i in range(4)]
 server.players=dict(enumerate(actors));return server,actors
for domain in ['profile','combat','objective','activity']:
 for trace in range(16):
  sent=[];fault=False;server,actors=setup();p=actors[0]
  for step in range(128):
   op=step%(10 if domain=='profile' else 8 if domain=='combat' else 4);at=(36.+(step%7)*.25,36.+(step%3)*.125,97.+(step%5)*.0625);aim=((1.,0.,0.) if step%5 else (0.,1.,0.));alive=step%13!=0;bot=step%17==0;air=step%4==0;fire=step%3==0;tool=[6,9,18,11,52,57,62,100,7,8,12,13,14,16,17,19,20,21,29,31,32,33,34,35,36,49,50,53,54,55,58,59,60,61][(step//4+trace)%34];cls=[0,1,2,3,4,5,6,7,8,9,10,11,12,14,15,16,17][(step//5+trace)%17];health=[0,1,20,21,100][step%5];mode=['tdm','ctf','zom','ugc','TUTORIAL'][((step//29)+trace)%5];ended=step%23==0;ugc=step%31==0;retiring=step%19==0;score=(step*13)%311-90;streak=[0,5,10,15][(step//7)%4];kt=[0,1,2,3,4,7,8,9,10,16,17,34,35,36][(step+trace)%14];dt=[0.,.1,.25,.5,1.,1.01][(step+trace)%6];amount=[-2.,0.,.5,1.5,49.5,50.5,100.][(step+trace)%7];count=(step+trace)%7-2;extra=(step+trace)%4
   map_name=['ww2','Chicago','Ancient Egypt','arctic_base','Bran Castle','Dragon Island','London','Lunar Base','Mayan Jungle','Spooky Mansion','Tokyo Neon','Block Ness','Castle Wars','WW1','To The Bridge','unknown'][trace]
   if domain=='activity':
    op=7;dt=1.;mode='tdm';ended=ugc=retiring=bot=False;alive=step<110;air=step%4==0;at=(36.+(.5 if 1<=step<75 else 0.),36.,97.);aim=(1.,0.,0.);actors[2].is_bot=step>=90
   fault=domain=='objective' and step%11==0
   if domain=='objective':
    roster_case=(step//4+trace)%3
    server.players.pop(0,None)
    if roster_case!=1:server.players[0]=p if roster_case==0 else actors[1]
   else:roster_case=0
   server.config.default_map=map_name
   p.position=(p.x,p.y,p.z)=at;p.orientation=aim;p.alive=alive;p.is_bot=bot;p.airborne=air;p.on_fire=fire;p.tool=tool;p.class_id=cls;p.health=health;p.score=score;p.kill_streak=streak
   server.config.default_mode=mode;server.config.ugc_runtime=ugc;server.mode.ended=ended;server.mode.retiring=retiring;server.mode.generic_scoring_enabled=step%3!=0;server.mode.spec.solo=step%5==0
   result='-';sent.clear();stat=[0,47,64,143,162,202,219,250,1006,2009,3006,999][(step//10+trace)%12]
   try:
    if domain in {'profile','activity'}:
     if op==0:P.add(p,stat,max(0,count),max(0,int(amount)))
     elif op==1:
      state=P.tracker(p)
      if state is not None:state.fractional(stat,amount)
     elif op==2:P.shot(p)
     elif op==3:P.hit(p,actors[extra%3])
     elif op==4:P.death(actors[2],p if extra<2 else actors[2] if extra==2 else None,kt)
     elif op==5:P.score_changed(p,[1,3,5,41,47,55,220,250][(step//10+trace)%8])
     elif op==6:B.record_profile_total(p,stat,max(0,count))
     elif op==7:P.tick(p,server,dt)
     elif op==8:
      state=getattr(p,'profile_stats',None)
      if state is not None:state.last_score=0
     else:
      copy=P.snapshot(p)
      if copy:next(iter(copy.values()))[0]+=999 # copied result cannot mutate live values
    elif domain=='combat':
     if op==0:B.record_damage_taken(server,p,actors[extra%3],amount,kt,now=step*.5)
     elif op==1:B.record_shot(server,p)
     elif op==2:B.record_blocks_placed(server,p,[(0,0,z) for z in range(239-count,239)])
     elif op==3:B.record_blocks_destroyed(server,p,count,[list(range(n)) for n in [extra,extra+2]])
     elif op==4:B.record_crate(server,p,extra)
     elif op==5:B.round_tick(p,server,dt)
     else:
      victim=actors[2];victim.replication_generation=step//8+1;victim.tool=18;victim.damage_contributions={0:B.DamageContribution(p,2,1,20,step*.5),1:B.DamageContribution(actors[1],2,1,[49.,50.,100.,75.][extra],step*.5-[0.,5.,5.001,-1.][extra])};killer=p if extra!=3 else victim
      B.record_death(server,victim,killer,kt,now=step*.5,domination=extra==1)
    else:
     if op==0:result=str(B.award_score_event(server,p,int(amount),55,award=14,mode=server.mode))
     elif op==1:result=str(B.award_kill_event(server,p,['defend','distract','intercept','absent'][extra],{'defend':10,'distract':20,'intercept':30},{'defend':55,'distract':53,'intercept':58},mode=server.mode))
     elif op==2:
      # Evaluate unchanged priority geometry with overlapping points and live/dead carriers.
      actors[1].alive=extra!=2;actors[1].position=(actors[1].x,actors[1].y,actors[1].z)=(37.,36.,97.)
      result=B.classify_objective_kill(p,actors[2],victim_carrying=extra==0,killer_team_carriers=[p,actors[1]],defend_points=[(36.,36.,97.)] if extra==1 else [],attack_points=[at] if extra!=3 else [],carrier_threat_radius=extra*.5,threat_radius=extra*.5) or '-'
     else:result=str(B.is_distraction(p,actors[2],kt,[p,actors[1]],extra*.5))
   except (ValueError,OverflowError,RuntimeError) as exc:result=type(exc).__name__
   snapshot='|'.join((prof(a) if domain in {'profile','activity'} else roundstate(a)) for a in actors)+'#'+','.join(str(a.score) for a in actors)+'#'+','.join(sent)
   rows.append((domain,trace,step,op,vec(at),vec(aim),int(alive),int(bot),int(air),int(fire),tool,cls,health,mode,int(ended),int(ugc),int(retiring),score,streak,kt,bits(dt),bits(amount),count,extra,stat,result,snapshot,map_name,int(fault),roster_case))
# Numerical independent CPython norm vectors include extreme scales and one-ULP boundaries.
for case in range(2048):
 a=math.ldexp(1.+(case%97)/97,case%1800-1000);b=math.ldexp(1.+(case%37)/37,case%1600-900);d=math.ldexp(1.+(case%19)/19,case%1400-800)
 rows.append(('norm',case,vec((a,b,d)),bits(math.dist((a,b,d),(0,0,0)))))
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();out=ROOT/'java/src/test/resources/golden';buf=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buf,mtime=0) as stream:stream.write(raw)
p=out/'score-evidence.tsv.gz';p.write_bytes(buf.getvalue())
(out/'score-evidence-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',profile_operations=2048,activity_ticks=2048,combat_operations=2048,objective_operations=2048,norm_cases=2048,source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ['server/profile_stats.py','server/combat_scores.py','server/scoreboard.py','server/mode_controls.py','shared/constants.py']},fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),catalog_sha256=hashlib.sha256(catalog.read_bytes()).hexdigest(),method_substitutions=False,scope='Actual unchanged policies/native score packets; controlled actor/config/clock/accepted contribution inputs and terminal broadcast collector. No mode-policy substitution. Signed-64-bit counters; typed vectors and explicit mode relation. External profile persistence/rewards, teabag corpse lookup and concrete modes remain outside this evidence.'),indent=2)+'\n')
print('Score evidence:',len(rows),'rows;',p.stat().st_size,'bytes')
