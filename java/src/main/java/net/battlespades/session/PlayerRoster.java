// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.battlespades.protocol.GamePacket;
import net.battlespades.protocol.PlayerPackets;
import net.battlespades.protocol.SimplePackets;

/** Concrete life tokens avoid missing an identical-looking respawn while a client loads. */
public final class PlayerRoster {
    public record Known(long life,int color) {}
    public record Plan(List<GamePacket> packets,Map<Integer,Known> known) {
        public Plan {packets=List.copyOf(packets);known=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(known));}
    }
    private record Life(long token,PlayerPackets.CreatePlayer create,int color) {}
    private final Thread owner=Thread.currentThread();
    private final Map<Integer,Life> lives=new LinkedHashMap<>();
    private long nextLife;
    private void owned() {if(Thread.currentThread()!=owner) throw new IllegalStateException("Roster accessed outside owner");}
    public long create(PlayerPackets.CreatePlayer player,int color) {
        owned();if(player.playerId()<0 || player.playerId()>254) throw new IllegalArgumentException("Roster id");
        long token=++nextLife;lives.put(player.playerId(),new Life(token,player,color&0xffffff));return token;
    }
    public void remove(int id) {owned();lives.remove(id);}
    public void color(int id,int color) {owned();var life=lives.get(id);if(life!=null) lives.put(id,new Life(life.token,life.create,color&0xffffff));}
    /** Caller commits known only after every packet in this reliable plan has been accepted. */
    public Plan reconcile(Map<Integer,Known> known,int exclude) {
        owned();var packets=new ArrayList<GamePacket>();var target=new LinkedHashMap<Integer,Known>();
        for(int id:known.keySet()) if(id!=exclude && !lives.containsKey(id)) packets.add(new SimplePackets.PlayerLeft(id));
        for(var entry:lives.entrySet()) {
            int id=entry.getKey();if(id==exclude) continue;var life=entry.getValue();var old=known.get(id);
            if(old==null || old.life!=life.token) packets.add(life.create);
            if(old==null || old.life!=life.token || old.color!=life.color) packets.add(new SimplePackets.SetColor(id,life.color));
            target.put(id,new Known(life.token,life.color));
        }
        return new Plan(packets,target);
    }
}
