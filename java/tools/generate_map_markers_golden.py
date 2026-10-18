"""Original ServerVXL cleanup and WorldManager load-derived flare restoration.

Native/Python are development oracles only. No source method is replaced.
"""
import gzip, hashlib, io, json, pathlib, struct
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT/'java/src/test/resources/golden'
prepare(ROOT)
from aoslib.vxl import find_marker_voxels
from server.runtime_vxl import ServerVXL
from server.world_manager import WorldManager

rows=[]; inputs={}; counts={}; state_count=0
def row(*items): rows.append('\t'.join(map(str,items)))
def word(color): return struct.pack('<I',color)
def column(kind, color):
    if kind==0: return bytes([0,90,90,0])+word(color)
    if kind==1: return bytes([2,100,100,0])+word(color)+bytes([2,102,102,101])+word(color)+bytes([0,239,239,103])+word(0x80334455)
    if kind==2: return bytes([3,200,200,0])+word(color)+word(0x8000ff00)+bytes([0,220,220,203])+word(0x8000ff00)
    return bytes([0,0,2,0])+word(color)+word(0x800000ff)+word(0x80112233)

def observe(wm, points):
    global state_count
    for x,y,z in sorted(points):
        row('P',x,y,z,int(wm.map.get_solid(x,y,z)),wm.map.get_color(x,y,z),int(wm.map.has_explicit_color(x,y,z)))
        state_count+=1
    columns=sorted({(x,y) for x,y,z in points})
    for x,y in columns:
        # Native serialization includes the coordinate header.
        row('C',x,y,bytes(wm.map.serialize_columns([(x,y)]))[8:].hex())
    snapshot=wm.capture_map_sync(wm.dirty_columns,full=False)
    row('D',len(snapshot.columns),hashlib.sha256(snapshot.overlay_data).hexdigest(),wm.topology_version,len(wm.dirty_columns),len(wm._air_override_masks))
    row('S',';'.join(f'{x},{y},{z},{rgb}' for (x,y,z),rgb in wm.static_light_cells.items()) or '-')
    if wm.map_name in ('Training','Atlantis'):
        full=wm.capture_map_sync(wm.dirty_columns,full=True).build_chunks()
        import zlib
        row('F',hashlib.sha256(zlib.decompress(b''.join(full))).hexdigest())

colors=[0x8000ff00,0xff05ff05,0x000000fa,0x120000ff,0x800028be,0x8000be28,0x8000be2a,0x80ff0000]
cases=[]
for index in range(64):
    edge=[1,2,4,1][index%4]
    raw=b''.join(column((index//8+j)%4,colors[(index+j)%len(colors)]) for j in range(edge*edge))
    cases.append((f'synthetic-{index}',raw,'retail',raw.hex()))
for name in ['20thCenturyTown','AncientEgypt','ArcticBase','Frontier','GreatWall','MayanJungle','SpookyMansion','TheColosseum','TokyoNeon','Training','Atlantis']:
    raw=(ROOT/'maps'/f'{name}.vxl').read_bytes()
    cases.append((name,raw,'auto','@'+name))
# An explicitly authored classic map with chroma words must bypass cleanup.
raw=(bytes([0,63,63,0])+word(0x8000ff00))*(512*512)
cases.append(('classic-markers',raw,'classic64','!classic'))
for name,raw,fmt,spec in cases:
    inputs[name]=hashlib.sha256(raw).hexdigest()
    wm=WorldManager(NS());wm.map=ServerVXL(None,raw,len(raw),source_format=fmt);wm.map_raw_bytes=raw;wm.map_name=name
    row('E',name,fmt,spec,wm.map.source_z_shift)
    markers=wm.map.retail_marker_families;counts[name]=len(markers)
    for marker in markers: row('K',*marker)
    points=set()
    candidates=list(find_marker_voxels(raw)) if fmt!='classic64' else [(0,0,63,0x8000ff00),(511,511,63,0x8000ff00)]
    for x,y,z,color in candidates:
        for dz in [-2,-1,0,1,2]:
            zz=z+wm.map.source_z_shift+dz
            if 0<=zz<240: points.add((x,y,zz))
    if name=='Atlantis': points.update([(117,97,192),(406,381,192)])
    if name.startswith('synthetic'): points.update([(0,0,10),(511,511,239)])
    observe(wm,points)
    # Repeated integer/fractional restores and invalid coordinates. They do not
    # emit dynamic dirty columns, navigation versions, or reconnect air masks.
    restores=[(x+.75,y+.25,float(z),0x123456 if family==0 else 0xabcdef) for x,y,z,family in markers]
    restores.extend([(-.75,.75,10.75,0x334455),(511.9,511.9,239.9,0x778899),(-1.,0.,10.,0x112233),(512.,0.,0.,0x112233),(0.,0.,240.,0x112233),(0.,0.,10.,0xabcdef)])
    events=[]
    def failing(x,y,z,rgb):
        events.append(f'F:{x},{y},{z},{rgb}')
        assert wm.static_light_cells[(x,y,z)]==rgb
        raise RuntimeError('controlled observer fault')
    def good(x,y,z,rgb):events.append(f'G:{x},{y},{z},{rgb}')
    wm.static_light_listeners=[failing,good]
    for x,y,z,rgb in restores:
        events.clear();ok=wm.restore_static_light_block(x,y,z,((rgb>>16)&255,(rgb>>8)&255,rgb&255))
        row('L',x,y,z,rgb,int(ok),';'.join(events) or '-')
    observe(wm,points)
    # A load restore clears all per-cell health/damage/shade/history state,
    # even when it paints an existing solid. Compare its topology delta.
    wm.set_block(0,0,10,True,0x406080,health=9.)
    wm.apply_block_damage(0,0,10,.25)
    before=wm.topology_version
    wm.restore_static_light_block(0,0,10,(1,2,3))
    row('H',wm.initial_block_health(0,0,10),wm.block_damage.get((0,0,10),0.),int((0,0,10) in wm.block_shade),int((0,0,10) in wm.block_hits),wm.topology_version-before)
    row('Z')
raw=('\n'.join(rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
p=OUT/'map-markers.tsv.gz';p.write_bytes(buffer.getvalue())
(OUT/'map-markers-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',cases=len(cases),point_observations=state_count,removed_markers=counts,input_sha256=inputs,fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ['server/runtime_vxl.py','server/world_manager.py','aoslib/vxl.pyx']},method_substitutions=False,scope='Actual original ServerVXL and native column serialization, original static-light restore and delta snapshot capture. Controlled inputs and observer IO. Includes all ten stock marker maps, Atlantis art, 64 synthetic maps and explicit classic64 bypass. Full map-resource assembly and live server remain separate work.'),indent=2)+'\n')
print(f'Map marker cases {len(cases)}; point observations {state_count}; fixture bytes {p.stat().st_size}')
