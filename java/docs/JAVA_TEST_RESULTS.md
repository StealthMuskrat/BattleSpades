# Current Java test results

The capture-zone batch passes **6,300 component + 46 integration = 6,346 Java tests** across 113 suites, with zero failures, errors or skips. Consolidated Java 25 `check build`, strict compiler warnings, portability checks and distribution packaging pass. All **65 selected original TC/Multi-Hill/objective/escape-watch tests** pass against updated source `546c548780c400f21d73ca6713f86194ceb3c844`. Earlier CTF/runtime test evidence remains scoped to its recorded snapshots.

The seven new `main` commits and all 29 changed reference/config/workflow files are imported exactly. The six original source directories match `main` at `546c548780c400f21d73ca6713f86194ceb3c844`. All 262 frozen Java fixture/catalog resource paths remain byte-identical; their historical provenance stays intact. Of the preceding 265 checked paths (which also included three generator outputs), only `PacketCodec.java` changes intentionally to add packet 106. The source inventory now audits 1,164 files and 277,001 Python/Cython source lines at the new baseline. The packet generator reproduces all three outputs exactly.

The CRC-valid JAR is 2307094 bytes, SHA-256 `4c4dcd50fa7db004f0f11304df8b7e68e70f0eeaf8e7ee62316bbd0c4a4b2ee4`, with class major 69. Version/configuration smokes exit 0; ordinary hosting remains gated at exit 2. All five CI jobs are pending for this checkpoint. Full migration, achievement/persistence/service composition and supported-client acceptance remain incomplete.

See [ZONE_RUNTIME.md](ZONE_RUNTIME.md).
