"""Original Cython ShootPacket serialization and reserved-flag decode normalization."""
import gzip,hashlib,io,json,pathlib,struct
ROOT=pathlib.Path(__file__).resolve().parents[2]
import sys
sys.path.insert(0,str(ROOT))
from shared.packet import ShootPacket
from shared.bytes import ByteReader
rows=[]
for index in range(768):
 p=ShootPacket();p.loop_count=[0,2147483647,-2147483648][index%3];p.shooter_id=index%256;p.shot_on_world_update=123456
 p.x,p.y,p.z=[(.125,-.25,100.5),(-0.,1e-30,-1e30),(1.,2.,3.)][index%3]
 p.ori_x,p.ori_y,p.ori_z=1.,-.5,.125;p.damage=[-32768,0,32767][index%3];p.penetration=-1;p.affect_shooter=index&1;p.secondary=(index>>1)&1;p.seed=255-index%256
 canonical=bytes(p.generate());wire=bytearray(canonical);wire[-2]=index%256;read=ShootPacket();read.read(ByteReader(bytes(wire[1:])))
 rows.append((index,canonical.hex(),bytes(wire).hex(),bytes(read.generate()).hex()))
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
path=ROOT/'java/src/test/resources/golden/shoot.tsv.gz';path.write_bytes(buffer.getvalue())
path.with_name('shoot-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','source_file_sha256':hashlib.sha256((ROOT/'shared/packet.pyx').read_bytes()).hexdigest(),'operations':len(rows),'fixture_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'scope':'Unmodified original Cython ShootPacket generate/read, signed integers and float32 stores, all flag bytes and ignored reserved bits.'},indent=2)+'\n')
print(len(rows),'source ShootPacket vectors',path.stat().st_size,'bytes')
