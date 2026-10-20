// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.admin.*;
import net.battlespades.protocol.*;

/** Server-lifetime admin and admission owner. The caller owns/flushes BanStore and its writer after stopping this host. */
public final class HostedAdministration {
    private final Thread owner=Thread.currentThread();private final ApplicationHost host;private final BanStore bans;private final AdminAuthority authority;private final Function<JoinSession,Set<String>> verifiedIdentities;private final Executor resume;private final BiConsumer<String,Throwable> failure;
    private final IdentityHashMap<JoinSession,AdminAuthority.Principal> principals=new IdentityHashMap<>();
    /** verifiedIdentities must return independently authenticated keys. Ticket bytes, names and client claims are not proof. */
    public HostedAdministration(ApplicationHost host,AdminAuthority.Settings settings,BanStore bans,Function<JoinSession,Set<String>> verifiedIdentities,DoubleSupplier monotonic,Executor resume,BiConsumer<String,Throwable> failure){this.host=Objects.requireNonNull(host);this.bans=Objects.requireNonNull(bans);this.authority=new AdminAuthority(settings,monotonic);this.verifiedIdentities=Objects.requireNonNull(verifiedIdentities);this.resume=Objects.requireNonNull(resume);this.failure=Objects.requireNonNull(failure);host.administration(this);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Administration outside owner");}
    private Optional<JoinSession> session(int id){owned();return host.transitionSessions().stream().filter(s->s.playerId().orElse(-1)==id&&s.metrics().state()!=JoinSession.State.CLOSED).findFirst();}
    private AdminAuthority.Principal principal(JoinSession session){owned();return principals.computeIfAbsent(session,s->authority.principal(Objects.requireNonNull(verifiedIdentities.apply(s))));}
    public boolean admin(int id){owned();return session(id).map(s->authority.admin(principal(s))).orElse(false);}
    public boolean roomHost(int id){owned();return session(id).map(s->authority.roomHost(principal(s))).orElse(false);}
    Integer admissionReason(String address){owned();return bans.lookup(address).map(BanStore.Entry::disconnectReason).orElse(null);}
    void prune(){owned();principals.keySet().removeIf(s->s.metrics().state()==JoinSession.State.CLOSED);}
    void clear(){owned();principals.clear();}
    public Optional<JoinSession> connection(int id){return session(id);}
    private static void notice(JoinSession session,String text){if(session.metrics().state()!=JoinSession.State.CLOSED)session.direct(new SimplePackets.ChatMessage(255,2,text));}
    public CompletionStage<Void> credential(int id,String name,String raw,boolean claim){owned();var connection=session(id).orElse(null);if(connection==null)return CompletableFuture.completedFuture(null);var p=principal(connection);boolean previousHost=authority.roomHost(p);var result=claim?authority.claim(p,connection.addressHost(),raw):authority.login(p,connection.addressHost(),raw);notice(connection,result.message());if(claim&&!previousHost&&authority.roomHost(p)&&!result.kick())authority.roomPassword(p).ifPresent(password->notice(connection,passwordMessage(password)));
        if(!result.kick())return CompletableFuture.completedFuture(null);
        if(!result.banAddress()){connection.fail(2);return CompletableFuture.completedFuture(null);}
        CompletionStage<Void> saved;try{saved=bans.add(connection.addressHost(),name,"Too many failed admin login attempts",600);}catch(RuntimeException error){failure.accept("admin login ban",error);connection.fail(2);return CompletableFuture.completedFuture(null);}
        return saved.handleAsync((unused,error)->{if(error!=null)failure.accept("admin login ban",error);connection.fail(2);return null;},resume);
    }
    private static String passwordMessage(String password){return "Room admin password: "+password+" (share it only with people who should get admin: they type /admin <password>).";}
    public void roomPassword(int id){owned();session(id).ifPresent(s->{var p=principal(s);if(!authority.admin(p))return;if(!authority.playerHosted())notice(s,"Only player-hosted rooms show their admin password in game.");else authority.roomPassword(p).ifPresent(password->notice(s,passwordMessage(password)));});}
    public CompletionStage<Void> ban(int id,String name,String reason,long duration,Runnable persisted){owned();var connection=session(id).orElse(null);if(connection==null)return CompletableFuture.failedFuture(new IllegalStateException("Target has no active connection"));return bans.add(connection.addressHost(),name,reason,duration).thenRunAsync(()->{persisted.run();connection.fail(duration<=0?1:19);},resume);}
}
