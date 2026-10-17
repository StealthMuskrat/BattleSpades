"""Unchanged original direct-hit damage and entity-damage helpers across source profiles and victim states."""
import gzip,hashlib,io,json,pathlib,struct
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server.combat_runtime import CombatSystem
from server.game_constants import WEAPON_CATALOG
from server.class_data import CLASS_IDS
bits=lambda x:struct.pack('>d',float(x)).hex()
c=CombatSystem(NS());rows=[];parts=[None,-1,0,1,2,3,4,999]
for tool,profile in sorted(WEAPON_CATALOG.items()):
 for part in parts:
  for head in (False,True):
   rows.append((str(tool),'-','-','-','-',str(int(head)),'-' if part is None else str(part),bits(c._calculate_damage(None,profile,head,target=None,part=part)),bits(c._entity_damage(profile))))
 for cid in [*sorted(CLASS_IDS),-1,999]:
  for hardcore in (False,True):
   for pack in (0,66,67,68,69,255):
    for flying in (False,True):
     target=NS(class_id=cid,jetpack_id=pack,jetpack_active=flying,connection=NS(server=NS(config=NS(game_mode='hc_tdm' if hardcore else 'tdm'))))
     for part in parts:
      for head in (False,True):rows.append((str(tool),str(cid),str(int(hardcore)),str(pack),str(int(flying)),str(int(head)),'-' if part is None else str(part),bits(c._calculate_damage(None,profile,head,target=target,part=part)),bits(c._entity_damage(profile))))
parts_meta=[]
for start in range(0,len(rows),60000):
 raw=('\n'.join('\t'.join(r) for r in rows[start:start+60000])+'\n').encode();buffer=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
 path=OUT/f'direct-damage-{start//60000:02}.tsv.gz';path.write_bytes(buffer.getvalue());parts_meta.append(dict(path=path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
(OUT/'direct-damage-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),profiles=len(WEAPON_CATALOG),parts=parts_meta,source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/combat_runtime.py','server/game_constants.py','server/weapons_retail.py','server/hardcore.py')},method_substitutions=False,scope='Unchanged original damage_for_part, direct victim class/flying-pack/Hardcore policy and entity-damage helpers. Controlled profile and victim domain states; unrounded floating damage only, not health admission or damage effects.'),indent=2)+'\n');print('direct-damage operations',len(rows),'parts',len(parts_meta),'largest',max((OUT/p['path']).stat().st_size for p in parts_meta))
