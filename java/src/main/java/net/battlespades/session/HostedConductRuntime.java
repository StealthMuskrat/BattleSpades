// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.transport.enet.Delivery;
import org.tomlj.TomlTable;

/** Accepted teammate harm, decaying bounded incidents and real-input AFK clocks. */
public final class HostedConductRuntime {
    public record Settings(boolean griefEnabled,double kick,double warn,double decay,double killPoints,double hpPoints,double incident,double cap,boolean griefAdmins,double afk,double afkWarn,double spectators,boolean afkAdmins,boolean announce){
        public static Settings from(TomlTable raw){return new Settings(flag(raw,"grief_kick_enabled",true),number(raw,"grief_kick_points",10),number(raw,"grief_warn_points",5),number(raw,"grief_decay_seconds",60),number(raw,"grief_team_kill_points",3),number(raw,"grief_team_damage_points",1),number(raw,"grief_incident_seconds",3),number(raw,"grief_incident_max_points",6),flag(raw,"grief_exempt_admins",true),number(raw,"afk_kick_seconds",600),number(raw,"afk_warn_seconds",540),number(raw,"afk_spectator_kick_seconds",1800),flag(raw,"afk_exempt_admins",true),flag(raw,"announce_kicks",true));}
        private static boolean flag(TomlTable raw,String name,boolean fallback){var value=raw.get("conduct."+name);if(value==null)return fallback;if(value instanceof Boolean b)return b;throw new IllegalArgumentException("conduct."+name+" must be boolean");}
        private static double number(TomlTable raw,String name,double fallback){var value=raw.get("conduct."+name);if(value==null)return fallback;if(value instanceof Number n&&Double.isFinite(n.doubleValue()))return Math.max(0,n.doubleValue());throw new IllegalArgumentException("conduct."+name+" must be finite numeric");}
    }
    public record Snapshot(double griefPoints,long teamKills,long teamDamage,double idleSeconds,boolean kickPending){}
    private static final class State {double points,incidentUntil,incidentPoints,idle;Double stamp;Integer movement,actions;Vector orientation;boolean griefWarn,afkWarn,pending,kicked;long kills,damage;}
    private final StandardMatchDomain match;private final ApplicationHost host;private final Settings settings;private final Map<PlayerLife,State> states=new IdentityHashMap<>();
    public HostedConductRuntime(StandardMatchDomain match,ApplicationHost host,Settings settings){this.match=Objects.requireNonNull(match);this.host=Objects.requireNonNull(host);this.settings=Objects.requireNonNull(settings);}
    private State state(PlayerLife p){return states.computeIfAbsent(p,key->new State());}
    public Snapshot snapshot(PlayerLife p){var s=state(p);decay(s,match.monotonic());return new Snapshot(s.points,s.kills,s.damage,s.idle,s.pending);}
    public void activity(PlayerLife p){var s=state(p);s.idle=0;s.afkWarn=false;}
    public void input(PlayerLife p,LiveClientInput input){
        var s=state(p);int movement=input.movementFlags(),actions=input.actionFlags()&135;var aim=new Vector(input.x(),input.y(),input.z());boolean active=s.movement==null||s.movement!=movement||s.actions==null&&actions!=0||s.actions!=null&&s.actions!=actions;
        if(s.orientation==null||Math.abs(aim.x()-s.orientation.x())>.02||Math.abs(aim.y()-s.orientation.y())>.02||Math.abs(aim.z()-s.orientation.z())>.02){active=true;s.orientation=aim;}s.movement=movement;s.actions=actions;if(active)activity(p);
    }
    private void decay(State s,double now){double elapsed=s.stamp==null?0:Math.max(0,now-s.stamp);if(settings.decay>0&&elapsed>0)s.points=Math.max(0,s.points-elapsed/settings.decay);s.stamp=now;if(s.points<settings.warn*.5)s.griefWarn=false;}
    public double harm(LifeRuntime.DamageEvidence evidence){
        var source=evidence.source();var victim=evidence.victim();if(source==null||source==victim||source.bot()||source.team()==0||source.team()!=victim.team()||LifeCatalog.TRANSITIONS.contains(evidence.killType())||settings.griefAdmins&&match.admin(source))return 0;
        int amount=Math.max(0,evidence.removed());if(amount<=0&&!evidence.killed())return 0;var s=state(source);double now=match.monotonic();decay(s,now);double added=amount/100.0*settings.hpPoints;if(evidence.killed()){added+=settings.killPoints;s.kills++;}s.damage+=amount;
        if(!(s.incidentUntil-settings.incident<=now&&now<=s.incidentUntil)){s.incidentUntil=now+settings.incident;s.incidentPoints=0;}if(settings.cap>0)added=Math.max(0,Math.min(added,settings.cap-s.incidentPoints));s.incidentPoints+=added;s.points+=added;
        if(!s.pending){if(settings.griefEnabled&&settings.kick>0&&s.points>=settings.kick)s.pending=true;else if(!s.griefWarn&&s.points>=settings.warn){s.griefWarn=true;privateMessage(source,"Warning: stop hurting your teammates or you will be kicked for griefing.");}}return added;
    }
    private void privateMessage(PlayerLife p,String text){try{host.sendPlayer(p.id(),new SimplePackets.ChatMessage(255,2,text));}catch(RuntimeException failure){match.failure("conduct private message",failure);}}
    private void kick(PlayerLife p,int reason,String text){host.disconnectPlayer(p.id(),reason);if(settings.announce)try{host.broadcast(new SimplePackets.ChatMessage(255,2,text),Delivery.RELIABLE);}catch(RuntimeException failure){match.failure("conduct announcement",failure);}}
    public void tick(int loop){if(loop%60!=0)return;double now=match.monotonic();for(var p:match.lives().players()){if(p.bot())continue;var s=state(p);if(s.pending){s.pending=false;s.points=0;kick(p,23,p.name()+" was kicked for team griefing.");continue;}decay(s,now);double limit=settings.afk<=0?0:p.team()==0?settings.spectators:settings.afk;if(limit<=0||settings.afkAdmins&&match.admin(p)||s.kicked||!host.inGame(p.id())||match.mode().clock().ended||!match.mode().started()||p.team()!=0&&!p.alive())continue;
            s.idle+=1;if(s.idle>=limit){s.kicked=true;kick(p,16,p.name()+" was kicked for being AFK.");continue;}double warn=limit==settings.afk?settings.afkWarn:Math.max(0,limit-(settings.afk-settings.afkWarn));if(!s.afkWarn&&warn>0&&warn<limit&&s.idle>=warn){s.afkWarn=true;privateMessage(p,"You are AFK. Move or you will be kicked in "+Math.max(1,(int)Math.rint(limit-s.idle))+" seconds.");}
        }}
    public void forget(PlayerLife p){states.remove(p);}public void clear(){states.clear();}
}
