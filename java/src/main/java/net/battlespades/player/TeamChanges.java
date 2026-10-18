// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.function.*;
import net.battlespades.match.Announcements;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;

/** Source voluntary/forced team moves; mode policy and team-bound cleanup remain explicit domain ports. */
public final class TeamChanges {
    public interface Effects {
        boolean allows(PlayerLife player,int team);boolean locked(int team);boolean assignsTeams();
        void retireDeployables(PlayerLife player);void removeTeam(PlayerLife player);void addTeam(PlayerLife player);
        void changed(PlayerLife player,int oldTeam,int newTeam);void send(PlayerLife player,GamePacket packet);
        void failure(String label,RuntimeException error);
    }
    private final Thread owner=Thread.currentThread();private final LifeRuntime lives;private final GameRules rules;private final AdmissionPolicy.Settings balance;private final double cooldown;private final DoubleSupplier monotonic,wall;private final Effects effects;
    private final IdentityHashMap<PlayerLife,Double> changed=new IdentityHashMap<>();
    public TeamChanges(LifeRuntime lives,GameRules rules,AdmissionPolicy.Settings balance,double cooldown,DoubleSupplier monotonic,DoubleSupplier wall,Effects effects){this.lives=Objects.requireNonNull(lives);this.rules=Objects.requireNonNull(rules);this.balance=Objects.requireNonNull(balance);if(!Double.isFinite(cooldown)||cooldown<0)throw new IllegalArgumentException("Team cooldown");this.cooldown=cooldown;this.monotonic=Objects.requireNonNull(monotonic);this.wall=Objects.requireNonNull(wall);this.effects=Objects.requireNonNull(effects);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Team change outside owner");}
    private void send(PlayerLife p,GamePacket packet){try{effects.send(p,packet);}catch(RuntimeException error){effects.failure("team notice",error);}}
    private void notice(PlayerLife p,String id){send(p,Announcements.localised(id,List.of(),false,false));}
    private void tell(PlayerLife p,String text){send(p,new SimplePackets.ChatMessage(255,2,text));}
    public boolean change(PlayerLife p,int team,boolean explain,boolean force){
        owned();if(!lives.current(p)||team!=0&&team!=2&&team!=3)return false;
        if(team==0&&!rules.enabled("RULE_ENABLE_SPECTATORS")){notice(p,"TEAM_SWITCH_NOT_ALLOWED");return false;}
        if(team==p.team()){if(explain)tell(p,"You're already on that team!");return false;}
        if(!effects.allows(p,team)){boolean locked=false;try{locked=(team==2||team==3)&&effects.locked(team);}catch(RuntimeException error){effects.failure("team lock presentation",error);}notice(p,locked?"TEAM_LOCKED":"TEAM_SWITCH_NOT_ALLOWED");return false;}
        double now=monotonic.getAsDouble();Double last=changed.get(p);
        if(!force&&p.team()!=0&&last!=null&&now-last<cooldown){notice(p,"TEAM_SWITCH_WAIT");if(explain)tell(p,"You can change team again in "+Math.max(1,(long)(cooldown-(now-last)+.999))+" second(s).");return false;}
        if(!force&&team!=0&&balance.autoBalance()&&!effects.assignsTeams()){
            var occupants=lives.players().stream().map(v->new AdmissionPolicy.Occupant(v.id(),v.name(),false,v.team())).toList();if(AdmissionPolicy.resolveTeam(team,true,balance,occupants,p.id(),true,null).internal()!=team){notice(p,"TEAM_FULL");return false;}
        }
        changed.put(p,now);int old=p.team();effects.retireDeployables(p);
        if(p.alive()&&lives.recentEnemyAttacker(p.id())!=null)lives.endLifeForTransition(p.id(),9);
        effects.removeTeam(p);p.team(team);effects.addTeam(p);if(p.alive())lives.die(p.id(),null,9);
        if(team==0)lives.spectator(p);else if(old==0)p.joinedDead(wall.getAsDouble(),p.lastDeath());effects.changed(p,old,team);return true;
    }
    public void forget(PlayerLife p){owned();changed.remove(p);}
}
