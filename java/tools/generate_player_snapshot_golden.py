"""Original live Player snapshot/flag methods after native movement and flight steps."""
import gzip,hashlib,io,json,time
from generate_flight_golden import FlightFixture,ROOT,OUT,original,bits
from shared.packet import WorldUpdate
rows=[]
try:
 for group in range(4):
  f=FlightFixture(group)
  def emit(op,arg):
   f.op(op,arg);p=f.p
   # Server-owned presentation domain inputs, deliberately independent of ClientData fire flags.
   p.on_fire=len(rows)%5==0;p.disguised=len(rows)%7==0;p.touching_goo=len(rows)%9==0
   p.pickup_id=3 if len(rows)%4==0 else None
   row=list(p.world_update_snapshot());row[3]=row[4]=0
   packet=WorldUpdate();packet.loop_count=0;packet[p.id]=tuple(row)
   fields=','.join(map(str,[p.tool,-1 if p.pickup_id is None else p.pickup_id,int(p.on_fire),int(p.disguised),int(p.touching_goo)]))
   rows.append((group,group,op,arg,fields,bytes(packet.generate()).hex()))
  emit('equip',f'{group},{[0,66,67,69][group]},1');emit('negotiate',str(group%3));emit('rtt',bits(45))
  for tick in range(130):
   emit('receive',','.join(map(str,[100+tick,(tick%16)*16,tick%256,0,bits(1),bits(0),bits(0)])))
   emit('tick',bits(1/60))
   if tick%7==0:emit('owner','-')
   if tick==70:emit('spawn',','.join(bits(v) for v in (36,36,20)))
 raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
 path=OUT/'player-snapshot.tsv.gz';path.write_bytes(buffer.getvalue())
 (OUT/'player-snapshot-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','source_file_sha256':hashlib.sha256((ROOT/'server/player.py').read_bytes()).hexdigest(),'operations':len(rows),'groups':4,'fixture_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'scope':'Unmodified original Player.world_update_snapshot, flag methods, native connected movement/flight; controlled trusted presentation fields, snapshot header/ping/pong set to zero for row comparison.'},indent=2)+'\n')
 print('player snapshot operations',len(rows),'bytes',path.stat().st_size)
finally:time.monotonic,time.time=original
