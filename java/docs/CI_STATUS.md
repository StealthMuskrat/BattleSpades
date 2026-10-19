# Current isolated creator host checkpoint

The isolated creator host passes **6,344 component + 57 integration = 6,401 Java tests** across 117 suites, with zero Java failures, errors or skips. Java 25 `check build`, strict compilation, portability and distributions pass. All four codec/catalog outputs reproduce exactly in an isolated tree. The six reference source directories still match upstream `546c548780c400f21d73ca6713f86194ceb3c844` and all 262 frozen resource paths are unchanged.

Fresh unchanged original-source checks report **52 passed, 0 failed and 3 skipped out of 55**; the skips require retail baseplates absent from this workspace. The original native FogColor encoder independently reproduces packet 74 as `4a00563412` for RGB 18/52/86. Previous project/validation differential evidence remains in [UGC_PIPELINE.md](UGC_PIPELINE.md); those larger traces were not rerun for this checkpoint.

The CRC-valid application JAR is 2565206 bytes, SHA-256 `e6b4b2f748ac36b1c5dfda03eaa4db954611aa0e1896b1a0ea2141e1483c7f67`, class major 69. Version/configuration/help checks exit 0; missing-project checks and ordinary hosting exit 2. CI for this new checkpoint is pending. This provides an explicit isolated creator API; complete retail editor tools, production startup/services, supported-client acceptance and the overall migration remain incomplete.

See [CREATOR_HOST.md](CREATOR_HOST.md).
