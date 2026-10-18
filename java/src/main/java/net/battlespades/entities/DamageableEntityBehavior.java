// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;

/** Each placed damageable owns its behavior and health, as in the source. */
public class DamageableEntityBehavior extends EntityBehavior {
    private double health;
    public DamageableEntityBehavior(double health){this.health=nonnegative(health);takesDamage=true;hitRadius=.75;hitCenterOffset=new Vector(0,0,.5);}
    // Python max(0.0, NaN) returns its first argument.
    private static double nonnegative(double value){return value>0?value:0;}
    public double health(){return health;}
    @Override public void onDamage(MapEntity entity,double amount,PlayerLife source,EntityContext context){
        double damage=nonnegative(amount);if(damage<=0||!entity.alive)return;
        health=nonnegative(health-damage);if(health<=0)onDestroyed(entity,source,context);
    }
    public void onDestroyed(MapEntity entity,PlayerLife source,EntityContext context){remove(entity,context);}
}
