"""Compile unchanged original server methods, including original decorators, without launching server.main."""
import ast,logging,time
from types import SimpleNamespace
from server import achievements
from server.combat_runtime import get_combat_system
from server.bot_ai.messages import StimulusKind

def methods(root):
 source=ast.parse((root/'server/main.py').read_text());cls=next(n for n in source.body if isinstance(n,ast.ClassDef) and n.name=='BattleSpadesServer');names=['_apply_blast','_apply_blast_terrain','_build_entity_ctx','_record_canonical_map_mutation'];selected=[n for n in cls.body if isinstance(n,ast.FunctionDef) and n.name in names]
 assignments={n.targets[0].id:n.value for n in source.body if isinstance(n,ast.Assign) and len(n.targets)==1 and isinstance(n.targets[0],ast.Name)}
 namespace=dict(time=time,achievements=achievements,get_combat_system=get_combat_system,StimulusKind=StimulusKind,STOCK_EXPLOSION_DAMAGE_TYPES=frozenset(ast.literal_eval(assignments['STOCK_EXPLOSION_DAMAGE_TYPES'].args[0])),_BLAST_PREDICTION_OBSERVED_FRAMES=ast.literal_eval(assignments['_SNOWBALL_PREDICTION_OBSERVED_FRAMES']),logger=logging.getLogger('blast-oracle'))
 exec(compile(ast.fix_missing_locations(ast.Module(body=selected,type_ignores=[])),str(root/'server/main.py'),'exec'),namespace)
 return SimpleNamespace(**{n.name:namespace[n.name] for n in selected})
