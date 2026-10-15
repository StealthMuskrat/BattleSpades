"""Execute unmodified original Player.damage/heal, conduct attribution and team credit.

The profile hook observes original combat-score call boundaries without replacing
any method. Other source score/award/conduct policies execute but are outside this
checkpoint's expected state. Only HP/KillAction wire packets are compared here.
"""
import gzip,hashlib,io,json,logging,pathlib,random,struct,sys,time
from contextlib import ExitStack
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from aoslib import vxl,world
from server.player import Player
from server.game_rules import GameRules
from server.class_selection import ClassSelection
from server import conduct,combat_scores
from server.handlers.team import recent_enemy_attacker,end_life_for_transition
assert sys.version_info[:2]==(3,12)
OUT=ROOT/'java/src/test/resources/golden';logging.disable(logging.CRITICAL)
clock=[100.0,1000.0];original=(time.monotonic,time.time);time.monotonic=lambda:clock[0];time.time=lambda:clock[1]
def bits(v):return struct.pack('>d',float(v)).hex()
def number(v):return struct.unpack('>d',bytes.fromhex(v))[0]
def vector(v):return '-' if v is None else ','.join(bits(x) for x in v)
def emit(name,rows):
 raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();b=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=b,mtime=0) as z:z.write(raw)
 (OUT/(name+'.tsv.gz')).write_bytes(b.getvalue());print(name,len(rows))
def history(server,entries):
 return '|'.join(','.join(map(str,[c.player.id,c.team,c.life,bits(c.damage),bits(c.touched),int(server.players.get(c.player.id) is c.player)])) for c in entries) or '-'
def state(server):
 result=[]
 for p in server.players.values():
  fields=[p.id,p.health,p.max_health,int(p.alive),int(p.spawned),int(p.god_mode),p.replication_generation,bits(p._last_damage_at),bits(p._last_combat_damage_at),p._last_damage_source_id,p._last_damage_kill_type,vector(p._last_damage_source_position),bits(p.spawn_protection_remaining()),p.kills,p.deaths,p.kill_streak,getattr(p,'last_killed_by_id',-1),p.last_kill_action_data.hex() if p.last_kill_action_data else '-',history(server,getattr(p,'damage_contributions',{}).values())]
  result.append('~'.join(map(str,fields)))
 return ';'.join(result)
class Fixture:
 def __init__(self,mode='tdm',duration=0,weapon=1,zombie=1,one_hit=False):
  self.wire=[];self.evidence=[];self.modifier='none';self.scopes=[];clock[:]=[100,1000]
  rules=GameRules.server_defaults();rules.values.update(RULE_SPAWN_PROTECTION_TIME=duration,RULE_WEAPON_DAMAGE=weapon,RULE_ZOMBIE_CLASS_DAMAGE=zombie,RULE_ONE_HIT_KILL=one_hit)
  m=vxl.VXL(-1,b'',0,2);w=world.World(m)
  self.server=NS(config=NS(game_rules=rules,default_mode=mode,game_mode=mode,respawn_time=1.5,conduct=NS(grief_kick_enabled=False,grief_warn_points=1e9)),mode=NS(),players={},teams={2:NS(color=(18,52,86)),3:NS(color=(50,60,70))},world_manager=NS(world=w),queue_mode_event=lambda *args:None)
  self.server.broadcast=lambda data:self.capture('B',data)
  for i,team in enumerate([2,3,2]):self.add(i,team)
 def add(self,id,team):
  conn=NS(server=self.server,in_game=True,send=lambda data:self.capture('O'+str(id),data));p=Player(id,'Life',team,6,conn);self.server.players[id]=p;p.apply_class_selection(ClassSelection(0,(5,6,23)));p.spawn(36+id*5.125,-1.5 if id==1 else 36,97.25);return p
 def capture(self,lane,data):
  raw=bytes(data)
  if raw[0] in (5,46):self.wire.append(lane+':'+raw.hex())
 def mode_modifier(self,name):
  self.modifier=name
  if name=='none':
   if hasattr(self.server.mode,'modify_incoming_damage'):del self.server.mode.modify_incoming_damage
  else:self.server.mode.modify_incoming_damage=lambda p,amount,source,kind:{'identity':lambda:amount,'veto':lambda:0,'half':lambda:amount*0.5,'double':lambda:amount*2,'minus':lambda:amount-0.5}[name]()
 def observe(self,frame,event,arg):
  if frame.f_code is combat_scores.record_death.__code__ and event=='call':
   a=frame.f_locals;v=a['victim'];k=a['killer'];self.evidence.append('death:'+str(v.id)+':'+str(-1 if k is None else k.id)+':'+str(a['kill_type'])+':'+history(self.server,getattr(v,'damage_contributions',{}).values()))
  elif frame.f_code is combat_scores.record_damage_taken.__code__ and event=='call':
   a=frame.f_locals;v=a['victim'];s=a['source'];self.evidence.append('damage:'+','.join(map(str,[v.id,-1 if s is None else s.id,int(a['damage']),a['kill_type'],v.health,int(v.health<=0)])))
  elif frame.f_code is combat_scores.record_healing.__code__ and event=='return':
   a=frame.f_locals;v=a['victim'];self.evidence.append('heal:'+str(v.id)+':'+str(int(a['restored'])))
 def op(self,op,arg):
  self.wire.clear();self.evidence.clear();a=arg.split(',');result='-'
  sys.setprofile(self.observe)
  try:
   p=lambda n:self.server.players[int(n)]
   if op=='damage':
    hp=None if a[4]=='-' else int(a[4]);result=str(int(p(a[0]).damage(number(a[1]),None if a[2]=='-' else p(a[2]),int(a[3]),hp_damage_type=hp)))
   elif op=='heal':p(a[0]).heal(int(a[1]))
   elif op=='advance':clock[0]+=number(a[0]);clock[1]+=number(a[0])
   elif op=='god':p(a[0]).god_mode=bool(int(a[1]))
   elif op=='cap':p(a[0]).spawn_protection_cap=None if a[1]=='-' else number(a[1])
   elif op=='health':p(a[0]).health=int(a[1]);p(a[0]).max_health=int(a[2])
   elif op=='live':p(a[0]).connection.in_game=bool(int(a[1]))
   elif op=='modifier':self.mode_modifier(a[0])
   elif op=='scoring':self.server.mode.ended=not bool(int(a[0]))
   elif op=='spawn':
    q=p(a[0]);q.apply_pending_selection();q.spawn(36+q.id*5.125,-1.5 if q.id==1 else 36,97.25)
   elif op=='class':p(a[0]).apply_class_selection(ClassSelection(int(a[1]),(5,6,23)))
   elif op=='die':p(a[0]).die(None if a[1]=='-' else p(a[1]),int(a[2]))
   elif op=='recent':q=recent_enemy_attacker(self.server,p(a[0]));result=str(-1 if q is None else q.id)
   elif op=='transition':q=end_life_for_transition(self.server,p(a[0]),int(a[1]));result=str(-1 if q is None else q.id)
   elif op=='remove':self.server.players.pop(int(a[0]),None)
   elif op=='add':self.add(int(a[0]),int(a[1]))
   elif op=='blast':scope=conduct.blast_instigator(self.server,None if a[0]=='-' else p(a[0]));scope.__enter__();self.scopes.append(scope)
   elif op=='endblast':self.scopes.pop().__exit__(None,None,None)
   else:raise AssertionError(op)
  except (ValueError,OverflowError):result='error'
  finally:sys.setprofile(None)
  return result,state(self.server),'|'.join(self.wire) or '-',';'.join(self.evidence) or '-'
try:
 rng=random.Random(410)
 rows=[]
 for case in range(1152):
  mode='tdm' if case%2 else 'zom';cid=[0,4,14,15][(case//2)%4];weapon=[0,0.5,1,1.25,2][(case//8)%5];zombie=[0,0.5,1,2][(case//40)%4];one=case%3==0;duration=3 if case%7==0 else 0
  f=Fixture(mode,duration,weapon,zombie,one);f.server.players[1].apply_class_selection(ClassSelection(cid,(5,6,23)))
  cap=[None,0,1.5,float('nan')][case%4];f.server.players[0].spawn_protection_cap=cap;god=case%19==0;f.server.players[0].god_mode=god
  health=[1,2,50,100,300][case%5];maximum=[0,1,100,300][case%4];f.server.players[0].health=health;f.server.players[0].max_health=maximum
  elapsed=[0,0.1,3,3.000001][(case//5)%4];clock[0]+=elapsed;clock[1]+=elapsed
  modifier=['none','identity','veto','half','double','minus'][(case//4)%6];f.mode_modifier(modifier)
  source=[None,0,1,2][(case//3)%4];amount=rng.choice([-10,-0.5,0,0.499999,0.5,0.500001,1,1.5,2.5,49.5,99.5,100.5,1e300]);kind=case%256;hp=None if case%6==0 else case%5;live=case%11!=0;f.server.players[0].connection.in_game=live
  args=','.join(map(str,[mode,cid,bits(weapon),bits(zombie),int(one),bits(duration),'-' if cap is None else bits(cap),int(god),health,maximum,bits(elapsed),modifier,'-' if source is None else source,bits(amount),kind,'-' if hp is None else hp,int(live)]))
  result=f.op('damage',','.join(['0',bits(amount),'-' if source is None else str(source),str(kind),'-' if hp is None else str(hp)]));rows.append((case,args,*result))
 emit('health-damage',rows)
 rows=[]
 for case in range(120):
  f=Fixture();health=[0,1,50,100,200,500][case%6];maximum=[-5,0,1,100,300][(case//6)%5];amount=[-500,-1,0,1,25,100,2147483647][case%7];live=case%3!=0;p=f.server.players[0];p.health=health;p.max_health=maximum;p.connection.in_game=live
  rows.append((case,','.join(map(str,[health,maximum,amount,int(live)])),*f.op('heal',f'0,{amount}')))
 emit('health-healing',rows)
 rows=[]
 for outer in [None,0,1,2]:
  for nested in [-2,None,0,1,2]:
   for source in [None,0,1,2]:
    f=Fixture();p=f.server.players
    with ExitStack() as stack:
     stack.enter_context(conduct.blast_instigator(f.server,None if outer is None else p[outer]))
     if nested!=-2:stack.enter_context(conduct.blast_instigator(f.server,None if nested is None else p[nested]))
     attributed=conduct.attribute_damage_source(f.server,p[0],None if source is None else p[source])
     rows.append(('-' if outer is None else outer,'none' if nested==-2 else '-' if nested is None else nested,'-' if source is None else source,-1 if attributed is None else attributed.id))
 emit('health-attribution',rows)
 rows=[]
 for case in range(16):
  f=Fixture('tdm',3 if case%2 else 0);ops=[]
  def op(kind,arg):rows.append((case,kind,arg,*f.op(kind,arg)))
  op('cap','0,'+bits(0));op('cap','2,'+bits(0));op('damage','0,'+bits(30)+',1,3,-');op('heal','0,10');op('damage','0,'+bits(35)+',2,1,3');op('recent','0');op('advance',bits(5 if case%4==0 else 5.000001));op('recent','0')
  op('damage','0,'+bits(1)+',1,0,-');op('die','1,-,0');op('damage','0,'+bits(100)+',-,7,-');op('heal','0,200');op('spawn','0');op('recent','0');op('spawn','1')
  op('modifier','identity');op('damage','0,'+bits(0.5)+',1,0,-');op('modifier','none');op('damage','0,'+bits(0.5)+',1,0,-');op('scoring','0');op('damage','0,'+bits(10)+',1,0,-');op('scoring','1')
  op('cap','0,'+bits(0));op('damage','0,'+bits(60)+',1,2,-');op('heal','0,20');op('spawn','1');op('damage','0,'+bits(3)+',1,0,-');op('remove','1');op('heal','0,5');op('add','1,3');op('damage','0,'+bits(2)+',1,0,-');op('transition','0,10');op('spawn','0')
  op('blast','2');op('blast','1');op('cap','0,'+bits(0));op('damage','0,'+bits(5)+',0,3,-');op('endblast','-');op('damage','0,'+bits(5)+',-,3,4');op('endblast','-')
  op('blast','1');op('damage','0,'+bits(2)+',0,3,-');op('endblast','-');op('advance',bits(0.25));op('damage','0,'+bits(2)+',1,3,-');op('transition','0,8');op('spawn','0');op('recent','0')
  op('damage','0,'+bits(float('nan'))+',1,0,-');op('damage','0,'+bits(float('inf'))+',1,0,-');op('modifier','veto');op('damage','0,'+bits(float('inf'))+',1,0,-')
 emit('health-traces',rows)
finally:sys.setprofile(None);time.monotonic,time.time=original
manifest={'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','policy_substitutions':False,'scope':'Player.damage/heal, accepted-damage and healing ledger, conduct blast attribution, recent enemy and transition/fall credit, death integration; HP/KillAction wire packets','inputs':'original native World/VXL, explicit clocks/rule values/mode arithmetic/camera positions; conduct warning/kick thresholds configured outside the test; no raw hit validation','observation':'sys.setprofile observes original record_damage_taken entry, record_healing return and record_death entry; no method is replaced; other source observers run but awards/assist-score packets are excluded from expected state','source_files':{p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/player.py','server/conduct.py','server/combat_scores.py','server/handlers/team.py','server/game_rules.py','server/kill_feed.py','shared/packet.pyx']},'fixtures':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(OUT.glob('health-*.tsv.gz'))}}
(OUT/'health-provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
