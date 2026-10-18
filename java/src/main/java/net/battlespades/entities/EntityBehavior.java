// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;

/** Original inert base hooks. Concrete device, objective and projectile policies remain separate. */
public class EntityBehavior {
    public double touchRadius,hitRadius;
    public boolean takesDamage;
    public Vector hitCenterOffset=new Vector(0,0,0);
    private static final ClassValue<Boolean> TICKS=new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try { return type.getMethod("onTick",MapEntity.class,double.class,EntityContext.class).getDeclaringClass()!=EntityBehavior.class; }
            catch(NoSuchMethodException impossible) { throw new AssertionError(impossible); }
        }
    };
    final boolean ticks(){return TICKS.get(getClass());}
    public void onTick(MapEntity entity,double dt,EntityContext context) { }
    public boolean onTouch(MapEntity entity,PlayerLife player,EntityContext context){return false;}
    public void onDamage(MapEntity entity,double amount,PlayerLife source,EntityContext context) { }
    public void onSupportLost(MapEntity entity,EntityContext context){remove(entity,context);}
    public Vector hitCenter(MapEntity entity){return new Vector(entity.x+hitCenterOffset.x(),entity.y+hitCenterOffset.y(),entity.z+hitCenterOffset.z());}
    /** Represents the source's optional on_respawn attribute without making every behavior a respawner. */
    public interface Respawning {void onRespawn(MapEntity entity,EntityContext context);}
    public static void remove(MapEntity entity,EntityContext context){
        if(!entity.alive)return;
        entity.alive=false;
        if(context.destroy()!=null)context.destroy().accept(entity.id());
        if(context.registry()!=null)context.registry().remove(entity.id());
    }
}
