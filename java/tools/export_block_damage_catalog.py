"""Export original terrain damage tables without replacing source policies."""
import pathlib,sys
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
sys.path.insert(0,str(ROOT/'java/build/reference-python'))
from server import block_damage_model as model
from server import combat_runtime as combat
from server.dig_profiles import MELEE_DIG_PROFILES,DEFAULT_MELEE_PROFILE
import shared.constants as C
rows=['# SPDX-License-Identifier: AGPL-3.0-or-later','# Source: 6216b771282d2d349ccd67ee58c378f035c83553','[[settings]]',f'max_damageable_z = {model.MAX_DAMAGEABLE_Z}',f'radius_extra = {float(C.RADIUS_BLOCK_DAMAGE_RANDOM_EXTRA)!r}','']
for id,(kind,parameter) in sorted(model.FOOTPRINTS.items()):rows.extend(['[[footprint]]',f'id = {id}',f'kind = "{kind}"',f'parameter = {float(parameter)!r}',''])
rows.extend(['[[combat_settings]]',f'melee_range = {float(combat.MELEE_RANGE)!r}',f'eye_slack = {float(combat.MELEE_EYE_SLACK)!r}',f'cell_half_diagonal = {float(combat._CELL_HALF_DIAGONAL)!r}',f'kill_damage = {float(combat.CombatSystem._BLOCK_KILL_DAMAGE)!r}',f'ugc_superspade = {int(C.UGC_SUPERSPADE_TOOL)}',f'ugc_secondary_type = {int(C.UGC_SUPERSPADE_SECONDARY_DAMAGE)}',f'ugc_secondary_damage = {float(C.UGC_SUPERSPADE_SECONDARY_DAMAGE_AMOUNT)!r}',''])
for tool,(type,amount,pattern) in sorted({-1:DEFAULT_MELEE_PROFILE,**MELEE_DIG_PROFILES}.items()):rows.extend(['[[melee]]',f'tool = {tool}',f'type = {type}',f'amount = {float(amount)!r}',f'pattern = "{pattern}"',''])
for type,sound in sorted(combat._BLOCK_HIT_SOUND_BY_DAMAGE.items()):rows.extend(['[[sound]]',f'type = {type}',f'id = {sound}',''])
(ROOT/'java/src/main/resources/net/battlespades/catalog/block-damage.toml').write_text('\n'.join(rows))
print('Exported',len(model.FOOTPRINTS),'source terrain footprints')
