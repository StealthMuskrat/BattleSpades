// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import net.battlespades.player.*;

/** Explicit inert mode/reload/lag hooks for UDP fixtures. Never production policy or packaged code. */
public final class InfantryPorts implements InfantrySimulation.Hooks {
    public int moves;
    public void lagHistory(PlayerLife p,int tick){}
    public void housekeeping(PlayerLife p){}
    public boolean burdened(PlayerLife p){return false;}
    public int pendingTerrainMutations(){return 0;}
    public void report(PlayerLife p,String kind,boolean enforced){}
    public void disconnect(PlayerLife p,int reason){throw new IllegalStateException("Unexpected fixture timeout "+reason);}
    public void afterMove(PlayerLife p,double dt){moves++;}
    public void afterTick(PlayerLife p,double dt){}
    public void failed(PlayerLife p,RuntimeException failure){throw new IllegalStateException("Fixture movement failed",failure);}
}
