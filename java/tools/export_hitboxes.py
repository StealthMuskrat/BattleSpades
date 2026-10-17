"""Export original combat_runtime KV6 bounds without modifying source methods."""
import json,pathlib
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from server import combat_runtime as R
import shared.constants as C
settings=dict(soldier=C.CLASS_SOLDIER,arms_z=C.BODY_PART_ARMS_CROUCH_Z,leg_y=C.BODY_PART_LEG_CROUCH_Y,crouch_torso=sum(R._CROUCH_TORSO,()),crouch_leg=sum(R._CROUCH_LEG,()),corpse=sum(R._CLASSIC_CORPSE_BOUNDS,()))
rows=[('settings',settings)]+[('profile',dict(id=k,**{f'part{i}':sum(v,()) for i,v in enumerate(parts)})) for k,parts in sorted(R._CLASS_HITBOXES.items())]
text='# Source 6216b771282d2d349ccd67ee58c378f035c83553; export_hitboxes.py\n'
for kind,fields in rows:text+='\n[['+kind+']]\n'+''.join(k+' = '+json.dumps(v)+'\n' for k,v in fields.items())
(ROOT/'java/src/main/resources/net/battlespades/catalog/hitboxes.toml').write_text(text)
print(len(R._CLASS_HITBOXES),'original class bounds')
