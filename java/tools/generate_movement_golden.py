"""Execute the original compiled world.Player, never Java, for deterministic tick traces.

Run with the reference venv after setup.py build_ext --inplace. All map cells,
commands and peer ordering are included so Java replays exactly the same inputs.
"""
import gzip
import hashlib
import json
import pathlib
import random
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from aoslib import world, vxl
from shared import constants as C

assert pathlib.Path(world.__file__).suffix != ".py", "requires compiled source oracle"
OUT = ROOT / "java/src/test/resources/golden"
rows = []
cases = []


def begin(name, class_id=0, gravity=1):
    global label, voxels, simulation, player
    label = name
    cases.append(name)
    voxels = vxl.VXL(-1, b"", 0, 2)
    simulation = world.World(voxels)
    simulation.set_gravity(gravity)
    player = world.Player(simulation)
    setters = {
        "accel_multiplier": "ACCEL_MULTIPLIER", "sprint_multiplier": "SPRINT_MULTIPLIER",
        "crouch_sneak_multiplier": "CROUCH_SNEAK_MULTIPLIER", "jump_multiplier": "JUMP_MULTIPLIER",
        "water_friction": "WATER_FRICTION", "falling_damage_min_distance": "FALLING_DAMAGE_MIN_DISTANCE",
        "falling_damage_max_distance": "FALLING_DAMAGE_MAX_DISTANCE", "falling_damage_max_damage": "FALLING_DAMAGE_MAX_DAMAGE",
        "fall_on_water_damage_multiplier": "FALL_ON_WATER_DAMAGE_MULTIPLIER", "can_sprint_uphill": "CAN_SPRINT_UPHILL",
    }
    for setter, table in setters.items():
        getattr(player, "set_class_" + setter)(getattr(C, "CLASS_" + table)[class_id])
    emit("new", [class_id, gravity])


def emit(command, args=(), result=0):
    p, v = player.position, player.velocity
    values = [p.x, p.y, p.z, v.x, v.y, v.z]
    flags = [player.airborne, player.wade, player.crouch, player.jump, player.jump_this_frame]
    rows.append("\t".join([label, command, json.dumps(args, separators=(",", ":")), str(result)]
                          + [float(n).hex() for n in values] + [str(int(n)) for n in flags]))


def command(op, args):
    if op == "position": player.set_position(*args)
    elif op == "velocity": player.set_velocity(*args)
    elif op == "orientation": player.set_orientation(args)
    elif op == "input":
        player.set_walk(*args[:4])
        player.jump, player.sneak, player.sprint = args[4:]
    elif op == "flight":
        pack, active, passive, hover, chute, canopy, accel, gravity, floor = args
        player.jetpack = C.NO_JETPACK + pack
        player.jetpack_active, player.jetpack_passive, player.hover = active, passive, hover
        player.parachute, player.parachute_active = chute, canopy
        player.engineer_flight_accel, player.parachute_gravity_scale = accel, gravity
        player.parachute_free_fall_floor = floor
    elif op == "crouch": player.set_crouch(args[0], args[1:], len(args)-1)
    elif op == "dead": player.set_dead(args[0])
    elif op == "exploded": player.set_exploded(args[0])
    elif op == "burdened": player.burdened = args[0]
    elif op == "slowdown": player.set_climb_slowdown(args[0])
    elif op == "lock": player.set_locked_to_box(args)
    elif op == "unlock": player.clear_locked_to_box()
    elif op == "cell":
        voxels.set_point(*args[:3], bool(args[3]), 0x7f123456)
    else: raise ValueError(op)
    emit(op, args)


def ticks(count, peers=(), step=1/60):
    for _ in range(count): emit("tick", [step, *peers], player.update(step, peers))


def box(x1, y1, z1, x2, y2, z2):
    # Box command compresses fixture setup without concealing the actual cells.
    for x in range(x1, x2+1):
        for y in range(y1, y2+1):
            for z in range(z1, z2+1): voxels.set_point(x, y, z, True, 0x7f123456)
    emit("box", [x1,y1,z1,x2,y2,z2])


for cid in range(18):
    begin(f"class-{cid}", cid)
    box(95,95,62,125,110,65)
    command("position", [100.5,100.5,59.75])
    for controls in [[1,0,0,0,0,0,0], [1,0,0,1,0,0,1], [1,1,1,1,0,0,0], [0,1,1,0,1,0,0], [1,0,0,0,0,1,0]]:
        command("input", controls); ticks(24)
    command("crouch", [True]); ticks(12)
    command("crouch", [False]); command("burdened", [True]); ticks(12)

# Step/glide, wall, head collision and auto-crouch contact planes.
for height in [1,2,3]:
    for sprint in [False,True]:
        begin(f"step-{height}-{sprint}",5 if sprint else 0)
        box(95,95,62,110,105,65); box(102,98,62-height,107,102,61)
        command("position", [100.5,100.5,59.75]);command("slowdown",[0.5])
        command("input",[1,0,0,0,0,0,sprint]);ticks(100)

for crouching in [False,True]:
    begin(f"gap-{crouching}")
    box(98,98,62,108,103,65); box(102,98,58,107,102,59)
    command("position",[100.5,100.5,59.75]);command("crouch",[crouching])
    command("input",[1,0,0,0,0,0,0]);ticks(90);command("crouch",[False]);ticks(10)

for water in [False,True]:
    for speed in [0,0.3,1]:
        begin(f"fall-{water}-{speed}")
        floor=238 if water else 62
        box(95,95,floor,125,115,floor)
        command("position",[100.5,100.5,floor-45]);command("velocity",[0.3,0.1,speed]);ticks(160)

for pack in range(1,5):
    for passive in [False,True]:
        for gravity in [1,26/64]:
            begin(f"flight-{pack}-{passive}-{gravity}",3,gravity)
            command("position",[100.5,100.5,130]);ticks(1)
            command("flight",[pack,True,passive,False,False,False,0.1,0.05,False])
            command("input",[1,0,0,1,1,0,0]);ticks(60)

for floor in [False,True]:
    for passive in [False,True]:
        begin(f"canopy-{floor}-{passive}")
        command("position",[100.5,100.5,100]);ticks(1)
        command("flight",[3,False,passive,False,True,True,0.4,0.15625,floor]);ticks(120)

for crouching in [False,True]:
    begin(f"hover-{crouching}")
    command("position",[100.5,100.5,100])
    command("flight",[4,False,False,True,False,False,0.1,0.05,False])
    command("crouch",[crouching]);ticks(60)

for dead in [False,True]:
    begin(f"corpse-{dead}")
    command("position",[100.5,100.5,100]);command("dead",[dead])
    command("flight",[3,False,False,False,False,False,0.1,0.05,False]);ticks(120)

# Ordered and axis-degenerate peer impulses, vertical contacts, crouch clearance.
for i, peers in enumerate([[[100.6,100.5,100]], [[100.7,100.8,100],[100.2,100.3,100]],
                          [[100.5,100.5,102.5]], [[100.5,100.5,97.5]], [[100.5,100.5,100,1.8]]]):
    begin(f"peer-{i}");command("position",[100.5,100.5,100]);ticks(4,peers)
    command("crouch",[True,*peers]);command("crouch",[False,*peers]);ticks(8,peers)
    command("exploded",[True]);ticks(2,peers)

# Edge and negative-z truncation cases, ledge-hole drift, lock-box post-fall ordering.
for i, pos in enumerate([[0.44,100.5,60],[511.56,100.5,60],[100.5,0.44,60],
                         [100.5,511.56,60],[100.5,100.5,-0.1],[100.5,100.5,-1.1],
                         [100.5,100.5,237.75],[100.5,100.5,241]]):
    begin(f"edge-{i}");command("position",pos);command("velocity",[-0.2,0.2,0.1]);ticks(5)
begin("ledge")
box(99,99,62,99,102,63);command("position",[100.35,100.5,59.75]);ticks(50)
begin("lock")
command("position",[100.5,100.5,100]);command("lock",[100,100,99,101,101,101])
command("input",[1,0,0,1,0,0,0]);ticks(90);command("unlock",[]);ticks(10)

for cid in [0,1,2,5,13,14,15]:
    for distance in [9.9,10.1,30.1,45]:
        begin(f"damage-{cid}-{distance}",cid)
        box(95,95,62,110,110,65)
        command("position",[100.5,100.5,59.75-distance]);ticks(160)

# Exact voxel boundaries and blocked/expanding crouch bodies.
for height in [0,1,2]:
    begin(f"ceiling-{height}")
    box(98,98,62,108,103,65);box(98,98,56+height,108,103,56+height)
    command("position",[100.5,100.5,59.75]);command("input",[1,0,0,0,1,0,0]);ticks(60)
    command("crouch",[True]);command("crouch",[False]);ticks(10)

for offset in [0,0.0000001,-0.0000001,0.0087,0.0088]:
    begin(f"contact-{offset}")
    box(98,98,62,110,105,65);box(102,98,60,104,104,61)
    command("position",[100.55,100.55,59.75+offset]);command("velocity",[0.1,0,0.4]);ticks(90)

# Dead/exploded peers do not resolve, active pack launch precedence, and zero-XY view.
begin("vertical-view")
command("position",[100.5,100.5,100]);command("orientation",[0,0,1]);command("input",[1,0,1,0,0,0,0]);ticks(20)
for hover in [False,True]:
    begin(f"grounded-pack-{hover}")
    box(98,98,62,110,105,65);command("position",[100.5,100.5,59.75])
    command("flight",[3,True,False,hover,False,False,0.4,0.05,False]);command("input",[1,0,0,0,1,0,0]);ticks(90)

# Seeded changing orientations and controls exercise non-axis float normalisation.
rng=random.Random(168)
for case in range(8):
    begin(f"random-{case}",case)
    box(94,94,80,115,115,83);box(103,97,78,104,105,79)
    command("position",[100.5,100.5,77.75])
    for segment in range(12):
        command("orientation",[rng.uniform(-1,1),rng.uniform(-1,1),rng.uniform(-1,1)])
        command("input",[rng.randrange(2) for _ in range(7)])
        if segment%3==0:command("crouch",[bool(rng.randrange(2))])
        ticks(12,step=[1/60,1/30,0.007][segment%3])

path=OUT/"movement.tsv.gz"
path.write_bytes(gzip.compress(("\n".join(rows)+"\n").encode(),mtime=0))
(OUT/"movement-provenance.json").write_text(json.dumps({
    "source_commit":"6216b771282d2d349ccd67ee58c378f035c83553",
    "oracle":"compiled aoslib.world.Player from aoslib/world.pyx and compiled aoslib.vxl.VXL",
    "generator":"java/tools/generate_movement_golden.py", "cases":cases,
    "rows":len(rows), "ticks":sum("\ttick\t" in r for r in rows),
    "sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
    "comparison":"exact double bits for exposed position/velocity; exact flags and return code",
    "limitations":"development source oracle, not a supported game-client run; private fall/climb accumulators observed via subsequent updates"
},indent=2)+"\n")
print(len(cases),"cases;",len(rows),"commands and",sum("\ttick\t" in r for r in rows),"source ticks")
