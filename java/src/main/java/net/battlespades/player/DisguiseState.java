// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import net.battlespades.physics.PlayerPhysics.Vector;

/** Source per-life disguise wallet/cadence and delayed input-label stationary anchor. */
public final class DisguiseState {
    private boolean active;private Vector anchor;private Integer pending;private double deadline,nextUse;
    public boolean active(){return active;}
    public boolean clear(){boolean changed=active;active=false;return changed;}
    void reset(){active=false;anchor=null;pending=null;deadline=nextUse=0;}
    public boolean activate(PlayerLife p,Integer loop,double now){if(active||now<nextUse||!p.inventory().consumeDisguise())return false;nextUse=now+.5;active=true;anchor=p.position();pending=p.actionClock().labelPlausible(loop)?loop:null;deadline=now+1.5;return true;}
    public void moved(PlayerLife p,double now){
        if(!active)return;var at=p.position();var source=p.inputTimeline().state().sourceLabel();
        if(pending!=null){if(now<deadline&&p.actionClock().labelPlausible(pending)&&(source==null||source<pending)){anchor=at;return;}pending=null;}
        if((p.motion().snapshot().movement()&31)!=0){clear();return;}
        if(anchor==null){anchor=at;return;}if(Math.hypot(at.x()-anchor.x(),at.y()-anchor.y())>.5)clear();
    }
}
