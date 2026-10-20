// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.match.Scoreboard;
import net.battlespades.player.PlayerLife;
import net.battlespades.results.*;

/** Concrete round/score-reset/departure/shutdown result composition. Identity comes from a verified embedding service. */
public final class HostedRoundResults {
    public record Settings(boolean online,String serverId,String playlistId,String relayLobbyId){public Settings{ResultOutbox.server(serverId);Objects.requireNonNull(playlistId);relayLobbyId=relayLobbyId==null?"":relayLobbyId;}}
    private final Thread owner=Thread.currentThread();private final ApplicationHost host;private final Settings settings;private final ResultPublisher publisher;private final RoundResultLedger ledger;
    private final Function<JoinSession,Optional<RoundResultLedger.Identity>> identities;private final Function<String,List<String>> achievements;private final BiConsumer<String,Throwable> failure;
    private final IdentityHashMap<PlayerLife,Optional<RoundResultLedger.Identity>> bound=new IdentityHashMap<>();private StandardMatchDomain current;private CompletableFuture<Void> shutdown;private boolean closing;
    private static final class Capture {final ResultOutbox.Event event;final HostedAchievements service;final Map<String,List<String>> unlocked=new LinkedHashMap<>();final Map<String,Set<String>> eligible=new LinkedHashMap<>();@SuppressWarnings("unchecked") Capture(ResultOutbox.Event event,HostedAchievements service){this.event=event;this.service=service;for(var p:(List<Map<String,Object>>)event.payload().get("players")){String id="aosplay:"+p.get("steamid");eligible.put(id,service.eligible(id));}}}
    private final ArrayDeque<Capture> captures=new ArrayDeque<>();private CompletionStage<Void> enriching;private double retryAt;

    public HostedRoundResults(ApplicationHost host,Settings settings,ResultPublisher publisher,Function<JoinSession,Optional<RoundResultLedger.Identity>> verifiedIdentities,Function<String,List<String>> unreportedAchievements,Supplier<Instant> wall,BiConsumer<String,Throwable> failure){
        this.host=Objects.requireNonNull(host);this.settings=Objects.requireNonNull(settings);this.publisher=Objects.requireNonNull(publisher);if(!settings.serverId.equals(publisher.serverId()))throw new IllegalArgumentException("Result publisher server mismatch");identities=Objects.requireNonNull(verifiedIdentities);achievements=Objects.requireNonNull(unreportedAchievements);this.failure=Objects.requireNonNull(failure);ledger=new RoundResultLedger(wall,()->UUID.randomUUID().toString().replace("-",""));host.results(this);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Round results outside owner");}
    void admitted(StandardMatchDomain match,PlayerLife p){owned();if(!settings.online||closing||p.bot())return;var session=host.transitionSessions().stream().filter(s->s.playerId().orElse(-1)==p.id()&&s.metrics().state()!=JoinSession.State.CLOSED).findFirst();bound.put(p,session.flatMap(identities));}
    private RoundResultLedger.Player snapshot(Scoreboard.Player p){return RoundResultLedger.Player.from(p,bound.getOrDefault(p.life,Optional.empty()).orElse(null));}
    private List<RoundResultLedger.Player> snapshots(StandardMatchDomain match){return match.lives().players().stream().map(p->snapshot(match.binding().row(p))).toList();}
    private void capture(Integer winner,boolean finished){
        // Reserve immutable score evidence before any worker wait or scene retirement.
        if(captures.size()+publisher.pendingMemory()>=256)throw new IllegalStateException("Round result memory queue is full; check achievement handoff and durable outbox");publisher.reserveSlot();var service=host.achievements().orElse(null);
        ledger.capture(snapshots(current),winner,finished,service==null?achievements:id->List.of()).ifPresent(event->{if(service==null)publisher.offer(event);else captures.addLast(new Capture(event,service));});
        drain().whenComplete((unused,error)->{owned();if(error!=null)failure.accept("achievement result handoff",error);});
    }
    @SuppressWarnings("unchecked") private CompletionStage<Void> enrich(Capture capture){
        var payload=new LinkedHashMap<String,Object>(capture.event.payload());var players=((List<Map<String,Object>>)payload.get("players")).stream().map(p->new LinkedHashMap<String,Object>(p)).toList();payload.put("players",players);CompletionStage<Void> chain=CompletableFuture.completedFuture(null);
        for(var player:players){String id="aosplay:"+player.get("steamid");if(!capture.unlocked.containsKey(id))chain=chain.thenCompose(unused->capture.service.takeUnreported(id,capture.eligible.get(id)).thenAccept(names->{owned();capture.unlocked.put(id,List.copyOf(names));}));}
        return chain.thenRun(()->{owned();for(var player:players){var names=capture.unlocked.get("aosplay:"+player.get("steamid"));if(!names.isEmpty())player.put("achievements",names);}publisher.offer(ResultOutbox.Event.from(payload));captures.removeFirst();});
    }
    private CompletionStage<Void> drain(){owned();if(enriching!=null)return enriching;if(captures.isEmpty())return CompletableFuture.completedFuture(null);var result=new CompletableFuture<Void>();enriching=result.minimalCompletionStage();drainNext(result);return result.minimalCompletionStage();}
    private void drainNext(CompletableFuture<Void> result){
        CompletionStage<Void> next;try{next=enrich(captures.getFirst());}catch(RuntimeException error){next=CompletableFuture.failedFuture(error);}
        next.whenComplete((unused,error)->{owned();if(error!=null){enriching=null;result.completeExceptionally(error);}else if(captures.isEmpty()){enriching=null;result.complete(null);}else drainNext(result);});
    }
    void beforeReset(StandardMatchDomain match){owned();if(settings.online&&!closing&&current==match&&!ledger.completed())capture(null,false);}
    void resetScoreboard(StandardMatchDomain match){owned();if(settings.online&&!closing&&current==match)ledger.resetScoreboard(snapshots(match));}
    void begin(StandardMatchDomain match){owned();if(!settings.online||closing)return;if(current!=null&&current!=match&&!ledger.completed())capture(null,false);current=match;var snapshots=snapshots(match);ledger.begin(new RoundResultLedger.Context(settings.serverId,match.modeCode(),settings.playlistId,match.world().sourceCrc(),settings.relayLobbyId),snapshots);for(var p:match.binding().players().values())if(p.profileStats!=null){p.profileStats.humanOpponents=0;p.profileStats.botOpponents=0;p.profileStats.opponentSampleSeconds=0;p.profileStats.activitySeen=false;}}
    void depart(StandardMatchDomain match,PlayerLife p){owned();if(settings.online&&!closing&&current==match)ledger.depart(snapshot(match.binding().row(p)));bound.remove(p);}
    void schedule(StandardMatchDomain match,Integer winner){owned();if(!settings.online||closing||current!=match)return;try{capture(winner,true);}catch(RuntimeException error){failure.accept("result capture",error);}}
    void retire(StandardMatchDomain match){owned();if(current!=match)return;if(settings.online&&!closing&&!ledger.completed())capture(null,false);current=null;}
    void pulse(double now){owned();if(settings.online&&!closing){if(enriching==null&&!captures.isEmpty()&&now>=retryAt){retryAt=now+5;drain().whenComplete((unused,error)->{owned();if(error!=null)failure.accept("achievement result handoff",error);});}publisher.pulse(now);}}
    public int pendingMemory(){owned();return captures.size()+publisher.pendingMemory();}
    /** Await on the owner loop before closing the host, world, HTTP client or IO executor. Failed persistence can be retried. */
    public CompletionStage<Void> shutdown(){owned();if(shutdown!=null&&!shutdown.isCompletedExceptionally())return shutdown.minimalCompletionStage();if(!closing){if(settings.online&&current!=null&&!ledger.completed())capture(null,false);closing=true;}shutdown=drain().thenCompose(unused->{owned();return publisher.shutdown();}).toCompletableFuture();return shutdown.minimalCompletionStage();}
    boolean stopped(){owned();return shutdown!=null&&shutdown.isDone()&&!shutdown.isCompletedExceptionally();}
}
