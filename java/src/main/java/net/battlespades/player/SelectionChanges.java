// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.function.DoubleSupplier;
import net.battlespades.protocol.*;

/** Packet13/78 atomic selections, source transition credit and live-death throttle. */
public final class SelectionChanges {
    public interface Policy {
        ClassCatalog.Selection normalize(int requested,List<Integer> tools,List<String> prefabs,List<Integer> ugc,int fallback);
        boolean selectable(int classId);boolean allows(PlayerLife player,ClassCatalog.Selection selection);
        boolean liveUgc(PlayerLife player,ClassCatalog.Selection selection);
        void send(PlayerLife player,GamePacket packet);void broadcast(GamePacket packet);void failure(String label,RuntimeException error);
    }
    private final Thread owner=Thread.currentThread();private final LifeRuntime lives;private final Policy policy;private final DoubleSupplier monotonic;private final IdentityHashMap<PlayerLife,Double> deaths=new IdentityHashMap<>();
    public SelectionChanges(LifeRuntime lives,Policy policy,DoubleSupplier monotonic){this.lives=Objects.requireNonNull(lives);this.policy=Objects.requireNonNull(policy);this.monotonic=Objects.requireNonNull(monotonic);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Selection outside owner");}
    private boolean allowed(PlayerLife p,ClassCatalog.Selection s){return policy.selectable(s.classId())&&policy.allows(p,s);}
    private void end(PlayerLife p){double now=monotonic.getAsDouble();var last=deaths.get(p);if(last!=null&&now-last<3){try{policy.send(p,new SimplePackets.ChatMessage(255,2,"Your new class/loadout applies on your next respawn."));}catch(RuntimeException error){policy.failure("selection notice",error);}return;}deaths.put(p,now);lives.endLifeForTransition(p.id(),10);}
    public void loadout(PlayerLife p,PlayerPackets.SetClassLoadout packet){
        owned();if(!lives.current(p))return;var s=policy.normalize(packet.classId(),packet.loadout(),packet.prefabs(),packet.ugcTools(),p.selection().classId());if(!allowed(p,s)||s.equals(p.selection()))return;
        if(policy.liveUgc(p,s)){p.apply(s);p.clearPending();policy.broadcast(new PlayerPackets.SetClassLoadout(p.id(),s.classId(),1,s.loadout(),s.prefabs(),s.ugcTools()));}
        else{p.stage(s);if(p.alive())end(p);}
    }
    public void change(PlayerLife p,int requested){owned();if(!lives.current(p))return;var pending=p.pendingSelection().orElse(null);var s=pending!=null&&pending.classId()==requested?pending:policy.normalize(requested,List.of(),List.of(),List.of(),p.selection().classId());if(!allowed(p,s))return;p.stage(s);if(s.classId()!=p.selection().classId()&&p.alive())end(p);}
    public void forget(PlayerLife p){owned();deaths.remove(p);}
}
