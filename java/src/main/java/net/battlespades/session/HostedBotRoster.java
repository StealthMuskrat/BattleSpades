// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.function.Supplier;
import net.battlespades.bot.BotCombatProfiles;
import net.battlespades.player.*;
import net.battlespades.protocol.SimplePackets;

/** Peerless life ownership. Slot reservations survive until ordinary departure completes. */
public final class HostedBotRoster implements AutoCloseable {
    public record Member(PlayerLife player,int generation,StandardMatchDomain match){}
    private record Lease(ApplicationHost.PeerlessSlot slot,StandardMatchDomain match){}
    private final Thread owner=Thread.currentThread();private final ApplicationHost host;private final Supplier<StandardMatchDomain> current;
    private final Map<Integer,Lease> leases=new LinkedHashMap<>();private final Map<Integer,Member> members=new LinkedHashMap<>();private final Map<Integer,Integer> generations=new HashMap<>();
    public HostedBotRoster(ApplicationHost host,Supplier<StandardMatchDomain> current){this.host=Objects.requireNonNull(host);this.current=Objects.requireNonNull(current);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Bot roster outside owner");}
    public List<Member> members(){owned();return List.copyOf(members.values());}
    public Optional<Member> add(String name,int team,ClassCatalog.Selection selection){
        owned();if(team!=2&&team!=3)throw new IllegalArgumentException("Playable bot team required");
        Objects.requireNonNull(selection);var match=current.get();var reserved=host.reservePeerless();if(reserved.isEmpty())return Optional.empty();
        var slot=reserved.get();int id=slot.id();leases.put(id,new Lease(slot,match));
        try{
            match.admitBot(id,new SimplePackets.NewPlayerConnection(team,selection.classId(),0,0,name),selection,0);
            var p=match.lives().player(id);int generation=generations.merge(id,1,Integer::sum);var member=new Member(p,generation,match);members.put(id,member);
            selectWeapon(p);match.lives().restock(id,0,true);return Optional.of(member);
        }catch(RuntimeException failure){try{release(id);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    public boolean current(Member member){owned();return members.get(member.player().id())==member&&member.match()==current.get()&&member.match().lives().players().stream().anyMatch(p->p==member.player());}
    private static void selectWeapon(PlayerLife p){var loadout=p.selection().loadout();int selected=loadout.stream().filter(BotCombatProfiles.FIREARMS::contains).findFirst().orElseGet(()->loadout.stream().filter(net.battlespades.combat.CombatCatalog.SPADES::contains).findFirst().orElse(loadout.isEmpty()?6:loadout.getFirst()));p.fire().tool(selected,true);}
    public boolean remove(Member member){owned();if(members.get(member.player().id())!=member)return false;release(member.player().id());return true;}
    private void release(int id){
        var lease=leases.get(id);if(lease==null)return;
        if(lease.match.lives().players().stream().anyMatch(p->p.id()==id))lease.match.disconnected(id);
        // A throwing cleanup retains both the reservation and lease for retry.
        lease.slot.close();leases.remove(id);members.remove(id);
    }
    @Override public void close(){owned();RuntimeException failure=null;for(int id:new ArrayList<>(leases.keySet()))try{release(id);}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}if(failure!=null)throw failure;}
}
