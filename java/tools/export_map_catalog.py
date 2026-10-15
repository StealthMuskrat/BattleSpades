"""Export original immutable metadata tables; no policy is generated."""
import json
from pathlib import Path
from reference_imports import prepare
ROOT = Path(__file__).resolve().parents[2]
prepare(ROOT)
from server import map_metadata as M
from shared import constants as C
rows = [('settings', dict(legacy_keys=sorted(M._LEGACY_ENVIRONMENT_KEYS),
                          ambient_assets=sorted(M.AMBIENT_SOUND_ASSETS)))]
for name, skybox in sorted(M.STOCK_MAP_SKYBOXES.items()):
    rows.append(('stock', dict(name=name, skybox=skybox,
                              gravity=M._STOCK_MAP_GRAVITY.get(name, 1.0),
                              ambient=M._MAP_AMBIENT_OVERRIDES.get(name, ''))))
for name, color in sorted(C.FOG_COLORS.items()):
    rows.append(('sky', dict(name=name, fog=list(color), ambient=M._SKYBOX_AMBIENTS.get(name, ''))))
for name, teams in sorted(M._STOCK_FALLBACK_SPAWN_REGIONS.items()):
    for team, bounds in sorted(teams.items()):
        rows.append(('region', dict(name=name, team=team, bounds=list(bounds))))
for item_id, name in sorted(C.UGC_TOOL_IMAGES.items()):
    if item_id in C.UGC_ZONE_SIZES:
        rows.append(('zone', dict(item=name, team=int(C.UGC_ENTITY_TEAMS[item_id]), extents=list(C.UGC_ZONE_SIZES[item_id]))))
for mode, playlist in sorted(M._RETAIL_MODE_PLAYLISTS.items()):
    rows.append(('playlist', dict(mode=mode, name=playlist)))
text = '# Source 6216b771282d2d349ccd67ee58c378f035c83553; export_map_catalog.py\n'
for kind, fields in rows:
    text += '\n[[' + kind + ']]\n' + ''.join(key + ' = ' + json.dumps(value, ensure_ascii=False) + '\n' for key, value in fields.items())
path = ROOT / 'java/src/main/resources/net/battlespades/catalog/map-metadata.toml'
path.write_text(text, encoding='utf-8')
print(len(rows), 'map metadata table rows')
