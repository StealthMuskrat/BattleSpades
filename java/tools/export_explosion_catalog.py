"""Export unchanged original stock explosion constants/handlers, without substituting policy."""
import pathlib,ast
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from server import weapons_retail as retail
rows=['# Unchanged source server/weapons_retail.py, reference 6216b771282d2d349ccd67ee58c378f035c83553','[[settings]]',f'self_reduction = {retail.SELF_EXPLOSION_DAMAGE_REDUCTION!r}',f'team_reduction = {retail.TEAM_EXPLOSION_DAMAGE_REDUCTION!r}',f'neutral_team = {retail.TEAM_NEUTRAL}',f'body_standing = {retail.BODY_OFFSET_STANDING!r}',f'body_crouching = {retail.BODY_OFFSET_CROUCHING!r}',f'ray_start = {retail.LOS_RAY_START_FRACTION!r}',f'weights = {list(retail.LINE_OF_SIGHT_WEIGHTS)!r}',f'offsets_standing = {list(retail.LOS_OFFSETS_STANDING)!r}',f'offsets_crouching = {list(retail.LOS_OFFSETS_CROUCHING)!r}']
# Literal-only source extraction avoids importing the unrelated full launcher.
module=ast.parse((ROOT/'server/main.py').read_text());assignments={node.targets[0].id:node.value for node in module.body if isinstance(node,ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0],ast.Name)}
stock_types=ast.literal_eval(assignments['STOCK_EXPLOSION_DAMAGE_TYPES'].args[0]);prediction=ast.literal_eval(assignments['_SNOWBALL_PREDICTION_OBSERVED_FRAMES'])
player=ast.parse((ROOT/'server/player.py').read_text());limit=next(ast.literal_eval(n.value) for n in player.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='PENDING_VELOCITY_IMPULSE_LIMIT' for t in n.targets))
rows += [f'stock_damage_types = {list(stock_types)!r}',f'prediction_frames = {prediction}',f'impulse_limit = {limit}']
for spec in retail.RETAIL_EXPLOSIONS:
 rows+=['','[[stock]]',f'name = "{spec.name}"',f'kill_type = {spec.kill_type}',f'radius = {spec.radius!r}',f'knockback_min = {spec.knockback_min!r}',f'knockback_max = {spec.knockback_max!r}',f'damage = {spec.damage!r}',f'classic = {str(spec.classic).lower()}']
p=ROOT/'java/src/main/resources/net/battlespades/catalog/explosions.toml';p.write_text('\n'.join(rows)+'\n');print('Stock explosion handlers',len(retail.RETAIL_EXPLOSIONS))
