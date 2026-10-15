// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.IOException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import net.battlespades.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComplexPacketTest {
    private EntityRecord entity() {
        return new EntityRecord(-25536,38,2,3,new Vector3f(10.25f,20.5f,200.75f),new Vector3f(-1,0.5f,0),
            -2,new Vector3f(18,52,86),1.5f,4,3.25f,1,List.of(-1,Integer.MAX_VALUE),List.of(-1.0,0.5,2.0));
    }
    private WorldUpdate snapshot() {
        var player=new WorldUpdate.PlayerRow(3,new Vector3f(1.25f,2.5f,230.75f),new Vector3f(1,0,-0.5f),new Vector3f(-0.25f,0.5f,0),
            23,12344,100,0xb1,0xc5,9,7,-1,12.5,2.25,-1);
        return new WorldUpdate(12345,List.of(player),List.of(entity()),List.of(new WorldUpdate.TurretRow(-25536,-1,0.5)));
    }
    @Test void sourceEntitySnapshotAndDamageBytes() throws IOException {
        for(String row:GoldenPacketTest.lines("complex.tsv")) {
            var p=row.split("\t");byte[] expected=HexFormat.of().parseHex(p[1]);
            GamePacket encoded=switch(p[0]) {
                case "CreateEntity" -> new CreateEntity(entity());
                case "WorldUpdate" -> snapshot();
                case "Damage" -> new DamagePacket(3,6,31.75f,4,0,168,-1,new Vector3f(1.25f,2.5f,230.75f));
                default -> throw new AssertionError("Unknown fixture");
            };
            assertArrayEquals(expected,encoded.encode(),p[0]);
            var decoded=PacketCodec.decode(expected);assertEquals(encoded.id(),decoded.id());
            for(int size=0;size<expected.length;size++) {
                byte[] truncated=Arrays.copyOf(expected,size);assertThrows(ProtocolException.class,()->PacketCodec.decode(truncated));
            }
        }
    }
    @Test void preservesSignedTurretIdAndNoPickupSentinel() {
        var decoded=(WorldUpdate)PacketCodec.decode(snapshot().encode());
        assertEquals(-25536,decoded.turrets().getFirst().entityId());
        assertEquals(-1,decoded.players().getFirst().pickup());assertEquals(9,decoded.players().getFirst().state());assertEquals(7,decoded.players().getFirst().tool());
        assertEquals(-63/64.0,decoded.players().getFirst().deploymentYaw());
    }
    @Test void minimumEntitiesAndCountBounds() {
        var zero=new Vector3f(0,0,0);
        var minimal=new EntityRecord(1,1,0,0,zero,zero,0,zero,0,0,0,0,List.of(),List.of());
        var snapshot=new WorldUpdate(1,List.of(),List.of(minimal,minimal,minimal),List.of());
        assertEquals(3,((WorldUpdate)PacketCodec.decode(snapshot.encode())).entities().size());
        assertThrows(ProtocolException.class,()->PacketCodec.decode(new byte[]{2,0,0,0,0,-1,-1}));
    }
}
