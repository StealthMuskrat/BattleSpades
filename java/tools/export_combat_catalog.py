"""Export unchanged original weapon profile data and tool classifications."""
import dataclasses,json,pathlib
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from server import game_constants as G,player as P
from server.combat_runtime import RETAIL_ACCURACY_SPREAD,RETAIL_ACCURACY_ZOOM
rows=[('settings',dict(default_weapon=G.DEFAULT_WEAPON_TOOL,spade=G.SPADE_PROFILE.tool_id,
 raw_tools=sorted(G.BLOCK_TOOL_IDS|G.SPADE_TOOL_IDS|G.GRENADE_TOOL_IDS|G.WEAPON_TOOL_IDS),spades=sorted(G.SPADE_TOOL_IDS),
 fire_grace=P.FIRE_RATE_GRACE,reload_grace=P.RELOAD_FIRE_GRACE_SECONDS,reload_fraction=P.RELOAD_FIRE_GRACE_FRACTION))]
rows += [('weapon',dataclasses.asdict(profile)) for id,profile in sorted(G.WEAPON_CATALOG.items())]
rows += [('accuracy',dict(tool=tool,minimum=row[0],maximum=row[1],increase=row[2],reduction=row[3])) for tool,row in sorted(RETAIL_ACCURACY_SPREAD.items())]
rows += [('zoom',dict(tool=tool,accuracy=value)) for tool,value in sorted(RETAIL_ACCURACY_ZOOM.items())]
text='# Source 6216b771282d2d349ccd67ee58c378f035c83553; export_combat_catalog.py\n'
for kind,fields in rows:text+='\n[['+kind+']]\n'+''.join(key+' = '+json.dumps(value)+'\n' for key,value in fields.items())
path=ROOT/'java/src/main/resources/net/battlespades/catalog/combat.toml';path.write_text(text)
print(len(G.WEAPON_CATALOG),'original weapon profiles',len(RETAIL_ACCURACY_SPREAD),'bloom curves')
