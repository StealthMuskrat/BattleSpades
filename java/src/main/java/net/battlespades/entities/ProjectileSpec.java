// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.*;

/** server.projectiles' authored flight and warhead profiles, including the retail RPG2 override. */
public record ProjectileSpec(String name,Motion motion,double gravity,double damage,double blockDamage,int killType,int damageType,
        double lifespan,double destroyedDamage,double destroyedBlockDamage,int entityType,double radius,double minimum,double maximum,
        double destroyedRadius,double destroyedMinimum,double destroyedMaximum) {
    public enum Motion {BOUNCE,CONTACT,STICK,DEPLOY}
    private static ProjectileSpec basic(String name,Motion motion,double gravity,double damage,double blocks,int kill,int type,int entity,double radius,double low,double high){return new ProjectileSpec(name,motion,gravity,damage,blocks,kill,type,0,0,0,entity,radius,low,high,0,0,0);}
    public static final Map<Integer,ProjectileSpec> TOOLS;
    public static final ProjectileSpec TURRET=basic("rocket_turret_rocket",Motion.CONTACT,.05,50,10,18,21,21,3,.1,.3);
    static {
        var s=new LinkedHashMap<Integer,ProjectileSpec>();
        s.put(11,basic("grenade",Motion.BOUNCE,1,230,4,3,7,0,4,.5,1));
        s.put(31,basic("classic_grenade",Motion.BOUNCE,1,130,15,22,22,0,9,.1,.1));
        s.put(32,basic("ap_grenade",Motion.BOUNCE,1,500,.5,23,23,0,6,.25,.5));
        s.put(33,basic("molotov",Motion.CONTACT,1,50,3,24,24,27,4,0,.1));
        s.put(21,basic("dynamite",Motion.BOUNCE,1,300,5,15,16,0,8,.1,.15));
        s.put(20,basic("landmine",Motion.BOUNCE,1,100,3,14,15,0,6,.75,.75));
        s.put(12,basic("rocket",Motion.CONTACT,.05,140,5,4,8,21,6,0,.25));
        s.put(13,basic("rocket2",Motion.CONTACT,.025,40,2,5,9,22,6,0,.25));
        s.put(14,new ProjectileSpec("drill",Motion.CONTACT,1.5,50,5,6,10,3,95,10,23,3,.01,.1,3.5,.1,.2));
        s.put(47,new ProjectileSpec("drill",Motion.CONTACT,1.5,50,5,28,33,3,95,10,23,3,.01,.1,3.5,.1,.2));
        s.put(29,basic("snowball",Motion.CONTACT,.5,10,0,21,20,24,5,.3,.3));
        s.put(48,basic("snowball",Motion.CONTACT,.5,10,0,29,32,24,5,.3,.3));
        s.put(57,basic("sticky_grenade",Motion.STICK,1,200,6,34,39,34,5,.75,.1));
        s.put(58,basic("mine_projectile",Motion.DEPLOY,1,100,15,35,40,37,6,.75,.75));
        s.put(54,basic("chemical_bomb",Motion.CONTACT,1,50,3,31,6,32,3,0,0));
        s.put(55,new ProjectileSpec("gl_grenade",Motion.CONTACT,1,100,6,32,37,3,0,0,33,4,0,.25,0,0,0));
        TOOLS=Collections.unmodifiableMap(s);
    }
}
