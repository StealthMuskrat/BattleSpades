// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.player.NameUnicode;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerSequence;

/** Source next-map ballot lifecycle. Ranked/discovered candidate selection and kick votes are separate services. */
public final class MapBallot<P> implements AutoCloseable {
    public static final double DURATION=((Number)SourceCatalog.load("round-media").get("mapVoteSeconds")).doubleValue();
    private final OwnerSequence owner;
    private final Supplier<Map<Integer,P>> players;
    private final ToIntFunction<P> id;
    private final Predicate<P> eligible;
    private final DoubleSupplier monotonic;
    private final Function<Double,CompletionStage<Void>> sleep;
    private final Consumer<GamePacket> broadcast;
    private final Map<String,String> available;
    private final BooleanSupplier blocked;
    private Ballot ballot;
    private String next;
    private boolean closed;
    private final class Ballot {
        final List<String> candidates;final Map<Integer,Integer> votes=new LinkedHashMap<>();final Set<Waiter> waiters=new LinkedHashSet<>();final double opened,deadline;boolean active=true;
        Ballot(List<String> candidates,double opened){this.candidates=List.copyOf(candidates);this.opened=opened;deadline=monotonic.getAsDouble()+DURATION;}
    }
    private final class Waiter {
        final Ballot selected;final CompletableFuture<String> result=new CompletableFuture<>();CompletableFuture<Void> timer;
        Waiter(Ballot selected){this.selected=selected;}
        void finish(Throwable error){
            if(result.isDone())return;
            if(error!=null){result.completeExceptionally(OwnerSequence.cause(error));return;}
            if(ballot!=selected){result.complete(null);return;}
            if(selected.active)resolve();result.complete(next);
        }
    }
    public MapBallot(List<String> available,Supplier<Map<Integer,P>> players,ToIntFunction<P> id,Predicate<P> eligible,DoubleSupplier monotonic,Function<Double,CompletionStage<Void>> sleep,Consumer<GamePacket> broadcast,Executor resume){
        this(available,players,id,eligible,monotonic,sleep,broadcast,resume,()->false);
    }
    public MapBallot(List<String> available,Supplier<Map<Integer,P>> players,ToIntFunction<P> id,Predicate<P> eligible,DoubleSupplier monotonic,Function<Double,CompletionStage<Void>> sleep,Consumer<GamePacket> broadcast,Executor resume,BooleanSupplier blocked){
        owner=new OwnerSequence(resume);this.players=Objects.requireNonNull(players);this.id=Objects.requireNonNull(id);this.eligible=Objects.requireNonNull(eligible);this.monotonic=Objects.requireNonNull(monotonic);this.sleep=Objects.requireNonNull(sleep);this.broadcast=Objects.requireNonNull(broadcast);
        this.blocked=Objects.requireNonNull(blocked);
        var names=new LinkedHashMap<String,String>();for(var name:available)names.put(NameUnicode.casefold(name),name);this.available=Map.copyOf(names);
    }
    public boolean active(){owner.owned();return ballot!=null&&ballot.active;}
    public List<String> candidates(){owner.owned();return active()?ballot.candidates:List.of();}
    public Map<Integer,Integer> votes(){owner.owned();return active()?Map.copyOf(ballot.votes):Map.of();}
    public String nextMap(){owner.owned();return next;}
    public boolean start(List<String> raw,double wallNow){
        owner.owned();if(closed||active()||blocked.getAsBoolean())return false;var normalized=new ArrayList<String>();var seen=new HashSet<String>();
        for(var rawName:raw){String value=NameUnicode.strip(rawName);if(value.isEmpty()||value.equals(".")||value.indexOf('/')>=0)continue;int dot=value.lastIndexOf('.');if(dot>0&&value.substring(dot).equalsIgnoreCase(".vxl"))value=value.substring(0,dot);
            try{VoteText.dynamic(value);}catch(IllegalArgumentException invalid){continue;}String key=NameUnicode.casefold(value);if(!available.isEmpty()){value=available.get(key);if(value==null)continue;key=NameUnicode.casefold(value);}if(!seen.add(key))continue;normalized.add(value);if(normalized.size()==3)break;
        }
        if(normalized.isEmpty())return false;ballot=new Ballot(normalized,wallNow);next=null;broadcast.accept(packet(0));return true;
    }
    public void cast(P voter,int index){
        owner.owned();expire();int key=id.applyAsInt(voter);if(!active()||index<0||index>=ballot.candidates.size()||players.get().get(key)!=voter||!eligible.test(voter)||Objects.equals(ballot.votes.get(key),index))return;
        ballot.votes.put(key,index);broadcast.accept(packet(2));if(ballot.votes.size()>=eligibleIds(-1).size())resolve();
    }
    public void castCandidate(P voter,String name){owner.owned();if(!active())return;String key=NameUnicode.casefold(NameUnicode.strip(name));int index=-1;for(int i=0;i<ballot.candidates.size();i++)if(NameUnicode.casefold(ballot.candidates.get(i)).equals(key)){index=i;break;}cast(voter,index);}
    public void castWire(P voter,String token){owner.owned();if(!active())return;int index=-1;for(int i=0;i<ballot.candidates.size();i++)if(VoteText.dynamic(ballot.candidates.get(i)).equals(token)){index=i;break;}cast(voter,index);}
    public void packet(P voter,GamePacket packet){owner.owned();if(packet instanceof VotePacket vote&&vote.messageType()==1&&!vote.candidates().isEmpty())castWire(voter,vote.candidates().getFirst().name());}
    private Set<Integer> eligibleIds(int excluded){var result=new HashSet<Integer>();for(var player:players.get().values()){int key=id.applyAsInt(player);if(key!=excluded&&eligible.test(player))result.add(key);}return result;}
    public void tick(Double wallNow){owner.owned();if(active()&&(wallNow!=null&&wallNow-ballot.opened>=DURATION||monotonic.getAsDouble()>=ballot.deadline))resolve();}
    private void expire(){if(active()&&monotonic.getAsDouble()>=ballot.deadline)resolve();}
    public void reveal(Consumer<GamePacket> send){owner.owned();expire();if(active())send.accept(packet(0));}
    public void forget(int playerId){
        owner.owned();if(!active())return;if(ballot.votes.remove(playerId)!=null)broadcast.accept(packet(2));var remaining=eligibleIds(playerId);if(!remaining.isEmpty()&&ballot.votes.keySet().containsAll(remaining))resolve();
    }
    public void cancel(){owner.owned();if(!active())return;broadcast.accept(packet(3));clear();}
    /** Disposal after transport shutdown releases waits/timers without attempting another HUD send. */
    @Override public void close(){owner.owned();closed=true;next=null;if(active())clear();}
    public String consume(){owner.owned();var value=next;next=null;return value;}
    private List<Integer> counts(){var counts=new ArrayList<>(Collections.nCopies(ballot.candidates.size(),0));for(int index:ballot.votes.values())if(index>=0&&index<counts.size())counts.set(index,counts.get(index)+1);return counts;}
    private VotePacket packet(int type){
        var counts=counts();var candidates=new ArrayList<VotePacket.Candidate>();for(int i=0;i<ballot.candidates.size();i++)candidates.add(new VotePacket.Candidate(VoteText.dynamic(ballot.candidates.get(i)),counts.get(i)));
        String title=type==3&&next!=null?VoteText.localised("MAP_VOTED_MESSAGE",List.of(next)):VoteText.localised("VOTE_MAP_TITLE",List.of());return new VotePacket(255,type,candidates,title,VoteText.localised("VOTE_MAP_DESCRIPTION",List.of()),1,type==3?0:1);
    }
    private void resolve(){
        if(!active())return;var counts=counts();int best=0;for(int i=1;i<counts.size();i++)if(counts.get(i)>counts.get(best))best=i;next=ballot.candidates.get(best);broadcast.accept(Announcements.localised("MAP_VOTED_MESSAGE",List.of(next),false,false));broadcast.accept(packet(3));clear();
    }
    private void clear(){var old=ballot;old.active=false;old.votes.clear();for(var waiter:List.copyOf(old.waiters))waiter.finish(null);}
    /** Each waiter has an independent fallback timeout; cancellation removes only its wait, never the ballot. */
    public CompletionStage<String> waitForResult(){
        owner.owned();if(!active())return CompletableFuture.completedFuture(next);var selected=ballot;var waiter=new Waiter(selected);selected.waiters.add(waiter);
        waiter.result.whenComplete((value,error)->owner.resume(()->{selected.waiters.remove(waiter);if(waiter.timer!=null)waiter.timer.cancel(false);},new CompletableFuture<>()));
        double remaining=Math.max(0,selected.deadline-monotonic.getAsDouble());if(remaining==0){waiter.finish(null);return waiter.result;}
        try{waiter.timer=Objects.requireNonNull(sleep.apply(remaining)).toCompletableFuture();waiter.timer.whenComplete((unused,error)->owner.resume(()->waiter.finish(error),waiter.result));if(waiter.result.isDone())waiter.timer.cancel(false);}
        catch(RuntimeException error){waiter.finish(error);}return waiter.result;
    }
}
