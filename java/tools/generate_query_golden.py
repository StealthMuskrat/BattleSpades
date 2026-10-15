"""Execute compiled original world queries; deterministic independent expected results."""
import gzip
import itertools
import pathlib
import random
import sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from aoslib import world,vxl
from shared.glm import Vector3
OUT=ROOT/'java/src/test/resources/golden'
map=vxl.VXL(-1,b'',0,2)
cells={(x,y,62) for x in range(96,109) for y in range(96,109)}
cells|={(104,y,z) for y in range(98,104) for z in range(55,62)}
for cell in cells:map.set_point(*cell,True,0x7f123456)
simulation=world.World(map)
rows=[]
def nums(items):return ','.join(float(v).hex() for v in items)
def ray(p,d,length,water):
    hit=simulation.hitscan_accurate(p,d,length,water)
    expected='none' if hit is None else nums([*hit[0].get(),*hit[1].get(),hit[2]])
    rows.append('\t'.join(['ray',nums([*p,*d,length,water]),expected]))
directions=list(itertools.product([-1,0,1],repeat=3))
for p in [[100.5,100.5,59.75],[104,100,61],[104.000001,100,61],[103.999999,100,61],
          [100.5,100.5,238.5],[-1,100,60],[513,100,60],[100,100,-0.5]]:
    for d in directions:
        for water in [False,True]:ray(p,d,128,water)
rng=random.Random(168)
for i in range(300):
    p=[rng.uniform(94,110),rng.uniform(94,110),rng.uniform(50,65)]
    d=[rng.uniform(-1,1) for _ in range(3)]
    ray(p,d,[0,0.1,10,128][i%4],i%2==0)
for i in range(100):
    a=tuple(rng.randrange(-5,8) for _ in range(3));b=tuple(rng.randrange(-5,8) for _ in range(3))
    line=world.cube_line(*a,*b)
    rows.append('\t'.join(['line',nums([*a,*b]),';'.join(nums(c) for c in line)]))
for face in range(-1,8):
    c=(12,-2,63)
    rows.append('\t'.join(['face',nums([*c,face]),nums([*simulation.get_block_face_center_position(c,face).get(),*world.get_next_cube(c,face).get()])]))
for i in range(100):
    p=[rng.uniform(-10,10) for _ in range(3)];d=[rng.uniform(-1,1) for _ in range(3)];t=[rng.uniform(-10,10) for _ in range(3)]
    tolerance=rng.uniform(0,10)
    rows.append('\t'.join(['center',nums([*p,*d,*t,tolerance]),str(int(world.is_centered(*p,*d,*t,tolerance)))]))
(OUT/'query-cells.tsv').write_text('\n'.join(','.join(str(n) for n in c) for c in sorted(cells))+'\n')
(OUT/'queries.tsv.gz').write_bytes(gzip.compress(('\n'.join(rows)+'\n').encode(),mtime=0))
print(len(rows),'source query vectors;',len(cells),'explicit cells plus source floor239')
