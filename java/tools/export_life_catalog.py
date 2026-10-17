"""Export existing life/inventory constants, never policy implementations."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
from shared import constants as C
from server import game_constants as G, player as P, deployable_inventory as D, kill_feed as K
rows=[('settings',dict(health=G.MAX_HEALTH,weapon=G.DEFAULT_WEAPON_TOOL,multikill_gap=K.MULTIKILL_MAX_TIME_GAP,domination_kills=K.DOMINATION_KILLS,interaction_window=float(C.PLAYER_INTERACTION_EXPIRY_SECONDS),transition_credit_kill=G.KILL_WEAPON,
                      turret_initial=int(C.ROCKET_TURRET_INITIAL_STOCK),turret_max=int(C.ROCKET_TURRET_STOCK),turret_restock=int(C.ROCKET_TURRET_RESTOCK_AMOUNT),
                      disguise_initial=int(C.DISGUISE_INITIAL_STOCK),disguise_max=int(C.DISGUISE_STOCK),disguise_restock=int(C.DISGUISE_RESTOCK_AMOUNT),
                      transitions=[int(C.FORCED_TEAM_CHANGE_KILL),int(C.TEAM_CHANGE_KILL),int(C.CLASS_CHANGE_KILL)],
                      domination_reset=[int(C.FORCED_TEAM_CHANGE_KILL),int(C.TEAM_CHANGE_KILL)],one_hit=sorted(P._ONE_HIT_KILL_TYPES),
                      fall_kill=int(C.KILL.FALL_KILL),zombies=[int(C.CLASS_ZOMBIE),int(C.CLASS_FAST_ZOMBIE),int(C.CLASS_JUMP_ZOMBIE)],weapon_tools=sorted(G.WEAPON_TOOL_IDS)))]
for tool,p in G.WEAPON_PROFILES.items():
    rows.append(('weapon',dict(tool=tool,clip=p.clip_size,reserve=p.reserve_ammo,initial=P.Player._initial_reserve(p),restock=p.restock_amount,reload=p.reload_time,clip_reload=p.clip_reload)))
for cid,(start,maximum) in sorted(C.CLASS_BLOCKS.items()):rows.append(('blocks',dict(id=int(cid),start=int(start),maximum=int(maximum))))
for tool,row in sorted(P.ORIENTED_STOCK_AMMO.items()):rows.append(('oriented',dict(tool=tool,stock=list(row))))
for tool,r in sorted(D.STOCK_RULES.items()):rows.append(('deployable',dict(tool=tool,initial=r.initial,maximum=r.maximum,restock=r.restock,interval=r.interval)))
for tool,interval in sorted(D._INTERVALS.items()):rows.append(('cadence',dict(tool=tool,interval=interval)))
for pack,values in sorted(P._JETPACK_PROPERTIES.items()):rows.append(('pack',dict(id=int(pack),fuel=float(values.get(1,100)),start_delay=float(values.get(0,.25)),activation_cost=float(values.get(2,10)),refill=float(values.get(3,10)),drain=float(values.get(4,75)),damage_delay=float(values.get(6,2)))))
for tool in sorted(P.Player._RELOADING_LAUNCHERS):
    profile=G.WEAPON_CATALOG.get(tool)
    if profile is not None:rows.append(('launcher',dict(tool=tool,clip=profile.clip_size,reload=profile.reload_time,interval=profile.fire_interval,cycles=tool in P.Player._CLIP_RELOAD_LAUNCHERS)))
text='# Source 6216b771282d2d349ccd67ee58c378f035c83553; export_life_catalog.py\n'
for kind,fields in rows:text+='\n[['+kind+']]\n'+''.join(k+' = '+json.dumps(v)+'\n' for k,v in fields.items())
(ROOT/'java/src/main/resources/net/battlespades/catalog/life.toml').write_text(text)
print(len(rows),'life catalog rows')
