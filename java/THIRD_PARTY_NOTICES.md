# Java migration dependencies

BattleSpades port: AGPL-3.0-or-later, original project attribution and notices at
repository root remain applicable. Source-derived Java codecs and behavior retain
that license.

Production: tomlj 1.1.1 (Apache-2.0), ANTLR Java runtime 4.11.1 (BSD-3-Clause),
Checker Framework checker-qual 3.21.2 (MIT). Dependencies' own META-INF notices and
licenses are retained where supplied. See dependency lock and verification metadata
for exact resolved artifacts.

Development: JUnit Jupiter/Platform 5.11.4/1.11.4 (EPL-2.0), Gradle 9.1.0
(Apache-2.0 and bundled notices). Neither is required by the application runtime.

ENet wire constants and command layout derive from the vendored ENet MIT source
Copyright (c) 2002-2020 Lee Salzman; complete MIT notice is preserved in the root
THIRD_PARTY_NOTICES.md and packaged in the JAR. No ENet native binary is included.
