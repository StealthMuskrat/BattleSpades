// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import net.battlespades.protocol.*;
import net.battlespades.protocol.DropPickup.Position;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class ObjectiveCarryGoldenTest {
    static double number(String v){return Double.longBitsToDouble(Long.parseUnsignedLong(v,16));}
    static String bits(double v){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v));}
    static Position vector(String value){if(value.equals("-"))return null;var p=value.split(",");return new Position(number(p[0]),number(p[1]),number(p[2]));}
    static String vec(Position v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    static String optional(Integer value){return value==null?"-":value.toString();}
    List<String[]> rows()throws Exception{try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/objective-carry.tsv.gz")))){return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}}
    static IntStream traces(){return IntStream.range(0,32);}
    @ParameterizedTest @MethodSource("traces") void originalCarrySanitizeSendFaultAndNativeBurdenBoundaries(int trace)throws Exception {
        try(var h=new LifeHarness()) {
            h.admit(0,2);var player=h.runtime.player(0);var wire=new ArrayList<String>();boolean[] failure={false};
            java.util.function.Consumer<GamePacket> send=packet->{wire.add(HexFormat.of().formatHex(packet.encode()));if(failure[0])throw new IllegalStateException("controlled reliable send fault");};
            for(var r:rows()) {
                if(!Character.isDigit(r[0].charAt(0))||Integer.parseInt(r[0])!=trace)continue;
                wire.clear();failure[0]=r[7].equals("1");String result="-";
                try {
                    switch(r[2]) {
                        case "P"->result=ObjectiveCarry.pickup(player,Integer.parseInt(r[3]),r[4].equals("1"),Integer.parseInt(r[5]),send)?"1":"0";
                        case "D"->{var d=ObjectiveCarry.drop(player,vector(r[8]),vector(r[9]),Integer.parseInt(r[6]),send);if(d!=null)result=d.type()+","+d.state()+";"+vec(d.position())+";"+vec(d.velocity());}
                        case "S"->{var d=ObjectiveCarry.sanitize(player,14+trace%3,vector(r[8]),vector(r[9]));if(d!=null)result=vec(d.position())+";"+vec(d.velocity());}
                        case "R"->h.runtime.respawn(0);
                        default->throw new AssertionError(r[2]);
                    }
                }catch(ArithmeticException overflow){result="overflow";}catch(IllegalStateException sendFailure){result="send-fault";}
                String where="trace "+trace+" step "+r[1];assertEquals(r[10],result,where+" result");assertEquals(r[11],optional(player.pickupId())+","+(player.pickupBurdensome()?1:0)+","+optional(player.pickupState())+","+(player.body().burdened()?1:0),where+" carry/body");assertEquals(r[12],wire.isEmpty()?"-":String.join("|",wire),where+" wire");
                var reveal=ObjectiveCarry.reveal(List.of(player));assertEquals(player.pickupId()==null?0:1,reveal.size());if(!reveal.isEmpty())assertArrayEquals(new SimplePackets.PickPickup(0,player.pickupId(),player.pickupBurdensome()?1:0).encode(),reveal.getFirst().encode());
            }
        }
    }
    static IntStream codecs(){return IntStream.range(0,16);}
    @ParameterizedTest @MethodSource("traces") void originalCompensatedArbitraryDropVectors(int group)throws Exception {
        try(var h=new LifeHarness()){h.admit(0,2);var player=h.runtime.player(0);for(var r:rows()) {
            if(!r[0].equals("sanitize")||Integer.parseInt(r[1])/256!=group)continue;
            String actual="-";try{var result=ObjectiveCarry.sanitize(player,Integer.parseInt(r[2]),vector(r[3]),vector(r[4]));if(result!=null)actual=vec(result.position())+";"+vec(result.velocity());}catch(ArithmeticException overflow){actual="overflow";}
            assertEquals(r[5],actual,"arbitrary vector "+r[1]);
        }}
    }
    @ParameterizedTest @MethodSource("codecs") void originalNativeDropTupleEncodingAndDecoding(int group)throws Exception {
        assertTrue(PacketCodec.supports(71));for(var r:rows()) {
            if(!r[0].equals("codec")||Integer.parseInt(r[1])/32!=group)continue;
            var packet=new DropPickup(Integer.parseInt(r[2]),Integer.parseInt(r[3]),Integer.parseInt(r[4]),vector(r[5]),vector(r[6]));var raw=HexFormat.of().parseHex(r[7]);assertArrayEquals(raw,packet.encode(),"native authored "+r[1]);var decoded=(DropPickup)PacketCodec.decode(raw);assertEquals(r[9],vec(decoded.position()));assertEquals(r[10],vec(decoded.velocity()));assertArrayEquals(HexFormat.of().parseHex(r[8]),decoded.encode(),"native re-encode "+r[1]);
        }
    }
}
