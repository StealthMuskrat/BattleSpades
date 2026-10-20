// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.util.*;
import net.battlespades.match.HudState;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudStateTest {
    private static final class Peer implements HudState.Peer {boolean live=true,fail;final Object token=new Object();final Set<Integer> known=new HashSet<>();final List<GamePacket> sent=new ArrayList<>();public Object token(){return token;}public boolean inGame(){return live;}public boolean knows(int id){return known.contains(id);}public boolean send(GamePacket p){if(fail)throw new IllegalStateException("controlled send failure");sent.add(p);return true;}}
    @SuppressWarnings("unchecked") @Test void nativeBillboardBytesAndGuardedAudiencesReplayAndCleanupMatchTheClientContract()throws Exception{
        var a=new Peer();var b=new Peer();var peers=new ArrayList<HudState.Peer>(List.of(a,b));try(var hud=new HudState(()->List.copyOf(peers))){
            for(var value:(List<?>)AchievementStoreTest.fixture().get("wire")){var row=(Map<String,Object>)value;var coords=(List<Number>)row.get("position");assertTrue(hud.add(((Number)row.get("id")).intValue(),((Number)row.get("key")).intValue(),(String)row.get("icon"),new Vector(coords.get(0).doubleValue(),coords.get(1).doubleValue(),coords.get(2).doubleValue()),false,((Number)row.get("color")).intValue(),null));var packet=a.sent.getLast();assertEquals(row.get("hex"),HexFormat.of().formatHex(packet.encode()));var decoded=(MinimapBillboard)PacketCodec.decode(packet.encode());var expected=(List<Number>)row.get("decoded");assertEquals(expected.get(0).floatValue(),decoded.x());assertEquals(expected.get(1).floatValue(),decoded.y());assertEquals(expected.get(2).floatValue(),decoded.z());assertEquals(((MinimapBillboard)packet).entityId(),decoded.entityId());assertEquals(((MinimapBillboard)packet).color(),decoded.color());assertEquals(((MinimapBillboard)packet).icon(),decoded.icon());assertTrue(PacketCodec.supports(41));}
            hud.clear();a.sent.clear();b.sent.clear();b.fail=true;assertTrue(hud.add(5,1,"map_player_16",new Vector(10,20,30),true,null,null));assertTrue(a.sent.isEmpty());a.known.add(5);assertEquals(1,hud.reveal(a,a::knows).size());assertEquals(1,hud.reveal(b,id->id==5).size(),"preceding CreateEntity packets authorize ordered loader replay");
            assertTrue(hud.add(6,1,"base_icon",new Vector(10,20,30),false,null,null));assertEquals(1,a.sent.size());b.fail=false;hud.remove(6,List.of(a,a));var c=new Peer();peers.add(c);assertTrue(hud.reveal(c,c::knows).isEmpty(),"partial clear freezes the current audience");assertEquals(1,hud.reveal(b,b::knows).size());hud.forget(b.token);assertTrue(hud.reveal(b,b::knows).isEmpty());
            int count=a.sent.size();assertFalse(hud.add(32768,0,"base_icon",new Vector(1,2,3),false,null,null));assertFalse(hud.add(1,0,"../../bad",new Vector(1,2,3),false,null,null));assertFalse(hud.add(1,0,"base_icon",null,false,null,null));assertFalse(hud.progress(.5,1));assertFalse(hud.clearProgress());assertEquals(count,a.sent.size());assertTrue(hud.focus(new Vector(700,30,20),List.of(a,a)));assertEquals(1,a.sent.stream().filter(PoiFocus.class::isInstance).count());assertFalse(a.sent.stream().anyMatch(p->p.id()==65));
        }
    }
}
