// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.function.*;
import net.battlespades.protocol.*;

/** Source kick denial, address cooldown, majority, localization and match exclusion policy. */
public final class KickBallot<P> {
    public interface Context<P> {
        Map<Integer,P> players();int id(P player);Integer team(P player);String name(P player);boolean eligible(P player);boolean bot(P player);
        String host(P player);boolean protectedHost(P player);void send(P player,GamePacket packet);void disconnect(P player,int reason);
    }
    public record Settings(double cooldown,double cancelledCooldown,int minimumTeamPlayers,double ratio){
        public Settings{if(!Double.isFinite(cooldown)||!Double.isFinite(cancelledCooldown)||!Double.isFinite(ratio))throw new IllegalArgumentException("Kick settings");}
        public static Settings defaults(){return new Settings(300,45,3,.5);}
    }
    private static final List<String> REASONS=List.of("KICK_REASON_GRIEFING","KICK_REASON_HACKING","KICK_REASON_ABUSE");
    private final Thread owner=Thread.currentThread();private final Context<P> context;private final Settings settings;private final BooleanSupplier blocked;private final DoubleSupplier monotonic,wall;private final Consumer<GamePacket> broadcast;private final BiConsumer<String,Throwable> failure;
    private final Map<Object,Double> cooldowns=new HashMap<>();private final Map<String,Integer> matchKicks=new HashMap<>();private final Map<Integer,Integer> votes=new LinkedHashMap<>();
    private boolean active;private Integer target,starter;private int reason;private String targetName="",starterName="";private double opened,deadline;private Object starterKey;
    public KickBallot(Context<P> context,Settings settings,BooleanSupplier blocked,DoubleSupplier monotonic,DoubleSupplier wall,Consumer<GamePacket> broadcast,BiConsumer<String,Throwable> failure){this.context=Objects.requireNonNull(context);this.settings=Objects.requireNonNull(settings);this.blocked=Objects.requireNonNull(blocked);this.monotonic=Objects.requireNonNull(monotonic);this.wall=Objects.requireNonNull(wall);this.broadcast=Objects.requireNonNull(broadcast);this.failure=Objects.requireNonNull(failure);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Kick ballot outside owner");}
    public boolean active(){owned();return active;}public Integer target(){owned();return target;}public Integer starter(){owned();return starter;}public Map<Integer,Integer> votes(){owned();return Map.copyOf(votes);}public Map<Object,Double> cooldowns(){owned();return Map.copyOf(cooldowns);}public Map<String,Integer> matchKicks(){owned();return Map.copyOf(matchKicks);}
    private String host(P player){try{String host=context.host(player);return host==null||host.isEmpty()||host.equals("unknown")?null:host;}catch(RuntimeException error){return null;}}
    private Object key(P player){String host=host(player);return host==null?context.id(player):"ip:"+host;}
    private long eligibleCount(){return context.players().values().stream().filter(context::eligible).mapToInt(context::id).distinct().count();}
    public int needed(){owned();return Math.max(1,(int)Math.ceil(Math.max(1,eligibleCount()-1)*settings.ratio()));}
    private static String name(String raw){String value=raw==null?"":raw.replace("\0","");int count=Math.min(64,value.codePointCount(0,value.length()));return value.substring(0,value.offsetByCodePoints(0,count));}
    private void deny(P player,String id,List<String> args){try{context.send(player,Announcements.localised(id,args,false,false));}catch(RuntimeException error){failure.accept("kick denial",error);}}
    public boolean start(P from,P to,int selectedReason,double now){
        owned();if(to==null||selectedReason<0||selectedReason>=REASONS.size()||context.players().get(context.id(from))!=from||!context.eligible(from))return false;
        cooldowns.entrySet().removeIf(entry->now>=entry.getValue());String denial=null;List<String> args=List.of();Integer team=context.team(from);
        if(Objects.equals(team,0))denial="KICK_DENIED_FOR_SPECTATOR";
        else if(context.id(to)==context.id(from))denial="KICK_DENIED_REASON_SELF_KICK";
        else{
            boolean isHost=false;try{isHost=context.protectedHost(to);}catch(RuntimeException error){failure.accept("kick host check",error);}
            if(isHost)denial="KICK_DENIED_REASON_KICK_HOST";
            else if(active||blocked.getAsBoolean())denial="KICK_DENIED_REASON_VOTE_IN_PROGRESS";
            else{var until=cooldowns.get(key(from));if(until!=null&&now<until){denial="KICK_DENIED_REASON_VOTE_TOO_SOON";args=List.of(Long.toString(Math.max(1,(long)Math.ceil(until-now))));}
                else if(team!=null&&context.players().values().stream().filter(p->Objects.equals(context.team(p),team)).filter(p->!context.bot(p)).count()<settings.minimumTeamPlayers())denial="KICK_NOT_ENOUGH_PLAYERS";}
        }
        if(denial!=null){deny(from,denial,args);return false;}if(context.players().get(context.id(to))!=to||!context.eligible(to))return false;
        active=true;target=context.id(to);starter=context.id(from);reason=selectedReason;targetName=name(context.name(to));starterName=name(context.name(from));votes.clear();votes.put(starter,0);opened=now;deadline=monotonic.getAsDouble()+30;starterKey=key(from);cooldowns.put(starterKey,now+settings.cooldown());broadcast.accept(packet(0,null));return true;
    }
    private long yes(){return votes.values().stream().filter(index->index==0).count();}
    public void cast(P voter,int index){owned();expire();int id=context.id(voter);if(!active||index<0||index>1||Objects.equals(target,id)||context.players().get(id)!=voter||!context.eligible(voter)||Objects.equals(votes.get(id),index))return;votes.put(id,index);broadcast.accept(packet(2,null));if(yes()>=needed())resolve(true,false);}
    public void castWire(P voter,String token){owned();if(!active)return;int index=token.equals(VoteText.localised("KICK_YES",List.of()))?0:token.equals(VoteText.localised("KICK_NO",List.of()))?1:-1;cast(voter,index);}
    public void tick(Double now){owned();if(active&&(now!=null&&now-opened>=30||monotonic.getAsDouble()>=deadline))resolve(yes()>=needed(),false);}
    private void expire(){if(active&&monotonic.getAsDouble()>=deadline)resolve(yes()>=needed(),false);}
    public void reveal(Consumer<GamePacket> send){owned();expire();if(active)send.accept(packet(0,null));}
    public void cancel(boolean byStarter,Double now){owned();if(!active)return;if(byStarter&&starterKey!=null)cooldowns.put(starterKey,(now==null?wall.getAsDouble():now)+settings.cancelledCooldown());resolve(false,byStarter);}
    public void forget(int id){owned();cooldowns.remove(id);if(active&&(Objects.equals(target,id)||Objects.equals(starter,id))){cancel(false,null);return;}if(votes.remove(id)!=null&&active)broadcast.accept(packet(2,null));}
    public Integer matchKickReason(String address){owned();return matchKicks.get(address);}public void clearMatchKicks(){owned();matchKicks.clear();}
    private VotePacket packet(int type,String result){return new VotePacket(starter==null?255:starter,type,List.of(new VotePacket.Candidate(VoteText.localised("KICK_YES",List.of()),(int)yes()),new VotePacket.Candidate(VoteText.localised("KICK_NO",List.of()),(int)votes.values().stream().filter(i->i==1).count())),result==null?VoteText.localised("VOTE_TO_KICK_TITLE",List.of()):result,VoteText.vote("VOTE_TO_KICK_DESCRIPTION",List.of(targetName,REASONS.get(reason),starterName),List.of(1)),1,type==3?0:1);}
    private void resolve(boolean passed,boolean cancelled){
        P victim=context.players().get(target);passed=passed&&victim!=null;String result=passed?VoteText.vote("VOTE_KICK_SUCCESSFUL",List.of(targetName,starterName,REASONS.get(reason)),List.of(2)):cancelled?VoteText.vote("VOTE_KICK_CANCELLED",List.of(targetName,starterName),List.of()):VoteText.vote("VOTE_KICK_UNSUCCESSFUL",List.of(starterName,targetName),List.of());
        broadcast.accept(packet(3,result));int disconnect=23+reason;active=false;target=null;starter=null;votes.clear();if(passed){String host=host(victim);if(host!=null)matchKicks.put(host,disconnect);try{context.disconnect(victim,disconnect);}catch(RuntimeException error){failure.accept("kick disconnect",error);}}
    }
}
