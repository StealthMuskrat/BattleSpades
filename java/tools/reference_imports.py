"""Load reference policy modules without executing server/__init__'s launcher imports.

No policy function or source table is substituted. The package namespace merely
avoids eagerly importing unrelated server.main, bots, discovery and integrations.
"""
import importlib.machinery
import importlib.util
import sys


def prepare(root):
    sys.path.insert(0,str(root))
    spec=importlib.machinery.ModuleSpec('server',loader=None,is_package=True)
    spec.submodule_search_locations=[str(root/'server')]
    module=importlib.util.module_from_spec(spec)
    sys.modules['server']=module
