// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.function.IntPredicate;

/** Current-life held-tool authorization shared by input and outbound snapshots. */
public final class ToolSelection {
    private ToolSelection(){}
    public static boolean authorized(PlayerLife p,int tool,boolean modeAllows,boolean mountedGun){
        return p.alive()&&p.spawned()&&tool>=0&&tool<66&&p.toolEnabled(tool)&&modeAllows&&
            (p.selection().loadout().contains(tool)||(tool==15&&mountedGun));
    }
    public static boolean active(PlayerLife p,int tool,boolean modeAllows,boolean mountedGun){
        return p.inventory().tool()==tool&&authorized(p,tool,modeAllows,mountedGun);
    }
    /** Source order: pre-MG hand tool, active weapon, committed loadout. Never synthesize a tool. */
    public static Integer ensureLegal(PlayerLife p,IntPredicate allowed){
        if(!p.alive()||!p.spawned()||allowed.test(p.inventory().tool()))return null;
        var candidates=new ArrayList<Integer>();candidates.add(p.fire().state().beforeMachineGun());candidates.add(p.inventory().weapon());candidates.addAll(p.selection().loadout());
        for(var tool:candidates)if(tool!=null&&tool!=p.inventory().tool()&&tool!=15&&allowed.test(tool)){p.fire().tool(tool,true);return tool;}
        return null;
    }
    /** The device owner must first revoke its mount, then call this hook. */
    public static Integer unmounted(PlayerLife p,IntPredicate allowed){
        Integer changed=p.inventory().tool()==15?ensureLegal(p,allowed):null;p.fire().forgetMachineGun();return changed;
    }
}
