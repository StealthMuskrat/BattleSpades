// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.battlespades.protocol.GamePacket;
import net.battlespades.protocol.PlayerPackets;
import net.battlespades.protocol.SimplePackets;
import net.battlespades.protocol.LifePackets;

/** Concrete life tokens avoid missing an identical-looking respawn while a client loads. */
public final class PlayerRoster {
    public record Known(long life,int color,long identity,boolean dead) {}
    public record Plan(List<GamePacket> packets,Map<Integer,Known> known) {
        public Plan {packets=List.copyOf(packets);known=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(known));}
    }
    private record Life(long token,long identity,PlayerPackets.CreatePlayer create,int color,LifePackets.KillAction death) {}
    private final Thread owner=Thread.currentThread();
    private final Map<Integer,Life> lives=new LinkedHashMap<>();
    private long nextLife,nextIdentity;
    private void owned() {if(Thread.currentThread()!=owner) throw new IllegalStateException("Roster accessed outside owner");}
    public long create(PlayerPackets.CreatePlayer player,int color) {
        owned();if(player.playerId()<0 || player.playerId()>254) throw new IllegalArgumentException("Roster id");
        var previous=lives.get(player.playerId());long identity=previous==null?++nextIdentity:previous.identity;
        long token=++nextLife;lives.put(player.playerId(),new Life(token,identity,player,color&0xffffff,null));return token;
    }
    /** A live class/loadout change updates fresh catch-up without inventing a new life or identity.
     * Its SetClassLoadout packet is ordered by the caller for peers that already know the body. */
    public void appearance(PlayerPackets.CreatePlayer player){owned();var life=lives.get(player.playerId());if(life==null)throw new IllegalStateException("Unknown player appearance");lives.put(player.playerId(),new Life(life.token,life.identity,player,life.color,life.death));}
    public long identity(int id){owned();var life=lives.get(id);return life==null?0:life.identity;}
    public void remove(int id) {owned();lives.remove(id);}
    public void color(int id,int color) {owned();var life=lives.get(id);if(life!=null) lives.put(id,new Life(life.token,life.identity,life.create,color&0xffffff,life.death));}
    public void death(LifePackets.KillAction packet){owned();var life=lives.get(packet.playerId());if(life==null)throw new IllegalStateException("Unknown dead player");lives.put(packet.playerId(),new Life(life.token,life.identity,life.create,life.color,packet.quiet()));}
    /** Caller commits known only after every packet in this reliable plan has been accepted. */
    public Plan reconcile(Map<Integer,Known> known,int exclude) {
        owned();var packets=new ArrayList<GamePacket>();var target=new LinkedHashMap<Integer,Known>();
        for(var entry:known.entrySet()){
            int id=entry.getKey();if(id==exclude)continue;
            if(!lives.containsKey(id)||lives.get(id).identity!=entry.getValue().identity)packets.add(new SimplePackets.PlayerLeft(id));
            else target.put(id,entry.getValue());
        }
        for(var entry:lives.entrySet()) {
            int id=entry.getKey();if(id==exclude) continue;var life=entry.getValue();var old=known.get(id);
            if(old!=null&&old.identity!=life.identity)old=null;
            if(life.death!=null){if(old==null)continue;if(old.life!=life.token||!old.dead)packets.add(life.death);target.put(id,new Known(life.token,life.color,life.identity,true));continue;}
            if(old==null || old.life!=life.token) packets.add(life.create);
            if(old==null || old.life!=life.token || old.color!=life.color) packets.add(new SimplePackets.SetColor(id,life.color));
            target.put(id,new Known(life.token,life.color,life.identity,false));
        }
        return new Plan(packets,target);
    }
}
