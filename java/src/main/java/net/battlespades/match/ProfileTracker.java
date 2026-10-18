// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.function.*;
import net.battlespades.player.*;
/** Accepted profile events. Callers supply current mode gates and the actual scoring relation. No persistence/reward IO. */
public final class ProfileTracker {
    public record Context(boolean ugcRuntime,String defaultMode,boolean ended){public Context{Objects.requireNonNull(defaultMode);}public boolean active(){return !ugcRuntime&&!ended&&!Set.of("ugc","tut","tutorial").contains(defaultMode.toLowerCase(Locale.ROOT));}}
    private final Supplier<Context> context;
    private final BiPredicate<PlayerLife,PlayerLife> opponents;
    public ProfileTracker(Supplier<Context> context,BiPredicate<PlayerLife,PlayerLife> opponents){this.context=Objects.requireNonNull(context);this.opponents=Objects.requireNonNull(opponents);}
    public ProfileStats tracker(Scoreboard.Player p){if(p==null||p.life.bot()||!context.get().active())return null;if(p.profileStats==null)p.profileStats=new ProfileStats();return p.profileStats;}
    public void add(Scoreboard.Player p,int stat,long count,long score){var state=tracker(p);if(state!=null&&(count!=0||score!=0))state.add(stat,count,score);}
    public void shot(Scoreboard.Player p){var state=tracker(p);int tool=p.life.inventory().tool();if(state!=null&&tool>=0&&tool<MatchCatalog.constant("STATS_MAX_WEAPONS")){state.shotSerial=Math.incrementExact(state.shotSerial);state.add(1000+tool,1,0);}}
    public void hit(Scoreboard.Player p,Scoreboard.Player target){var state=tracker(p);int tool=p.life.inventory().tool();if(state!=null&&p.life!=target.life&&opponents.test(p.life,target.life)&&tool>=0&&tool<MatchCatalog.constant("STATS_MAX_WEAPONS")&&state.shotSerial>0&&state.hitSerial!=state.shotSerial){state.hitSerial=state.shotSerial;state.add(2000+tool,1,0);}}
    private static int c(String name){return MatchCatalog.constant(name);}
    public void death(Scoreboard.Player victim,Scoreboard.Player killer,int type){
        if(LifeCatalog.TRANSITIONS.contains(type))return;add(victim,c("DEATH_SCORE_REASON"),1,0);
        if(killer==victim){add(victim,c("SUICIDE_SCORE_REASON"),1,0);return;}if(killer==null)return;
        if(!opponents.test(killer.life,victim.life)){add(killer,c("KILL_SCORE_TEAMKILL_REASON"),1,0);return;}
        add(killer,c("KILL_SCORE_REASON"),1,0);if(type==c("HEADSHOT_KILL"))add(killer,c("KILL_SCORE_HEADSHOT_REASON"),1,0);else if(type==c("MELEE_KILL"))add(killer,c("KILL_SCORE_MELEE_REASON"),1,0);
        Integer tool=MatchCatalog.killTool(type);if(tool==null&&(type==c("WEAPON_KILL")||type==c("HEADSHOT_KILL")||type==c("MELEE_KILL")))tool=killer.life.inventory().tool();
        if(tool!=null){Integer stat=MatchCatalog.classKill(killer.life.selection().classId(),tool);if(stat!=null)add(killer,stat,1,0);}
        for(int streak:new int[]{5,10,15})if(killer.killStreak==streak)add(killer,c("COMBAT_"+streak+"INAROW_TOTAL"),1,0);
        if(LifeCatalog.ZOMBIES.contains(killer.life.selection().classId())){add(killer,c("ZOMBIE_HUMANS_KILLED_TOTAL"),1,0);if(tool!=null&&tool==c("ZOMBIEHAND_TOOL"))add(killer,c("ZOMBIE_HANDS_KILLS_TOTAL"),1,0);}
    }
    public void scoreChanged(Scoreboard.Player p,int reason){var state=tracker(p);if(state==null)return;long delta=p.score>state.lastScore?Math.subtractExact(p.score,state.lastScore):0;state.lastScore=p.score;if(delta==0)return;state.add(reason,Set.of(1,2,3,4,6,220).contains(reason)?0:1,delta);for(var e:MatchCatalog.AGGREGATES.entrySet())if(e.getKey()>=202&&e.getKey()<=219&&e.getValue().contains(reason))state.add(e.getKey(),1,delta);}
    public void total(Scoreboard.Player p,int stat,long count){add(p,stat,count,0);for(var e:MatchCatalog.AGGREGATES.entrySet())if(e.getKey()>=202&&e.getKey()<=219&&e.getValue().contains(stat))add(p,e.getKey(),count,0);}
    /** Source reset clears only an already-existing profile's score baseline. */
    public void resetBaseline(Scoreboard.Player p){if(p.profileStats!=null)p.profileStats.lastScore=0;}
    public void tick(Scoreboard.Player p,double dt,boolean airborne,String mapName,Collection<Scoreboard.Player> players){
        var state=tracker(p);if(state==null||!(dt>0&&dt<=1))return;state.connectedSeconds+=dt;var at=p.life.position();var aim=p.life.aim();double distance=state.lastPosition==null?0:SpawnMath.hypot(at.x()-state.lastPosition.x(),at.y()-state.lastPosition.y(),at.z()-state.lastPosition.z());boolean moved=distance>0&&distance<=2;boolean changed=state.lastOrientation!=null&&(aim.x()!=state.lastOrientation.x()||aim.y()!=state.lastOrientation.y()||aim.z()!=state.lastOrientation.z());state.lastPosition=at;state.lastOrientation=aim;state.idleSeconds=moved||changed?0:state.idleSeconds+dt;state.activitySeen|=moved||changed;if(!state.activitySeen||state.idleSeconds>=60)state.afkSeconds+=dt;
        if(!p.life.alive()||!ObjectiveScores.playable(p))return;
        if(state.activitySeen&&state.idleSeconds<60){state.activeSeconds+=dt;state.opponentSampleSeconds-=dt;if(state.opponentSampleSeconds<=0){state.opponentSampleSeconds=1;int humans=0,bots=0;for(var other:players)if(ObjectiveScores.playable(other)&&other.team()!=p.team()){if(other.life.bot())bots++;else humans++;}state.humanOpponents=Math.max(state.humanOpponents,humans);state.botOpponents=Math.max(state.botOpponents,bots);}}
        if(moved&&!airborne)state.fractional(c("COMBAT_DISTANCE_RAN_TOTAL"),distance);
        String key=Objects.requireNonNull(mapName).toLowerCase(Locale.ROOT).codePoints().filter(ch->Character.isLetterOrDigit(ch)||Character.getType(ch)==Character.LETTER_NUMBER||Character.getType(ch)==Character.OTHER_NUMBER).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();Integer stat=MatchCatalog.mapStat(key);if(stat!=null)state.fractional(stat,dt/60.0);
    }
}
