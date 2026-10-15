// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.physics;

/** Exact class tables from shared/constants.py at source commit 6216b77. */
public record MovementProfile(float acceleration,float sprint,float crouchSneak,float jump,
                              float waterFriction,float fallMinDistance,float fallMaxDistance,
                              float fallMaxDamage,float waterDamage,boolean canSprintUphill) {
    public MovementProfile {
        for(float value:new float[]{acceleration,sprint,crouchSneak,jump,waterFriction,fallMinDistance,fallMaxDistance,fallMaxDamage,waterDamage})
            if(!Float.isFinite(value)||value<0)throw new IllegalArgumentException("Movement profile");
    }
    private static final MovementProfile[] CLASSES={
        new MovementProfile(0.7f,1.4f,0.5f,1.2f,8.0f,10.0f,40.0f,100.0f,0.5f,true),
        new MovementProfile(0.7f,1.45f,0.5f,1.5f,8.0f,10.0f,30.0f,100.0f,0.5f,true),
        new MovementProfile(0.7f,1.1f,0.5f,1.0f,12.0f,10.0f,10.0f,10.0f,0.5f,true),
        new MovementProfile(0.7f,1.4f,0.5f,1.2f,8.0f,10.0f,40.0f,100.0f,0.5f,true),
        new MovementProfile(0.5f,1.65f,0.5f,1.5f,4.0f,10.0f,60.0f,100.0f,0.25f,true),
        new MovementProfile(1.0f,1.33f,0.5f,1.0f,8.0f,6.0f,26.0f,100.0f,1.0f,false),
        new MovementProfile(0.7f,1.5f,0.5f,1.2f,8.0f,10.0f,40.0f,100.0f,0.5f,true),
        new MovementProfile(0.7f,1.5f,0.5f,1.2f,8.0f,10.0f,40.0f,100.0f,0.5f,true),
        new MovementProfile(0.7f,1.5f,0.5f,1.2f,8.0f,10.0f,40.0f,100.0f,0.5f,true),
        new MovementProfile(0.7f,1.5f,0.5f,1.2f,8.0f,10.0f,40.0f,100.0f,0.5f,true),
        new MovementProfile(0.7f,1.5f,0.5f,1.2f,8.0f,10.0f,40.0f,100.0f,0.5f,true),
        new MovementProfile(0.7f,1.5f,0.5f,1.2f,8.0f,10.0f,40.0f,100.0f,0.5f,true),
        new MovementProfile(0.7f,1.25f,0.5f,1.0f,8.0f,10.0f,40.0f,100.0f,0.5f,true),
        new MovementProfile(1.0f,3.0f,0.5f,1.0f,1.0f,255.0f,255.0f,0.0f,1.0f,true),
        new MovementProfile(1.1f,3.0f,0.25f,2.5f,12.0f,6.0f,20.0f,500.0f,2.0f,true),
        new MovementProfile(0.5f,1.0f,0.5f,3.0f,8.0f,15.0f,25.0f,100.0f,0.5f,false),
        new MovementProfile(0.85f,1.55f,0.5f,1.5f,8.0f,10.0f,40.0f,100.0f,0.5f,true),
        new MovementProfile(0.6f,1.35f,0.5f,1.2f,8.0f,10.0f,40.0f,100.0f,0.5f,true)
    };
    public static MovementProfile forClass(int id) {
        if(id<0||id>=CLASSES.length)throw new IllegalArgumentException("Class id");
        return CLASSES[id];
    }
}
