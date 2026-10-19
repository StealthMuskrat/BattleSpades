# Current Map Creator pipeline checkpoint

The Map Creator pipeline passes **6,339 component + 55 integration = 6,394 Java tests** across 116 suites, with zero Java failures, errors or skips. Java 25 `check build`, strict compilation, portability and distributions pass. A corrupt generated local JAR was rebuilt; final packaging verification reused the passed tests. All four generated codec/catalog outputs reproduce exactly in an isolated tree. The six reference source directories still match upstream `546c548780c400f21d73ca6713f86194ceb3c844` and all 262 frozen resource paths are unchanged.

The unchanged original-source checks report **52 passed, 0 failed and 3 skipped out of 55**; those three cases require retail baseplates absent from this workspace. Independent comparisons match 24 project traces containing 1,920 mutations, 216 mode validations and four VXL capacity samples. Original native encoders reproduce all four packet-68/98/102/118 literals. This is not a full Python-baseline or supported-client acceptance claim.

The CRC-valid application JAR is 2505161 bytes, SHA-256 `c1362d073322ea2fa2699f21cd75d9ad8ee48b2d4c7fed5b04981e5661ba8678`, class major 69. Version/configuration/help checks exit 0; missing-project checks and ordinary hosting exit 2. CI for this new checkpoint is pending. Full UGC hosting, production startup/services and the overall migration remain incomplete.

See [UGC_PIPELINE.md](UGC_PIPELINE.md).
