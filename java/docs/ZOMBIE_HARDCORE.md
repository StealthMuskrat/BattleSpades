# Zombie and Hardcore runtimes

Zombie and all ten Hardcore variants pass **6,329 component + 52 integration = 6,381 Java tests** across 113 suites, with zero failures, errors or skips. Java 25 `check build`, strict compilation, portability and distributions pass. All three packet-generator outputs reproduce exactly. The six reference source directories still match upstream `546c548780c400f21d73ca6713f86194ceb3c844` and all 262 frozen resource paths are unchanged.

The focused original-source run reports **109 passed and 10 failed out of 119**. Eight failures are unchanged `_Server` test doubles missing `respawn_player`; one exposed-minimap expectation and one reset respawn-call expectation differ from the current reference implementation. Original source/tests were not edited or skipped. This is not a passing full Python-baseline claim.

The CRC-valid application JAR is 2436289 bytes, SHA-256 `a3216142aec5c31808b816aff61a267ed3a4faf772318a98b89bc3927f8e19d3`, class major 69. Version/configuration checks exit 0; ordinary hosting remains gated at exit 2. All five CI jobs passed for implementation `078664999ad7ba490994677b857ada05df904e50`, tested tree `a6ed77f797f5cb7ddc4effe64aefc76c3eb8e5a5`, in [run 39331928555](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39331928555). All 65 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 77 configured reference generators passed; the build byte-compared repeated archives. All three platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-19T22:09:28.070192+00:00. Full migration, production startup/services and supported-client acceptance remain incomplete.

## Zombie

The standard `zom` factory now owns waiting/countdown/outbreak/active/intermission/reset phases. Patient zero selection retains at least one survivor, prefers settled peers or bots, applies short protection and converts actual lives immediately at a verified death-site spawn. Survivor deaths convert with native transition packets and classic corpse presentation; ordinary infected deaths retain native graves and the ten-second respawn policy. Live team/class authority, asymmetric minimap visibility, hidden score bars, native marker replay and late joining clients share the same current identities.

Last-survivor markers/music, periodic 50/10-second and 150/5-second awards, infected kills and survivor bonuses use real scoreboard/profile owners. Underpopulation aborts without a win; departure of the last infected selects a replacement without transferring state to recycled ids. Fixed-count rounds use cancellable intermission timers and bounded reset queues restoring saved survivor roles. Only the final round enables ordinary escape-watch/ballot timeout behavior. Required round observations and bot retirement safety remain explicit external service boundaries.

## Hardcore

All ten `hc_` variants (`tdm`, `ctf`, `cctf`, `zom`, `vip`, `mh`, `tc`, `dia`, `dem`, `oc`) reuse their concrete base owners. Immutable mode-scoped rule overlays retain original explicit-rule provenance and restore ordinary behavior on replacement. Five roles, 35 native slot overrides, exact default/validated loadouts, quantized speed profiles, gravity and fall/water/canopy policy are applied to actual lives. Flight capability 3 is enforced before map payloads; negotiated BSFP v2 movement fields are preserved.

Actual direct/explosion damage, Heavy body-hit reduction, medpack cooldowns, classic corpse/teabag policy, Soldier supplies and `/hcrules` are wired through their existing owners. Hardcore VIP crowns retain live roles, bodies and health; Hardcore Zombie restores saved survivor roles. InitialInfo/StateData, native CreatePlayer catch-up and owner supplies use the same authoritative selections. Raw Hardcore loadouts are normalized before stock defaults so the starting Soldier weapon remains 60.

## Verification and remaining work

Existing harnesses cover outbreak/conversion/respawn, periodic awards/fixed rounds/bounded reset, departure/reused slots/underpopulation, all ten factories, mode restoration, classic corpses/teabag and retained-role VIP/Zombie composition. Two real UDP cases cover ordinary/Hardcore Zombie, pre-map capability rejection, negotiated movement/default loadouts, supplies, infection and spectator replay. No new fixture goldens, copied UDP harness or redundant one-assertion test families were added.

Full UGC/editor, bot director/navigation/actions, persistent identities/admin/bans/profiles/results/achievements/master services, end/startup/transition composition and supported-client/capacity acceptance remain required. The ordinary launcher remains gated. Evidence: [zombie-hardcore-local-verification.json](zombie-hardcore-local-verification.json). CI: [CI_STATUS.md](CI_STATUS.md).
