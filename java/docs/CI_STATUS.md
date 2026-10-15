# Independent CI verification

Current lifecycle implementation: awaiting publication and independent CI.
The previous metadata implementation passed all five jobs in
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38790040711.

Local current-tree checks pass: clean check/build, 3016 unit/component tests,
nine UDP tests, 128 focused original-source regressions, fresh packaged native
transport/loader probes and version/config smoke. Life fixtures and the 69-row
production catalog reproduce from original source methods and codecs.

The new UDP test uses production life/body/session code with explicit test
mode/input/effect ports. Packaged native probes retain fixture player lives.
Full gameplay input/combat, concrete modes/effects, authentication, supported
client sessions, capacity and soak remain pending; ordinary hosting exits 2.
See [PLAYER_LIFECYCLE.md](PLAYER_LIFECYCLE.md) and [verification-results.json](verification-results.json).
