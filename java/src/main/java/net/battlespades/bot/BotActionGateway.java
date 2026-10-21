// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.Objects;
import net.battlespades.player.PlayerLife;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;

/** Owner-thread boundary between detached proposals and ordinary gameplay services.
 * Implementations of Services must use the same admission, inventory, cadence,
 * construction reservation and replication paths as human requests.
 */
public final class BotActionGateway {
    public interface Services {
        PlayerLife occupant(int id);
        boolean select(PlayerLife player,int tool);
        boolean fire(PlayerLife player);
        boolean reload(PlayerLife player);
        boolean blockIntervalReady(PlayerLife player);
        void palette(PlayerLife player,String argument);
        boolean build(PlayerLife player,Vector cell);
        boolean buildLine(PlayerLife player,Vector start,Vector end);
        boolean paint(PlayerLife player,Vector cell);
        boolean prefab(PlayerLife player,int tool,String name,Vector at,int quarterYaw);
        boolean deploy(PlayerLife player,int tool,Vector at,int face,double yaw);
        boolean explosiveSafe(PlayerLife player,int tool,Vector at);
        boolean detonate(PlayerLife player);
        boolean disguise(PlayerLife player,boolean communityOnly);
        boolean modeCommand(PlayerLife player,String command,Vector at);
        boolean oriented(PlayerLife player,Action action);
        void rejected(PlayerLife player,Action action,RuntimeException failure);
    }
    private final Thread owner=Thread.currentThread();
    private final Services services;
    public BotActionGateway(Services services){this.services=Objects.requireNonNull(services);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Bot actions require gameplay owner");}
    /** Slot identity, rather than just its reusable numeric id, is authoritative. */
    public boolean ownsLiveSlot(PlayerLife player){owned();return player!=null&&services.occupant(player.id())==player;}
    public boolean execute(PlayerLife player,Action action){
        owned();
        if(player==null||action==null||action.kind()==null||!player.bot()||!ownsLiveSlot(player)||!player.alive()||!player.spawned())return false;
        try{return dispatch(player,action);}catch(RuntimeException failure){
            // Reporting must not turn a rejected proposal into a simulation crash.
            try{services.rejected(player,action,failure);}catch(RuntimeException ignored){/* fail closed */}
            return false;
        }
    }
    private boolean select(PlayerLife player,int tool){
        return player.selection().loadout().contains(tool)&&player.toolEnabled(tool)
            &&services.select(player,tool)&&player.inventory().tool()==tool;
    }
    private boolean dispatch(PlayerLife p,Action a){
        return switch(a.kind()){
            case NONE,MINE -> false;
            case FIRE -> (a.toolId()<0||select(p,a.toolId()))&&services.fire(p);
            case RELOAD -> (a.toolId()<0||select(p,a.toolId()))&&services.reload(p);
            case MELEE -> select(p,a.toolId())&&services.fire(p);
            case PAINT -> finite(a.position())&&select(p,5)&&services.paint(p,truncated(a.position()));
            case DISGUISE -> services.disguise(p,true);
            case MODE_COMMAND -> ("stash".equals(a.argument())||"pass".equals(a.argument()))&&services.modeCommand(p,a.argument(),a.position());
            case BUILD -> build(p,a,false);
            case BUILD_LINE -> build(p,a,true);
            case PLACE_PREFAB -> finite(a.position())&&Double.isFinite(a.yaw())&&a.argument()!=null&&!a.argument().isEmpty()
                &&prefabTool(a.toolId())&&select(p,a.toolId())
                &&services.prefab(p,a.toolId(),a.argument(),a.position(),((int)Math.rint(a.yaw()/(Math.PI/2)))&3);
            case DEPLOY -> deploy(p,a);
            case ORIENTED -> services.oriented(p,a);
        };
    }
    private boolean build(PlayerLife p,Action a,boolean line){
        if(!finite(a.position())||(line&&!finite(a.endPosition()))||!services.blockIntervalReady(p)||!select(p,5))return false;
        services.palette(p,a.argument());
        return line?services.buildLine(p,rounded(a.position()),rounded(a.endPosition())):services.build(p,rounded(a.position()));
    }
    private boolean deploy(PlayerLife p,Action a){
        int tool=a.toolId();
        if(!select(p,tool))return false;
        if(tool==64)return services.disguise(p,false);
        if(tool==59&&"detonate".equals(a.argument()))return services.detonate(p);
        if(!finite(a.position()))return false;
        if((tool==21||tool==20||tool==59)&&!services.explosiveSafe(p,tool,a.position()))return false;
        if(tool!=21&&tool!=20&&tool!=59&&tool!=56&&tool!=51&&tool!=15&&tool!=16)return false;
        if((tool==15||tool==16)&&!Double.isFinite(a.yaw()))return false;
        int face=a.face();if((tool==21||tool==51)&&(face<0||face>5))face=4;
        return services.deploy(p,tool,a.position(),face,a.yaw());
    }
    private static boolean prefabTool(int tool){return tool==23||tool==28||tool==22||tool==41||tool==42;}
    private static boolean finite(Vector v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z())&&Math.abs(v.x())<=Integer.MAX_VALUE&&Math.abs(v.y())<=Integer.MAX_VALUE&&Math.abs(v.z())<=Integer.MAX_VALUE;}
    private static Vector rounded(Vector v){return new Vector(Math.rint(v.x()),Math.rint(v.y()),Math.rint(v.z()));}
    private static Vector truncated(Vector v){return new Vector((int)v.x(),(int)v.y(),(int)v.z());}
}
