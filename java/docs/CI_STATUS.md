# Independent CI verification

Current accepted-health implementation: awaiting publication and independent CI.
Previous lifecycle checkpoint (all five passed):
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38795898680.

Local clean check/build passes 4390 unit/component tests and ten UDP tests.
Focused original health-related suites pass 46 tests. The broader original rules
audit has 45 passes and one existing VIP round-limit failure, reproduced in
isolation with the original source unchanged. See PLAYER_HEALTH.md for scope.

Source damage/heal/history/attribution fixtures reproduce exactly. Portable JAR,
native-probe, repeat-build and smoke evidence is in verification-results.json.
The production-domain UDP health test verifies loading gates, HP/death ordering
and respawn with explicit test policies. Packaged native probes retain fixture
player lives. Full gameplay and supported-client/capacity checks remain pending.
