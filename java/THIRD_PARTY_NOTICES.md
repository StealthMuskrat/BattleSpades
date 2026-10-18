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

Player-name matching includes a transformed Unicode Character Database 15.0.0
export (normalization, casefold and properties) from Python 3.12's unicodedata.
Unicode data copyright © 1991-2022 Unicode, Inc.; the permission notice is retained
in licenses/unicode-15.txt and packaged under META-INF/licenses. No ICU or Python
implementation/runtime is included. Reference license:
https://github.com/unicode-org/icu/blob/release-72-1/icu4c/LICENSE.

SourceRandom ports MT19937 and CPython 3.12 integer seeding and sequence selection
to Java. SpawnMath ports CPython's compensated two-coordinate norm and floating-point sum,
used by spawn and combat geometry. MT19937
copyright (C) 1997-2002 Makoto Matsumoto and Takuji Nishimura, BSD-3-Clause;
CPython adaptations copyright Python Software Foundation, PSF-2.0. Notices are in
licenses/mt19937.txt and licenses/python-psf.txt and packaged in the JAR. Changes:
Java owner-confined state/collections and portable arithmetic replace Python/C
objects, interpreter callbacks and native implementation. Integer seeds only;
no Python runtime is included. Reference sources:
https://github.com/python/cpython/blob/v3.12.0/Modules/_randommodule.c,
https://github.com/python/cpython/blob/v3.12.0/Lib/random.py,
https://github.com/python/cpython/blob/v3.12.0/Modules/mathmodule.c.

ENet wire constants and command layout derive from the vendored ENet MIT source
Copyright (c) 2002-2020 Lee Salzman; complete MIT notice is preserved in the root
THIRD_PARTY_NOTICES.md and packaged in the JAR. No ENet native binary is included.

SourceEntityIds adapts CPython 3.12 Objects/setobject.c (Python Software Foundation,
PSF-2.0) to owner-confined primitive uint16 entity IDs. It preserves support-bucket
callback ordering without Python object comparison or interpreter/runtime code.
The full PSF license is in licenses/python-psf.txt and packaged in the JAR.
Reference: https://github.com/python/cpython/blob/v3.12.0/Objects/setobject.c.
