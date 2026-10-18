// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

/** Authored deployable blast parameters; stock placement defaults are a separate caller policy. */
public record DeviceBlast(int throwerId,double damage,double blockDamage,int craterRadius,int killType,
        double radius,boolean forceDestroy,double minimum,double maximum,Integer damageType,boolean ignorePlayerLos) { }
