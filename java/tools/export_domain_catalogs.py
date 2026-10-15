"""Export original immutable data tables, not implementation logic, for the Java port."""
import json,pathlib,sys
ROOT=pathlib.Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
from shared import constants as C
from server import class_data,mode_data
from server.game_rules import RULE_DEFINITIONS
from server.class_selection import _allowed_prefabs,_allowed_ugc_tools
from modes.unofficial.catalog import ALIASES
OUT=ROOT/'java/src/main/resources/net/battlespades/catalog'
def value(v):return json.dumps(v,ensure_ascii=False,separators=(',',':'))
def write(name,rows):
 text='# Source data: 6216b771282d2d349ccd67ee58c378f035c83553; regenerate with export_domain_catalogs.py\n'
 for kind,fields in rows:
  text+='\n[['+kind+']]\n'+''.join(k+' = '+value(v)+'\n' for k,v in fields.items())
 (OUT/name).write_text(text)
write('rules.toml',[('rule',dict(key=r.key,category=r.category,default=r.default,choices=list(r.choices),modes=list(r.modes),tool=-1 if r.tool_id is None else r.tool_id,class_id=-1 if r.class_id is None else r.class_id,visible=r.menu_visible)) for r in RULE_DEFINITIONS.values()])
write('classes.toml',[('class',dict(id=i,slots=[list(C.CLASS_ITEMS[i].get(slot,())) for slot in range(4)],common=list(C.CLASS_ITEMS[i].get(C.CLASS_COMMON,())),movement=[getattr(C, name)[i] for name in ['CLASS_ACCEL_MULTIPLIER','CLASS_SPRINT_MULTIPLIER','CLASS_CROUCH_SNEAK_MULTIPLIER','CLASS_JUMP_MULTIPLIER','CLASS_WATER_FRICTION','CLASS_FALLING_DAMAGE_MIN_DISTANCE','CLASS_FALLING_DAMAGE_MAX_DISTANCE','CLASS_FALLING_DAMAGE_MAX_DAMAGE','CLASS_FALL_ON_WATER_DAMAGE_MULTIPLIER']],uphill=bool(C.CLASS_CAN_SPRINT_UPHILL[i]),prefabs=sorted(_allowed_prefabs(i)),ugc=sorted(_allowed_ugc_tools(i)),damage=class_data.get_damage(i).damage_multiplier,headshot=class_data.get_damage(i).headshot_multiplier)) for i in sorted(class_data.CLASS_IDS)])
rows=[]
for m in mode_data.MODES.values():
 rows.append(('mode',dict(code=m.code,id=m.mode_id,title=m.title_string,description=m.description_string,info=[m.infographic1,m.infographic2,m.infographic3],score=m.default_score_limit,time=m.default_time_limit,classic=m.classic,mafia=m.mafia,classes=list(m.allowed_classes))))
for alias,target in sorted(ALIASES.items()):rows.append(('alias',dict(name=alias,target=target)))
write('modes.toml',rows)
print(len(RULE_DEFINITIONS),'rules;',len(class_data.CLASS_IDS),'classes;',len(mode_data.MODES),'mode metadata entries;',len(ALIASES),'community aliases')
