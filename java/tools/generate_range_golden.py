"""Development-only ctypes oracle against vendored ENet C; not production runtime."""
import ctypes as C,hashlib,pathlib,random,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
lib=C.CDLL(sys.argv[1])
class Buffer(C.Structure):_fields_=[('data',C.c_void_p),('dataLength',C.c_size_t)]
lib.enet_range_coder_create.restype=C.c_void_p
lib.enet_range_coder_destroy.argtypes=[C.c_void_p]
lib.enet_range_coder_compress.argtypes=[C.c_void_p,C.POINTER(Buffer),C.c_size_t,C.c_size_t,C.c_void_p,C.c_size_t]
lib.enet_range_coder_compress.restype=C.c_size_t
lib.enet_range_coder_decompress.argtypes=[C.c_void_p,C.c_void_p,C.c_size_t,C.c_void_p,C.c_size_t]
lib.enet_range_coder_decompress.restype=C.c_size_t
rng=random.Random(168);rows=[]
for length in [1,2,4,8,32,128,1024,4096,8192,32768]:
 for kind in ['zero','repeated','random']:
  data=bytes(length) if kind=='zero' else (b'BattleSpades ENet range coding. '*((length+31)//31))[:length] if kind=='repeated' else bytes(rng.randrange(256) for _ in range(length))
  source=C.create_string_buffer(data);out=C.create_string_buffer(len(data)*2+1024);buffer=Buffer(C.cast(source,C.c_void_p),len(data));context=lib.enet_range_coder_create()
  n=lib.enet_range_coder_compress(context,C.byref(buffer),1,len(data),out,len(out));assert n
  coded=out.raw[:n];decoded=C.create_string_buffer(len(data)+1)
  size=lib.enet_range_coder_decompress(context,coded,len(coded),decoded,len(decoded));assert decoded.raw[:size]==data
  lib.enet_range_coder_destroy(context)
  rows.append(f'{kind}/{length}\t{data.hex()}\t{coded.hex()}')
(ROOT/'java/src/test/resources/golden/range.tsv').write_text('\n'.join(rows)+'\n')
print(len(rows),'native ENet compression/decompression golden vectors')
