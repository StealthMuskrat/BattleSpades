# Current Zombie/Hardcore checkpoint

Zombie and all ten Hardcore variants pass **6,329 component + 52 integration = 6,381 Java tests** across 113 suites, with zero failures, errors or skips. Java 25 `check build`, strict compilation, portability and distributions pass. All three packet-generator outputs reproduce exactly. The six reference source directories still match upstream `546c548780c400f21d73ca6713f86194ceb3c844` and all 262 frozen resource paths are unchanged.

The focused original-source run reports **109 passed and 10 failed out of 119**. Eight failures are unchanged `_Server` test doubles missing `respawn_player`; one exposed-minimap expectation and one reset respawn-call expectation differ from the current reference implementation. Original source/tests were not edited or skipped. This is not a passing full Python-baseline claim.

The CRC-valid application JAR is 2436289 bytes, SHA-256 `a3216142aec5c31808b816aff61a267ed3a4faf772318a98b89bc3927f8e19d3`, class major 69. Version/configuration checks exit 0; ordinary hosting remains gated at exit 2. CI for this new checkpoint is pending. Full migration, production startup/services and supported-client acceptance remain incomplete.

See [ZOMBIE_HARDCORE.md](ZOMBIE_HARDCORE.md).
