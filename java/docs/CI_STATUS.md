# Current Demolition/VIP checkpoint

Demolition and VIP pass **6,314 component + 50 integration = 6,364 Java tests** across 113 suites, with zero failures, errors or skips. Java 25 `check build`, strict compilation, portability and distribution packaging pass. All three packet-generator outputs reproduce exactly; original native packet encoders independently reproduce the packet-18/117 literal bytes used by the boundary tests. The six reference source directories still match upstream `546c548780c400f21d73ca6713f86194ceb3c844` and all 262 frozen resource paths are unchanged.

The focused original-source run reports **36 passed and 35 failed out of 71**. All 35 failures occur in unchanged VIP test doubles missing `set_tool`, now required by upstream live promotion. The original code/tests were not edited or skipped; this is not a passing Python-baseline claim.

The CRC-valid application JAR is 2403794 bytes, SHA-256 `0bcfbbfb92d2f0cf47e1034b86429c2fe66306390a4ee35fcfb8e01c6adb22f8`, class major 69. Version/configuration checks exit 0; ordinary hosting remains gated at exit 2. CI for this new checkpoint is pending. The previous carried-objective run [39321095562](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39321095562) has build/native/Windows/Ubuntu passing and macOS still running at the latest observation; no all-five pass is claimed for it. Full migration, production startup/services and supported-client acceptance remain incomplete.

See [DEMVIP_MODES.md](DEMVIP_MODES.md).
