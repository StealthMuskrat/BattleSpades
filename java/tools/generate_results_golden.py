#!/usr/bin/env python3
"""Execute unchanged Revival result reservation methods with controlled identities, UUIDs and UTC time."""
import ast
from datetime import datetime, timezone, timedelta
import hashlib
import json
import os
from pathlib import Path
import sys
from types import ModuleType, SimpleNamespace

ROOT = Path(__file__).resolve().parents[2]
source = (ROOT / "server/revival_master.py").read_text()
tree = ast.parse(source)
original = next(n for n in tree.body if isinstance(n, ast.ClassDef) and n.name == "RevivalMasterService")
methods = {"_counters", "_player_delta", "accumulate_departing_player", "_participation_counters", "_participation_delta", "_merge_participation", "begin_round", "reset_scoreboard_baselines", "_profile_delta", "_merge_profile", "_result_players", "_capture_round_results"}
selected = [n for n in original.body if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name in methods]
assert len(selected) == len(methods)
cls = ast.ClassDef(name="NativeResults", bases=[], keywords=[], body=selected, decorator_list=[])
profile = ast.parse((ROOT / "server/profile_stats.py").read_text())
snapshot = next(n for n in profile.body if isinstance(n, ast.FunctionDef) and n.name == "snapshot")
mode_total = next(n for n in tree.body if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "MODE_TOTAL_STAT" for t in n.targets))
clock = SimpleNamespace(now=datetime(2026, 10, 19, tzinfo=timezone.utc), serial=0)
class Clock:
    @staticmethod
    def now(zone):
        return clock.now

def uuid4():
    clock.serial += 1
    return SimpleNamespace(hex=f"{clock.serial:032x}")

stub = ModuleType("server")
stub.achievements = SimpleNamespace(take_unreported=lambda _server, _identity: [])
sys.modules["server"] = stub
namespace = dict(datetime=Clock, timezone=timezone, uuid4=uuid4, os=os, Any=object, RevivalMasterError=RuntimeError)
module = ast.Module(body=[mode_total, snapshot, cls], type_ignores=[])
exec(compile(ast.fix_missing_locations(module), "unchanged-result-reservation-methods", "exec"), namespace)
namespace["profile_snapshot"] = namespace["snapshot"]
Native = namespace["NativeResults"]
os.environ.pop("AOS_RELAY_LOBBY_ID", None)

cases = []
for managed in (False, True):
    clock.now = datetime(2026, 10, 19, tzinfo=timezone.utc)
    clock.serial = 0
    service = Native()
    service.mode_code = "ctf" if managed else "tdm"
    service.enabled, service.write_token, service._closing = True, "controlled-test-credential", False
    service.server_id = "one:27015"
    service._player_baselines, service._profile_baselines, service._participation_baselines = {}, {}, {}
    service._pending_results, service._departed = {}, {}
    service.server = SimpleNamespace(config=SimpleNamespace(ugc_runtime=False, default_mode=service.mode_code, steam=SimpleNamespace(playlist_id=8)), players={}, world_manager=SimpleNamespace(map_file_crc=-1))
    steps, tokens = [], {}
    def step(op, **values):
        entry = dict(op=op, **values)
        if op == "player":
            fields = dict(name=values["name"], account_nickname=values["nickname"], account_legacy_id=values["identity"], is_bot=values.get("bot", False), team=values["team"], kills=values["kills"], deaths=values["deaths"], captures=values["captures"], score=values["score"])
            p = SimpleNamespace(**fields)
            if "profile" in values:
                p.profile_stats = SimpleNamespace(values={int(k):list(v)for k,v in values["profile"].items()}, connected_seconds=values["connected"], active_seconds=values["active"], afk_seconds=values["afk"], human_opponents=values["humans"], bot_opponents=values["bots"], opponent_sample_seconds=0, activity_seen=True)
            tokens[values["token"]] = p
            service.server.players[values["slot"]] = p
        elif op == "begin":
            service.begin_round()
        elif op == "advance":
            clock.now += timedelta(seconds=values["seconds"])
        elif op == "update":
            p = tokens[values["token"]]
            for key,value in values.items():
                if key in {"kills","deaths","captures","score"}:setattr(p,key,value)
                elif key == "profile":p.profile_stats.values={int(k):list(v)for k,v in value.items()}
                elif key in {"connected","active","afk"}:setattr(p.profile_stats,key+"_seconds",value)
        elif op == "reset":
            for p in service.server.players.values():p.kills=p.deaths=p.captures=p.score=0
            service.reset_scoreboard_baselines()
        elif op == "depart":
            p=tokens[values["token"]];service.accumulate_departing_player(p)
            service.server.players={k:v for k,v in service.server.players.items()if v is not p}
        elif op == "capture":
            previous=set(service._pending_results)
            service._capture_round_results(values.get("winner"),finished=values["finished"])
            entry["expected"]=[value for key,value in service._pending_results.items()if key not in previous]
        steps.append(entry)
    step("begin")
    p=dict(token="old",slot=7,name="WireName",nickname="Canonical雪",identity="1000000000",team=2,kills=4,deaths=2,captures=1,score=17)
    if managed:p.update(profile={"1":[4,0],"220":[2,0],"49":[1,1],"1006":[5,0]},connected=45,active=40,afk=10,humans=250,bots=3)
    step("player",**p)
    step("player",token="bot",slot=8,name="Bot",nickname="Bot",identity="1000000001",bot=True,team=3,kills=99,deaths=0,captures=0,score=999)
    step("player",token="anonymous",slot=9,name="ClaimedIdentity",nickname="Unverified",identity=None,team=3,kills=99,deaths=0,captures=0,score=999)
    step("advance",seconds=35)
    step("capture",winner=2,finished=True)
    step("capture",winner=2,finished=True)
    if managed:
        step("reset")
        step("update",token="old",kills=1,score=100,profile={"1":[5,0],"220":[2,0],"49":[1,1],"1006":[7,0]},connected=50,active=44,afk=11)
    else:step("update",token="old",kills=5,score=27)
    step("depart",token="old")
    step("player",token="replacement",slot=7,name="OtherWireName",nickname="NewCanonical",identity="1000000000",team=3,kills=2,deaths=0,captures=0,score=20)
    step("advance",seconds=5)
    step("capture",winner=2,finished=False)
    cases.append(dict(mode=service.mode_code,steps=steps))
# Exercise the real standard-library SQLite outbox and the read-only migration exporter.
import importlib.util
import tempfile
native_spec=importlib.util.spec_from_file_location("native_result_outbox",ROOT/"server/result_outbox.py")
native=importlib.util.module_from_spec(native_spec);native_spec.loader.exec_module(native)
export_spec=importlib.util.spec_from_file_location("export_result_outbox",ROOT/"java/tools/export_result_outbox.py")
exporter=importlib.util.module_from_spec(export_spec);export_spec.loader.exec_module(exporter)
(ROOT/"java/build").mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(dir=ROOT/"java/build") as work:
    work=Path(work);database=work/"legacy.sqlite3";events=[e for step in cases[0]["steps"] for e in step.get("expected",[])]
    native.ResultOutbox(database).put_many(events)
    before=database.read_bytes();exporter.export(database,work/"legacy.ndjson")
    assert database.read_bytes()==before
    assert [json.loads(line)for line in(work/"legacy.ndjson").read_text().splitlines()]==events
result={"source":"unchanged server/revival_master.py + server/profile_stats.py snapshot","source_sha256":hashlib.sha256(source.encode()).hexdigest(),"cases":cases}
output=ROOT/"java/src/test/resources/golden/result-reservations.json"
output.write_text(json.dumps(result,ensure_ascii=True,indent=2)+"\n")
