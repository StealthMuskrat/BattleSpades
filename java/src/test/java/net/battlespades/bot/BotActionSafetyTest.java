// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.List;
import net.battlespades.physics.PlayerPhysics.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.battlespades.bot.BotActionSafety.*;

class BotActionSafetyTest {
    private static Actor actor(int id,int team,double x,double y){return new Actor(id,team,true,true,new Vector(x,y,0),new Vector(x,y,0));}
    @Test void liveSafetyClosesPlacementRacesAndRechecksTheAuthoritativeLaunchLane(){
        var owner=actor(1,0,0,0);var target=new Vector(1,0,0);
        assertTrue(deploySafe(owner,target,5,List.of(owner),List.of()));
        // Owner may plant then retreat; a teammate on the inclusive boundary may not.
        assertFalse(deploySafe(owner,target,5,List.of(owner,actor(2,0,7.5,0)),List.of()));
        assertTrue(deploySafe(owner,target,5,List.of(owner,actor(2,1,7.5,0)),List.of()));
        // A second acceptance in this frame sees the device just admitted by the first.
        assertFalse(deploySafe(owner,target,5,List.of(owner),List.of(new Explosive(38,true,new Vector(15,0,0),8))));
        assertTrue(deploySafe(owner,target,5,List.of(owner),List.of(new Explosive(38,false,new Vector(1,0,0),8))));
        var aim=new Vector(1,0,0);var far=new Vector(30,0,0);
        assertTrue(launchSafe(owner,aim,5,far,3,List.of(owner),(o,d,length)->false));
        assertFalse(launchSafe(owner,aim,5,far,3,List.of(owner),(o,d,length)->length==8));
        assertFalse(launchSafe(owner,aim,5,far,3,List.of(owner),(o,d,length)->length==29));
        assertFalse(launchSafe(owner,aim,5,far,3,List.of(owner,actor(2,0,5,1.75)),(o,d,length)->false));
        // Source target-blast test belongs inside the forward-lane interval filter.
        assertFalse(launchSafe(owner,aim,5,far,3,List.of(owner,actor(2,0,30,6)),(o,d,length)->false));
        assertTrue(launchSafe(owner,aim,5,far,3,List.of(owner,actor(2,0,96,0)),(o,d,length)->false));
        assertFalse(launchSafe(owner,aim,5,far,3,List.of(owner),null));
        assertNull(normalized(new Vector(0,0,0)));
        assertEquals(aim,normalized(new Vector(5,0,0)));
    }
}
