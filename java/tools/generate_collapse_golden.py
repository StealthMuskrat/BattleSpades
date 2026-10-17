"""Unchanged original native and fallback collapse policies on controlled topology/budgets."""
import gzip,hashlib,io,json,pathlib,random
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from aoslib import vxl
from server.world_manager import WorldManager
encode=lambda cells:';'.join(','.join(map(str,c)) for c in cells) or '-'
rng=random.Random(25917);native=vxl.VXL(-1,b'',0,2);rows=[];last=[];floating=0
for case in range(512):
 for p in last:native.remove_point_nochecks(*p)
 origin=[(0,0,0),(508,508,235),(100,100,100),(100,100,235)][case%4]
 solids={(origin[0]+x,origin[1]+y,origin[2]+z) for x in range(4) for y in range(4) for z in range(5) if rng.random()<.43}
 if case%7==0:solids.update((origin[0]+2,origin[1]+2,z) for z in range(origin[2],240))
 if case==511:solids={(100+x,100+y,100) for x in range(84) for y in range(100)}
 removed=[(origin[0]+rng.randrange(-1,5),origin[1]+rng.randrange(-1,5),origin[2]+rng.randrange(-1,6)) for _ in range(8)]
 if case==511:removed=[(99,100,100)]
 for p in removed:solids.discard(p)
 for p in sorted(solids):native.set_point(*p,0x80406080)
 last=sorted(solids);budget=[0,1,5,17,18,19,35,36,37,100,360,180000000][case%12]
 if case==511:budget=180000000
 manager=WorldManager.__new__(WorldManager);manager.map=native;manager.COLLAPSE_WORK_BUDGET=budget
 chunks=manager.find_unsupported_chunks(removed)
 # Force only the existing fallback dispatch with controlled solidity IO.
 fallback=WorldManager.__new__(WorldManager);fallback.map=object();fallback.COLLAPSE_WORK_BUDGET=budget;fallback.get_solid=lambda x,y,z:(x,y,z) in solids
 expected=fallback.find_unsupported_chunks(removed)
 assert chunks==expected,(case,budget,chunks,expected)
 floating+=sum(map(len,chunks));rows.append((case,budget,encode(sorted(solids)),encode(removed),'|'.join(encode(chunk) for chunk in chunks) or '-'))
raw=('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'collapse.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'collapse-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/world_manager.py','aoslib/vxl.pyx']},cases=len(rows),floating_cells=floating,fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),method_substitutions=False,scope='Unchanged native VXL collapse policy and unchanged WorldManager fallback, with controlled topology, removed-cell order and work budget. Exact chunk and DFS cell order, bounds, 18-connectivity, grounded/budget-safe branches and an 8400-cell component; does not prove mutation, terrain repair, scores or supported-client animation.'),indent=2)+'\n');print('Collapse cases',len(rows),'floating cells',floating,'compressed bytes',path.stat().st_size)
