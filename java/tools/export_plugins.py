"""Unchanged source plugin lifecycle, budget and example-policy observations."""
import asyncio,json,pathlib,logging
from types import SimpleNamespace as NS
from unittest.mock import patch
ROOT=pathlib.Path(__file__).resolve().parents[2]
from reference_imports import prepare
prepare(ROOT)
from plugins.base_plugin import BasePlugin,PluginManager
from plugins._example_plugin import ExamplePlugin
import plugins.base_plugin as B
steps=[['load','alpha',False,False,0],['load','alpha',False,False,0],['load','broken',True,False,0],['load','beta',False,True,0],['disable','alpha'],['event','on_tick',42,None],['enable','alpha'],['event','on_player_spawn',7,None],['unload','alpha'],['unload','absent'],['unload','beta'],['load','faulty',False,False,-1],['event','on_tick',76,None],['unload','faulty'],['load','slow',False,False,.003],['load','later',False,False,0],['event','on_tick',77,.1],['event','on_tick',78,0],['disable','slow'],['event','on_tick',79,None],['enable','slow'],['event','on_tick',80,-1],['unload','slow'],['unload','later']]
async def run():
 warnings=[]
 class Capture(logging.Handler):
  def emit(self,record):
   if record.getMessage().startswith("Plugin event"):warnings.append(record.getMessage())
 capture=Capture();B.logger.addHandler(capture)
 server=NS(calls=[],metrics=NS(skipped_plugin_callbacks=0));manager=PluginManager(server);clock=[0.];rows=[]
 def factory(name,bad_load,bad_unload,cost):
  class Plugin(BasePlugin):
   def __init__(self,server):super().__init__(server);server.calls.append('construct:'+name)
   async def on_load(self):
    server.calls.append('load:'+name)
    if bad_load:raise ValueError('controlled load')
   async def on_unload(self):
    server.calls.append('unload:'+name)
    if bad_unload:raise ValueError('controlled unload')
   async def on_enable(self):server.calls.append('enable:'+name)
   async def on_disable(self):server.calls.append('disable:'+name)
   async def on_tick(self,tick):
    server.calls.append('tick:'+name+':'+str(tick))
    if cost<0:raise ValueError('controlled event')
    clock[0]+=cost
   async def on_player_spawn(self,value):server.calls.append('spawn:'+name+':'+str(value))
  Plugin.name=name
  return Plugin
 with patch.object(B.time,'perf_counter',side_effect=lambda:clock[0]),patch.object(B.time,'monotonic',return_value=100.):
  for step in steps:
   op,*a=step;result=None
   if op=='load':result=await manager.load_plugin(factory(*a))
   elif op=='unload':result=await manager.unload_plugin(a[0])
   elif op=='enable':result=await manager.enable_plugin(a[0])
   elif op=='disable':result=await manager.disable_plugin(a[0])
   else:await manager.call_event(a[0],a[1],budget_ms=a[2])
   rows.append(dict(step=step,result=result,calls=list(server.calls),plugins=[[p.name,p.enabled] for p in manager.get_all_plugins()],skipped=server.metrics.skipped_plugin_callbacks,warnings=len(warnings)))
 B.logger.removeHandler(capture)
 messages=[]
 async def broadcast(message):messages.append(message)
 sample=ExamplePlugin(NS(broadcast_message=broadcast));await sample.on_load();players=[NS(id=i,name='P'+str(i)) for i in range(2)];example=[]
 for killer,victim in [(None,1),(0,0),*[(0,1)]*10,(1,0),*[(0,1)]*3]:
  await sample.on_player_kill(None if killer is None else players[killer],players[victim],0)
  example.append(dict(killer=killer,victim=victim,messages=list(messages)))
 return dict(lifecycle=rows,example=example)
result=asyncio.run(run());(ROOT/'java/src/test/resources/golden/plugins.json').write_text(json.dumps(result,indent=2)+'\n');print(len(result['lifecycle']),'lifecycle/budget boundaries;',len(result['example']),'example kill boundaries')
