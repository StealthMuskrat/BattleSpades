// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import net.battlespades.physics.PlayerPhysics.Vector;

final class BotVectors {
    static final Vector ZERO=new Vector(0,0,0);
    private BotVectors() {}
    static double flat(Vector a,Vector b) {return Math.hypot(a.x()-b.x(),a.y()-b.y());}
    static double distance(Vector a,Vector b) {return Math.hypot(Math.hypot(a.x()-b.x(),a.y()-b.y()),a.z()-b.z());}
    static Vector unit(double x,double y) {double length=Math.hypot(x,y);return length>1e-6?new Vector(x/length,y/length,0):ZERO;}
    static Vector ahead(BotMessages.Player p,Vector heading) {return new Vector(p.eye().x()+heading.x()*6,p.eye().y()+heading.y()*6,p.eye().z());}
}
