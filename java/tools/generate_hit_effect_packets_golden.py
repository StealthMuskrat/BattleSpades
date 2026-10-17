"""Original Cython direct-hit packet codecs and source feedback packet builder."""
import gzip,hashlib,io,json,pathlib,random,struct
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server.combat_runtime import CombatSystem
from shared.packet import HitEntity,ShootResponse
from shared.bytes import ByteReader
bits=lambda x:struct.pack('>d',float(x)).hex()
rows=[];rng=random.Random(179288)
for i in range(4000):
 pos=tuple(rng.choice([rng.uniform(-700,700),0.,-0.,.0078125,-.0078125,.015625,-.015625,511.99]) for _ in range(3));entity=HitEntity();entity.entity_id=rng.randrange(-32768,32768);entity.x,entity.y,entity.z=pos;entity.type=rng.randrange(256);wire=bytes(entity.generate());decoded=HitEntity(ByteReader(wire[1:]));rows.append(('entity',str(entity.entity_id),str(entity.type),','.join(map(bits,(entity.x,entity.y,entity.z))),wire.hex(),','.join(map(bits,(decoded.x,decoded.y,decoded.z))),bytes(decoded.generate()).hex()))
 response=ShootResponse();response.damage_by=rng.randrange(256);response.damaged=rng.randrange(256);response.blood=rng.randrange(256);response.position_x,response.position_y,response.position_z=pos;wire=bytes(response.generate());decoded=ShootResponse(ByteReader(wire[1:]));rows.append(('response',','.join(map(str,(response.damage_by,response.damaged,response.blood))),'-',','.join(map(bits,(response.position_x,response.position_y,response.position_z))),wire.hex(),','.join(map(bits,(decoded.position_x,decoded.position_y,decoded.position_z))),bytes(decoded.generate()).hex()))
 loop=rng.randrange(-2**31,2**31);p=NS(id=rng.randrange(255),tool=rng.randrange(66));packet=NS(seed=rng.randrange(-512,512),shot_on_world_update=rng.randrange(-2**31,2**31));feedback=CombatSystem(NS(loop_count=loop))._build_shoot_feedback_packet(p,packet);rows.append(('feedback',','.join(map(str,(loop,p.id,p.tool,packet.shot_on_world_update,packet.seed))),'-','-',bytes(feedback.generate()).hex(),'-',bytes(feedback.generate()).hex()))
raw=('\n'.join('\t'.join(r) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
path=OUT/'hit-effect-packets.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'hit-effect-packets-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('shared/packet.pyx','server/combat_runtime.py')},method_substitutions=False,scope='Unchanged original Cython packet20/9 encode/decode, source ShootFeedback builder and codec. Controlled field values; no damage/shot authorization proof.'),indent=2)+'\n');print('hit effect packet operations',len(rows),'bytes',path.stat().st_size)
