"""Original immutable WorldUpdate transforms, including malformed variable tails."""
import gzip,hashlib,io,json,pathlib,random
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server.replication import ReplicationService as R
from shared.packet import WorldUpdate,Entity
rows=[];rng=random.Random(2525)
def emit(op,arg,result):rows.append((op,arg,result))
for group in range(160):
 packet=WorldUpdate();packet.loop_count=group
 for id in range(group%33):packet[id]=((100.+id,200.,50.),(1.,0.,0.),(.125,0.,0.),40,1000+id,100,1,255,15,7,255,12.5,1.5,0.)
 entities=[]
 for id in (1,32767,32768,40000,65535):
  e=Entity();e.entity_id=id;e.type=12;e.state=0;e.player_id=1;e.pos_x=100;e.pos_y=110;e.pos_z=50;e.vel_x=1;e.vel_y=0;e.vel_z=-.5
  e.int_properties=list(range(group%7));e.float_properties=[.25,-.125]*(group%3);entities.append(e)
 packet.updated_entities=entities;packet.rocket_turrets=[(1,.5,.25),(-25536,.5,-.25)]
 data=bytes(packet.generate());prefix,players,tail=R._parse_snapshot(data)
 held={1,-25536,-1};filtered=R._tail_without(tail,held)
 emit('tail',tail.hex()+',1:-25536:-1',filtered.hex())
 for malformed in (tail[:group%len(tail)],tail+b'\0',b'',b'\1'):
  emit('tail',malformed.hex()+',1:-25536:-1',R._tail_without(malformed,held).hex())
 for limit in (67,180,1329):
  parts=R._observer_parts(prefix,list(players.values()),tail,limit)
  emit('parts',data.hex()+','+str(limit),';'.join(p.hex() for p in parts))
 offsets=R._player_tool_offsets(data)
 for id in (0,group%33,99):emit('local',data.hex()+','+str(id),R._with_local_owner_overrides(data,offsets,id).hex())
 if players:emit('owner',prefix.hex()+','+next(iter(players.values())).hex(),R._owner_payload(prefix,next(iter(players.values()))).hex())
 # Duplicate/incomplete player sections cannot prove offsets to patch.
 for malformed in (data[:group%len(data)], b'\x02'+bytes(4)+b'\x02\x00'+bytes(112)+bytes(4)):
  parsed=R._parse_snapshot(malformed)
  emit('parse',malformed.hex(),'-' if parsed is None else parsed[0].hex()+'|'+':'.join(map(str,parsed[1]))+'|'+parsed[2].hex())
raw=('\n'.join('\t'.join(r) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
path=OUT/'replication-wire.tsv.gz';path.write_bytes(buffer.getvalue())
(OUT/'replication-wire-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','source_file_sha256':hashlib.sha256((ROOT/'server/replication.py').read_bytes()).hexdigest(),'operations':len(rows),'fixture_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'scope':'Unchanged original ReplicationService wire transforms, native WorldUpdate/Entity serialization; controlled malformed and complete packets.'},indent=2)+'\n')
print('replication wire operations',len(rows),'bytes',path.stat().st_size)
