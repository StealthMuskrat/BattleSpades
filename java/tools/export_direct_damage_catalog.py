"""Immutable original direct-hit victim/equipment policy data."""
import json,pathlib
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
import shared.constants as C
from server import hardcore as H
from server.game_constants import MELEE_RANGE
fields=dict(mounted_gun_interval=C.MG_DEPLOYED_SHOOT_INTERVAL,melee_range=MELEE_RANGE,hardcore_classes=sorted(H.BY_CLASS),heavy=C.CLASS_SOLDIER,headshot_kill=C.KILL.HEADSHOT_KILL,entity_part=C.PART_ENTITY1,shield_tool=C.RIOTSHIELD_TOOL,shield_absorption=C.RIOTSHIELD_DAMAGE_ABSORPTION_PERCENT,shield_knockback=C.RIOTSHIELD_KNOCKBACK)
rows=[('settings',fields)]+[('pack',dict(id=k,damage=v.get(C.JETPACK_DAMAGE_MULTIPLIER,1.))) for k,v in sorted(C.JETPACK_PROPERTIES.items())]
text='# Source 6216b771282d2d349ccd67ee58c378f035c83553; export_direct_damage_catalog.py\n'
for kind,fields in rows:text+='\n[['+kind+']]\n'+''.join(k+' = '+json.dumps(v)+'\n' for k,v in fields.items())
(ROOT/'java/src/main/resources/net/battlespades/catalog/direct-damage.toml').write_text(text)
