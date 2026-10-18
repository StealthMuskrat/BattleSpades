// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.match.Announcements;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerSequence;

/** Source mid-match policy. Bot retirement is awaited; all continuations remain on the gameplay owner. */
public final class TeamBalancer {
    public record Settings(boolean enabled,boolean midMatch,int threshold,double interval,double grace,double cooldown,double botWait) {
        public Settings {threshold=Math.max(2,threshold);interval=number(interval,1);grace=number(grace,5);cooldown=number(cooldown,600);botWait=number(botWait,10);}
        private static double number(double v,double fallback){return Double.isNaN(v)?0:Math.max(0,v);}
    }
    public record Status(boolean started,boolean ended,boolean retiring,boolean assignsTeams,boolean autoBalance){}
    public record Objectives(Collection<PlayerLife> vips,Set<Integer> patientZero,Integer lastSurvivor) {
        public Objectives {vips=List.copyOf(vips);patientZero=Set.copyOf(patientZero);}
    }
    public interface Bots {
        boolean started();boolean reconnecting();List<PlayerLife> players();boolean safeToRetire(PlayerLife p);
        CompletionStage<Boolean> remove(PlayerLife p);CompletionStage<Void> add(int team);
    }
    public interface Policy {
        Settings settings();Status status();boolean transitionBusy();Objectives objectives();boolean retireSafe(PlayerLife p);
        boolean allows(PlayerLife p,int team);boolean inGame(PlayerLife p);long captures(PlayerLife p);long score(PlayerLife p);
        boolean change(PlayerLife p,int team);Bots bots();void send(PlayerLife p,GamePacket packet);void failure(String label,Throwable error);
    }
    private record Arrival(PlayerLife player,double time){}
    private final Supplier<List<PlayerLife>> roster;private final Policy policy;private final DoubleSupplier clock;private final OwnerSequence owner;
    private final Map<Integer,Arrival> arrivals=new HashMap<>();private double nextCheck;private Double unevenSince;private int botMoves,humanMoves;private boolean active;
    public TeamBalancer(Supplier<List<PlayerLife>> roster,Policy policy,DoubleSupplier clock,Executor resume){this.roster=Objects.requireNonNull(roster);this.policy=Objects.requireNonNull(policy);this.clock=Objects.requireNonNull(clock);owner=new OwnerSequence(resume);}
    public int botMoves(){owner.owned();return botMoves;}public int humanMoves(){owner.owned();return humanMoves;}
    public double joinedAt(PlayerLife p){owner.owned();var a=arrivals.get(p.id());return a!=null&&a.player()==p?a.time():0;}
    private boolean applicable(boolean manual){var s=policy.settings();if(!manual&&(!s.enabled()||!s.midMatch()))return false;var m=policy.status();if(m==null||!m.started()||m.ended()||m.retiring()||m.assignsTeams()||!m.autoBalance())return false;try{return !policy.transitionBusy();}catch(RuntimeException error){return false;}}
    private void remember(double now){var seen=new HashSet<Integer>();for(var p:roster.get()){seen.add(p.id());var a=arrivals.get(p.id());if(a==null||a.player()!=p)arrivals.put(p.id(),new Arrival(p,now));}arrivals.keySet().retainAll(seen);}
    public Map<Integer,Integer> counts(){owner.owned();var counts=new LinkedHashMap<Integer,Integer>();counts.put(2,0);counts.put(3,0);for(var p:roster.get())if(counts.containsKey(p.team()))counts.merge(p.team(),1,Integer::sum);return counts;}
    private boolean safe(PlayerLife p){if(p.pickupId()!=null)return false;var o=policy.objectives();for(var vip:o.vips())if(vip==p)return false;if(o.patientZero().contains(p.id())||Objects.equals(o.lastSurvivor(),p.id()))return false;try{return policy.retireSafe(p);}catch(RuntimeException error){return false;}}
    private boolean allows(PlayerLife p,int team){try{return policy.allows(p,team);}catch(RuntimeException error){return false;}}
    private boolean movable(PlayerLife p,int team){return !p.alive()&&(p.bot()||policy.inGame(p))&&safe(p)&&allows(p,team);}
    private Bots director(){var b=policy.bots();return b!=null&&b.started()&&!b.reconnecting()?b:null;}
    private boolean hasBot(int big){var b=director();return b!=null&&List.copyOf(b.players()).stream().anyMatch(p->p.team()==big&&safe(p)&&allows(p,5-big));}
    private static <T> CompletionStage<T> ready(T value){return CompletableFuture.completedFuture(value);}
    private <T> CompletionStage<T> await(CompletionStage<T> stage,BiFunction<T,Throwable,CompletionStage<T>> operation){var result=new CompletableFuture<T>();stage.whenComplete((value,error)->owner.resume(()->{var next=operation.apply(value,error==null?null:OwnerSequence.cause(error));next.whenComplete((v,e)->{if(e==null)result.complete(v);else result.completeExceptionally(OwnerSequence.cause(e));});},result));return result.minimalCompletionStage();}
    private CompletionStage<Boolean> moveBot(int big,int small,boolean retire){
        var b=director();if(b==null)return ready(false);var candidates=List.copyOf(b.players()).stream().filter(p->p.team()==big).sorted(Comparator.comparing(PlayerLife::alive).thenComparingInt(PlayerLife::id)).toList();
        for(var p:candidates)if(movable(p,small)&&policy.change(p,small))return ready(true);
        if(!retire)return ready(false);return retire(b,candidates.iterator(),small);
    }
    private CompletionStage<Boolean> retire(Bots b,Iterator<PlayerLife> candidates,int small){
        while(candidates.hasNext()){var p=candidates.next();if(!safe(p)||!allows(p,small)||!b.safeToRetire(p))continue;
            return await(b.remove(p),(removed,error)->{if(error!=null)return CompletableFuture.failedFuture(error);if(!removed)return retire(b,candidates,small);
                CompletionStage<Void> added;try{added=b.add(small);}catch(RuntimeException failure){if(failure instanceof CancellationException)throw failure;policy.failure("replacement bot",failure);return ready(true);}
                var result=new CompletableFuture<Boolean>();added.whenComplete((unused,failure)->owner.resume(()->{var root=failure==null?null:OwnerSequence.cause(failure);if(root instanceof Error||root instanceof CancellationException){result.completeExceptionally(root);return;}if(root!=null)policy.failure("replacement bot",root);result.complete(true);},result));return result.minimalCompletionStage();});
        }return ready(false);
    }
    private boolean moveHuman(int big,int small,double now,boolean cooldown){
        double wait=cooldown?policy.settings().cooldown():0;var candidates=roster.get().stream().filter(p->!p.bot()&&p.team()==big&&!(p.balanceMovedAt()!=null&&now-p.balanceMovedAt()<wait)&&movable(p,small)).sorted(Comparator.<PlayerLife,Boolean>comparing(p->policy.captures(p)>0).thenComparingLong(policy::score).thenComparing(Comparator.comparingDouble(this::joinedAt).reversed()).thenComparing(Comparator.comparingInt(PlayerLife::id).reversed())).toList();
        for(var p:candidates)if(policy.change(p,small)){p.balanceMovedAt(now);notice(p,Announcements.localised("TEAM_FULL",List.of(),false,false));notice(p,new SimplePackets.ChatMessage(255,2,"Teams were uneven: you will respawn on the other team."));return true;}return false;
    }
    private void notice(PlayerLife p,GamePacket packet){try{policy.send(p,packet);}catch(RuntimeException error){policy.failure("auto-balance notice",error);}}
    private <T> CompletionStage<T> guarded(Supplier<CompletionStage<T>> operation){owner.owned();if(active)throw new IllegalStateException("Team balance already suspended");active=true;var result=new CompletableFuture<T>();try{operation.get().whenComplete((value,error)->owner.resume(()->{active=false;if(error==null)result.complete(value);else result.completeExceptionally(OwnerSequence.cause(error));},result));}catch(Throwable error){active=false;result.completeExceptionally(error);}return result.minimalCompletionStage();}
    public CompletionStage<Boolean> tick(double now){return guarded(()->{
        if(now<nextCheck)return ready(false);nextCheck=now+Math.max(0,policy.settings().interval()-.1);remember(now);if(!applicable(false)){unevenSince=null;return ready(false);}var counts=counts();int big=counts.get(2)>=counts.get(3)?2:3,small=5-big;if(counts.get(big)-counts.get(small)<policy.settings().threshold()){unevenSince=null;return ready(false);}if(unevenSince==null)unevenSince=now;double waited=now-unevenSince;if(waited<policy.settings().grace())return ready(false);
        return await(moveBot(big,small,waited>=policy.settings().botWait()),(moved,error)->{if(error!=null)return CompletableFuture.failedFuture(error);if(moved){botMoves++;unevenSince=now;return ready(true);}if(hasBot(big)&&waited<policy.settings().botWait())return ready(false);if(moveHuman(big,small,now,true)){humanMoves++;unevenSince=now;return ready(true);}return ready(false);});
    });}
    public CompletionStage<Integer> force(){return guarded(()->{if(!applicable(true))return ready(0);int[] moved={0};boolean[] stop={false};var steps=new ArrayList<Supplier<CompletionStage<Void>>>();for(int i=0;i<64;i++)steps.add(()->{if(stop[0])return ready(null);var counts=counts();int big=counts.get(2)>=counts.get(3)?2:3,small=5-big;if(counts.get(big)-counts.get(small)<2){stop[0]=true;return ready(null);}return moveBot(big,small,true).thenAccept(bot->{owner.owned();if(bot){botMoves++;moved[0]++;}else if(moveHuman(big,small,clock.getAsDouble(),false)){humanMoves++;moved[0]++;}else stop[0]=true;});});return owner.run(steps.iterator()).thenApply(unused->moved[0]);});}
}
