"""Execute the intact BaseMode -> native WorldManager -> RoundLifecycle spawn chain.

Existing frozen spawn layouts are controlled inputs. Only actors, clocks and the
shared random seed are supplied; no spawn policy method is replaced.
"""
import gzip, hashlib, json, logging, pathlib, random, sys
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT = pathlib.Path(__file__).resolve().parents[2]
prepare(ROOT)
from aoslib import vxl
from modes.base_mode import BaseMode
from modes.tdm import TDMMode
from server.world_manager import WorldManager
from server.map_metadata import MapZone, MapMetadata
from server.round_lifecycle import resolve_player_spawn
from server import spawn_selection as S

assert sys.version_info[:2] == (3, 12)
logging.disable(logging.CRITICAL)
OUT = ROOT / 'java/src/test/resources/golden'
layouts = [line.split('\t') for line in gzip.decompress((OUT/'spawn-layouts.tsv.gz').read_bytes()).decode().splitlines()]
rows = []
clock = [100.0]
previous = S.time.monotonic
S.time.monotonic = lambda: clock[0]
try:
    for case in ['flat', 'sea', 'empty']:
        world = WorldManager(NS())
        world.map = vxl.VXL(-1, b'', 0, 2)
        zones, regions = [], {}
        for name, kind, value in layouts:
            if name != case:
                continue
            f = value.split(',')
            if kind == 'box':
                x0, x1, y0, y1, z0, z1, solid = map(int, f)
                for x in range(x0, x1+1):
                    for y in range(y0, y1+1):
                        for z in range(z0, z1+1):
                            if solid:
                                world.map.set_point(x, y, z, True, 0x7f123456)
                            else:
                                world.map.remove_point(x, y, z)
            elif kind == 'zone':
                n = list(map(float, f[2:11]))
                zones.append(MapZone(f[0], int(f[1]), *n[:3], tuple(n[3:]), f[11]))
            elif kind == 'region':
                n = list(map(int, f))
                regions[n[0]] = tuple(n[1:])
        world.map_metadata = MapMetadata(spawn_zones={t:[z for z in zones if z.kind=='spawn' and z.team==t] for t in [2,3]}, base_zones={t:[z for z in zones if z.kind=='base' and z.team==t] for t in [2,3]}, fallback_spawn_regions=regions)
        server = NS(world_manager=world, players={})
        mode = TDMMode.__new__(TDMMode)
        BaseMode.__init__(mode, server)
        server.mode = mode
        random.seed(168)
        for i, team in enumerate([2, 0, 3, 0, 2, 3]):
            clock[0] = 100+i
            actor = NS(id=0, team=team, alive=False, spawned=False, position=(0.,0.,0.), name='Spawn')
            server.players = {0:actor}
            at = resolve_player_spawn(server, actor)
            actor.position = at
            S.record_death(server, actor)
            rows.append((case, str(team), ','.join(float(v).hex() for v in at), random.random().hex()))
finally:
    S.time.monotonic = previous
(OUT/'standard-spawns.tsv').write_text('\n'.join('\t'.join(row) for row in rows)+'\n')
sources = ['modes/base_mode.py','modes/tdm.py','server/world_manager.py','server/spawn_selection.py','server/round_lifecycle.py','aoslib/vxl.pyx']
provenance = {'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','traces':3,'spawns':len(rows),'policy_substitutions':False,'inputs':'Frozen spawn-layouts.tsv.gz; explicit dead actors, monotonic clock and shared seed 168. Intact BaseMode initialization and get_spawn_point, WorldManager including native VXL random positions, final RoundLifecycle resolve and spawn intel death updates.','source_files':{p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in sources},'fixtures':{p:hashlib.sha256((OUT/p).read_bytes()).hexdigest() for p in ['spawn-layouts.tsv.gz','standard-spawns.tsv']}}
(OUT/'standard-spawns-provenance.json').write_text(json.dumps(provenance,indent=2)+'\n')
print('standard-spawns',len(rows),'positions and shared-stream continuations')
