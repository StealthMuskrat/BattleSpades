# Reference baseline (2026-10-15 UTC)

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
