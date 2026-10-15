"""Execute original name policy and unmodified connection admission methods.

Connection's selected AST nodes avoid unrelated eager networking/integration
imports. No method body, called rule/selection function or source table is replaced.
"""
import ast, base64, gzip, hashlib, io, json, logging, pathlib, random, struct, typing, unicodedata as U
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from server import player_names as N, game_constants as C, mode_data
from server.game_rules import GameRules, RULE_DEFINITIONS
from server.class_selection import normalize_class_selection
from server.handlers.equipment import is_class_selectable, fallback_class_id

if U.unidata_version!='15.0.0':raise RuntimeError('Use reference Python 3.12 Unicode 15.0.0')
OUT=ROOT/'java/src/test/resources/golden'
def b64(v):return base64.b64encode(v.encode()).decode()
def emit(name,rows):
    raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode()
    compressed=io.BytesIO()
    with gzip.GzipFile(filename='',mode='wb',fileobj=compressed,mtime=0) as stream:stream.write(raw)
    (OUT/(name+'.tsv.gz')).write_bytes(compressed.getvalue())
    print(name,len(rows))
def hashed_string(h,s):
    raw=s.encode();h.update(struct.pack('>i',len(raw)));h.update(raw)
rows=[]
for start in range(0,0x110000,4096):
    end=min(start+4096,0x110000);h=hashlib.sha256()
    for cp in range(start,end):
        if 0xd800<=cp<=0xdfff:continue
        c=chr(cp);cat=U.category(c)
        flags=int(N._is_invisible(c))|(int(cat.startswith('M'))<<1)|(int(c.isspace())<<2)|(int(c.isalnum())<<3)|(U.combining(c)<<8)
        h.update(struct.pack('>ii',cp,flags))
        for s in [U.normalize('NFKC',c),U.normalize('NFKD',c),N.name_skeleton(c),N._safe_base_name(c)]:hashed_string(h,s)
    rows.append((start,end,h.hexdigest()))
emit('name-unicode',rows)

examples=['','Kiko','Κiko','Kіko','Kikо','Kik0','K​iko','Ｋｉｋｏ','KIKO','Kíko',' Kiko ','Admin','[Admin]x','(MOD)x','<Staff>x','Аdmin','A d m i n','[ABC]Kiko','Bottle','B0T_Alpha','[B0T]Alpha','x{BOT}y','x|BOT|y','ß','SS','İ','ı','ﬃ','Å','A\u030a','\u1100\u1161\u11a8','\uac01','ᅟ','ㅤ','⠀','\x00\x01','́́','Ж'*20,'😀'*8,'FifteenByteName','Ki\u202eko','\u1c89','\U0001e030','\U0001e4d0']
rng=random.Random(168)
marks=[chr(cp) for cp in range(0x110000) if U.category(chr(cp)).startswith('M')]
for _ in range(800):
    cp=rng.randrange(0x110000)
    if 0xd800<=cp<=0xdfff:cp=0x61
    examples.append(chr(cp)+''.join(rng.choices(marks,k=rng.randrange(6)))+'A'+''.join(rng.choices(marks,k=3)))
rows=[]
for name in examples:
    rows.append(('normalize',b64(name),'','','',b64(U.normalize('NFKC',name))+';'+b64(U.normalize('NFKD',name))))
    rows.append(('skeleton',b64(name),'','','',b64(N.name_skeleton(name))))
    for index in range(5):
        players=[NS(name='Kiko',admin=index==1),NS(name='Player',admin=False),NS(name='FifteenByteName',admin=False)]
        if index==2:players.extend(NS(name='Player~'+str(i),admin=False) for i in range(2,12))
        extra=['Kiril','SS'] if index==3 else []
        label=['','[BOT]','BOT_','{BOT}','|B0T|'][index]
        encoded_players=','.join(b64(p.name)+':'+str(int(p.admin)) for p in players)
        encoded_extra=','.join(map(b64,extra))
        rows.append(('allocate',b64(name),b64(label),encoded_extra,encoded_players,b64(N.allocate_unique_player_name(name,players,extra_reserved=extra,bot_label=label))))
        rows.append(('bot',b64(name),b64(label),encoded_extra,encoded_players,b64(N.allocate_bot_display_name(name,label,players))))
        rows.append(('reserved',b64(name),b64(label),encoded_extra,encoded_players,b64(str(int(N.is_reserved_name(name,extra))))))
        rows.append(('poses',b64(name),b64(label),encoded_extra,encoded_players,b64(str(int(N.poses_as_bot(name,label))))))
emit('names',rows)

tree=ast.parse((ROOT/'server/connection.py').read_text())
functions={'wire_team_to_internal','internal_team_to_wire','is_non_playable_wire_team'}
methods={'_resolve_join_team','_balance_join_team','_enforce_join_class'}
nodes=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in functions]
original=next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name=='Connection')
nodes.append(ast.ClassDef(name='Connection',bases=[],keywords=[],body=[n for n in original.body if isinstance(n,ast.FunctionDef) and n.name in methods],decorator_list=[]))
env=dict(vars(C));env.update(Optional=typing.Optional,logger=logging.getLogger('admission-oracle'),WIRE_TEAM_SPECTATOR=0,WIRE_TEAM_NEUTRAL=1,DEFAULT_WIRE_TEAM=2)
exec(compile(ast.fix_missing_locations(ast.Module(body=nodes,type_ignores=[])),str(ROOT/'server/connection.py'),'exec'),env)
Connection=env['Connection'];logging.disable(logging.CRITICAL)
rows=[]
for wire in [*range(256),-1,256]:
 for scenario in range(24):
    blue=scenario%5;green=(scenario//3)%5;threshold=[-2,0,1,2,3,10][scenario%6];auto=scenario%4!=0;spec=scenario%2==0;balance=scenario%3!=0
    hook=[None,lambda t:3,lambda t:1][scenario%3] if scenario>=18 else None
    rules=GameRules.server_defaults();rules.apply({'RULE_ENABLE_SPECTATORS':spec})
    players={i:NS(team=t) for i,t in enumerate([2]*blue+[3]*green+[0,1])}
    # A stale self row is excluded from balance counts, even if it is alive/dead/bot.
    own=players.get(0) if scenario%5==0 else None
    conn=Connection();conn.player=own;conn._join_rebalanced=False
    conn.server=NS(config=NS(game_rules=rules,auto_balance=auto,balance_threshold=threshold),players=players,mode=NS(prepare_join_team=hook) if hook else NS())
    team,outwire=conn._resolve_join_team(wire,balance=balance)
    rows.append((wire,blue,green,threshold,int(auto),int(spec),int(balance),0 if hook is None else 3 if scenario%3==1 else 1,-1 if own is None else 0,team,outwire,int(conn._join_rebalanced)))
emit('admission-teams',rows)
rows=[]
for code in [c for c in mode_data.MODES if not c.startswith('hc_')]:
 for variant in range(4):
  rules=GameRules.server_defaults()
  if variant:
   for key,d in RULE_DEFINITIONS.items():
    if d.class_id is not None and (variant==3 or int(d.class_id)%2==variant%2):rules.apply({key:False})
  for custom,ugc in [(False,False),(True,False),(False,True)]:
   server=NS(config=NS(game_rules=rules,game_mode=code,ugc_runtime=ugc),mode=NS(allows_class_selection=lambda *args:True) if custom else NS())
   conn=Connection();conn.server=server
   for cid in range(18):
    selection=normalize_class_selection(cid,range(75),['pillbox'],range(30),disabled_tools=rules.disabled_tools())
    for team in [0,2]:
     result=conn._enforce_join_class(selection,team)
     rows.append((code,variant,int(custom),int(ugc),cid,team,int(is_class_selectable(server,cid)),-1 if fallback_class_id(server) is None else fallback_class_id(server),result.class_id,','.join(map(str,result.loadout)),','.join(map(b64,result.prefabs)),','.join(map(str,result.ugc_tools))))
emit('admission-classes',rows)
manifest={'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','unicode':U.unidata_version,'unicode_scalar_values':0x110000-0x800,'name_examples':len(examples),'name_vectors':len(examples)*22,'team_vectors':258*24,'class_vectors':len(rows),'connection_method_bodies':'unmodified AST extraction; original rules/equipment/selection imports'}
manifest['sha256']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted([*OUT.glob('name*.tsv.gz'),*OUT.glob('admission-*.tsv.gz'),ROOT/'java/src/main/resources/net/battlespades/player/name-unicode.bin.gz'])}
manifest['source_files']={name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in ['server/player_names.py','server/connection.py','server/handlers/equipment.py','server/class_selection.py','server/game_rules.py','shared/constants.py']}
(OUT/'admission-provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
