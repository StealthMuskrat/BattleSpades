# Reference baseline (2026-10-15 UTC)

The 2026-10-16 flight checkpoint separately passes **117 original tests**:
`tests/test_jetpack.py`, `tests/test_jetpack_handoff_latency.py`,
`tests/test_parachute.py`, `tests/test_flight_balance.py` and
`tests/test_flight_authority_flow.py`. Zero failures/errors/skips. Its full-path
flight oracle executes unchanged Player/SimulationRuntime/native World methods;
see [PLAYER_FLIGHT.md](PLAYER_FLIGHT.md). This focused result does not replace the
full baseline or resolve its existing VIP discrepancy.

Source: `6216b771282d2d349ccd67ee58c378f035c83553`, unchanged production
Python/Cython implementation. Python 3.12. Original requirements installed in an
isolated reference virtual environment. Cython 3.0.12, setuptools 80.9.0. Native
extensions built from the original sources using `CC=gcc CXX=g++` because the
Python runtime's configured `clang` executable was unavailable.

## Full canonical suite

Command: `python -m pytest -q --junitxml=java/docs/python-baseline.xml`.
Outcome: **collection stopped with two errors**, in `test_server_gui_network.py`
and `test_workshop_dialog.py`. This environment cannot load Tk's
`libtcl9tk9.0.so`. No full-suite pass is claimed. Original `tests/conftest.py`
ignores five old test modules; those ignores were preserved.

## Headless full-suite attempt

Command: `python -m pytest -q --ignore=tests/test_server_gui_network.py
--ignore=tests/test_workshop_dialog.py --junitxml=java/docs/python-baseline-headless.xml`.
A separate collect-only run reports **11,795 tests**. The execution was interrupted
with exit code 130 after 1,087 passing progress symbols and three failures,
approximately 9% through collection order. It did not complete or write a JUnit
report. These are progress-symbol counts, not a complete pytest summary. Do not
interpret it as 1,087 independently isolated passing regression guarantees.

The first three failures were identified from collection order, then reproduced
by running the complete `tests/test_bot_built_block_breach.py` module separately:
**1 passed, 3 failed**. The failing tests are:

- `test_built_wall_costs_more_swings_than_map_terrain`
- `test_removed_built_cell_forgets_its_health`
- `test_zombie_hand_breach_timeout_covers_every_retail_damage_roll`

Each fails because the test's `SimpleNamespace` observer lacks `can_dig`, which
`server/bot_ai/simple_worker.py:_dig_profile` accesses. These failures predate the
Java changes. The reference source/tests were not changed to hide them.

## Focused compatibility baseline

Command:

```sh
python -m pytest -q tests/test_lzf_codec.py \
  tests/test_client_data_palette_packet.py tests/test_reversed_bytes_packets.py \
  tests/test_reversed_world_update.py tests/test_compact_vxl.py \
  tests/test_vxl_column_fill.py tests/test_legacy_vxl_import.py \
  --junitxml=java/docs/python-core-baseline.xml
```

Outcome: **79 passed, 1 failed in 5.76 seconds**. The failing
`test_mayan_jungle_load_stays_under_the_memory_budget` uses psutil to read its
process RSS. This environment has no usable `/proc`, so psutil raises
`NoSuchProcess` rather than measuring RSS. Memory-budget acceptance is unverified.

The three completed reference runs' XML reports are committed with this baseline.
Native ENet handshake and golden-fixture generation are separate evidence, recorded
in the migration report and fixture provenance manifest. No baseline performance
or original-client result has been inferred from unit-test results.


## Accepted-health checkpoint, 2026-10-15

Original kill-feed, combat-score, conduct and explosion-rounding subsets pass
46 tests. The additional original rules audit passes 45 and fails
`test_vip_match_ends_after_the_configured_rounds_played`; isolated reproduction
fails the same way. After one win per team and a draw, `rounds_played` is 3 but
the expected termination callback `[3]` remains `[]`. The original server,
shared, aoslib, protocol and mode source files match reference commit
`6216b771282d2d349ccd67ee58c378f035c83553`. This is retained reference evidence;
the full Python baseline is not reported as passing. See PLAYER_HEALTH.md.

## ClientData/action-cadence checkpoint, 2026-10-16

Unchanged `test_input_loss_recovery.py`, `test_action_clock.py` and
`test_action_clock_recovery_review.py`: **31 passed**, no skips. The compiled
original World/VXL/packet modules were rebuilt with strict floating-point flags.
The new fixture generators execute original methods without replacement; their
phase/timing comparison scope is documented in PLAYER_INPUT.md. Original source
and test files still match the reference commit. This does not complete the full
Python baseline or remove the existing VIP discrepancy.

## Infantry simulation checkpoint, 2026-10-16

Unchanged `test_anticheat_movement.py` and `test_input_loss_recovery.py`:
**57 passed**, zero skips. The new oracle runs original SimulationRuntime/Player
and compiled native World without method replacement. Its full no-flight scope
and observer exclusions are documented in INFANTRY_SIMULATION.md. Source and tests
remain unchanged; this subset does not complete the full Python baseline.
