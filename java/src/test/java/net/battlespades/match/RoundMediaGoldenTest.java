// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import net.battlespades.player.SourceRandom;
import net.battlespades.protocol.*;
import net.battlespades.world.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class RoundMediaGoldenTest {
    static Stream<Map<?,?>> cases()throws Exception{try(var in=Objects.requireNonNull(RoundMediaGoldenTest.class.getResourceAsStream("/golden/round-media.json"))){return ((List<?>)((Map<?,?>)MetadataDocument.json(new String(in.readAllBytes(),StandardCharsets.UTF_8))).get("cases")).stream().map(row->(Map<?,?>)row);}}
    private static String hex(GamePacket packet){return HexFormat.of().formatHex(packet.encode());}
    private static int n(Object value){return ((Number)value).intValue();}
    private static double d(Object value){return ((Number)value).doubleValue();}
    @SuppressWarnings("unchecked") private static List<String> strings(Object value){return (List<String>)value;}
    @ParameterizedTest @MethodSource("cases") void intactSourceMusicAmbienceBallotsAndNativeCodecs(Map<?,?> row){
        switch((String)row.get("domain")){
            case "music"->music(row);case "ambience"->ambience(row);case "ballot"->ballot(row);case "codecs"->codecs(row);default->fail("Unknown source domain");
        }
    }
    private static void music(Map<?,?> row){
        var wire=new ArrayList<String>();var direct=new ArrayList<String>();var music=new MusicService(p->wire.add(hex(p)),new SourceRandom(n(row.get("seed"))));
        for(var value:(List<?>)row.get("steps")){var step=(Map<?,?>)value;Object result=null;String op=(String)step.get("op");switch(op){
            case "gameplay"->music.gameplay((Boolean)step.get("arg"));case "ending"->result=music.ending();case "timeout"->music.timeout();case "last"->result=music.lastMan();case "play"->music.play((String)step.get("arg"));case "stop"->music.stop();case "direct"->MusicService.playTo(p->direct.add(hex(p)),(String)step.get("arg"));default->fail(op);
        }assertEquals(step.get("result"),result,op);assertEquals(step.get("family"),music.family(),op);assertEquals(step.get("wire"),wire,op);assertEquals(step.get("direct"),direct,op);}
    }
    private static void ambience(Map<?,?> row){
        var definitions=new ArrayList<MapMetadata.Ambient>();for(var value:(List<?>)row.get("definitions")){var r=(Map<?,?>)value;var points=((List<?>)r.get("points")).stream().map(p->{var a=(List<?>)p;return new StateData.Vector(d(a.get(0)),d(a.get(1)),d(a.get(2)));}).toList();definitions.add(new MapMetadata.Ambient((String)r.get("name"),points,d(r.get("volume")),d(r.get("attenuation"))));}
        var wire=new ArrayList<String>();MapAmbience.send(definitions,new Vector3f(36.25f,47.5f,97.75f),p->wire.add(hex(p)));assertEquals(row.get("wire"),wire);
    }
    private static final class Actor {final int id;final boolean bot,settled,connected;Actor(int id){this.id=id;bot=id==2;settled=id!=3;connected=id!=2&&id!=4;}}
    private static void ballot(Map<?,?> row){
        var wire=new ArrayList<String>();var direct=new ArrayList<String>();var players=new LinkedHashMap<Integer,Actor>();var stale=new HashMap<Integer,Actor>();for(int i=0;i<6;i++)players.put(i,new Actor(i));double[] mono={100};var ballot=new MapBallot<>(strings(row.get("catalog")),()->players,p->p.id,p->!p.bot&&p.settled&&p.connected,()->mono[0],seconds->new CompletableFuture<Void>(),p->wire.add(hex(p)),Runnable::run);CompletableFuture<String> wait=null;
        for(var value:(List<?>)row.get("steps")){var step=(Map<?,?>)value;var action=(List<?>)step.get("action");String op=(String)action.getFirst();Object result=null;switch(op){
            case "start"->result=ballot.start(strings(action.get(1)),d(action.get(2)));case "cast"->ballot.cast(players.get(n(action.get(1))),n(action.get(2)));case "stale"->ballot.cast(stale.get(n(action.get(1))),n(action.get(2)));case "wire"->ballot.castWire(players.get(n(action.get(1))),(String)action.get(2));case "mono"->mono[0]=d(action.get(1));case "tick"->ballot.tick(action.get(1)==null?null:d(action.get(1)));case "replace"->{int id=n(action.get(1));stale.put(id,players.put(id,new Actor(id)));}case "forget"->ballot.forget(n(action.get(1)));case "cancel"->ballot.cancel();case "consume"->result=ballot.consume();case "reveal"->ballot.reveal(p->direct.add(hex(p)));case "wait"->wait=ballot.waitForResult().toCompletableFuture();default->fail(op);
        }var votes=new LinkedHashMap<String,Long>();ballot.votes().forEach((id,index)->votes.put(id.toString(),index.longValue()));assertEquals(step.get("result"),result,"result "+op);assertEquals(step.get("active"),ballot.active(),op);assertEquals(step.get("candidates"),ballot.candidates(),op);assertEquals(step.get("votes"),votes,op);assertEquals(step.get("next"),ballot.nextMap(),op);assertEquals(step.get("wire"),wire,op);assertEquals(step.get("direct"),direct,op);}
        var expected=(List<?>)row.get("wait_results");if(!expected.isEmpty()){assertNotNull(wait);assertTrue(wait.isDone());assertEquals(expected.getFirst(),wait.join());}
    }
    private static void codecs(Map<?,?> row){
        var checked=new HashSet<String>();for(var entry:((Map<?,?>)row.get("vectors")).entrySet()){
            byte[] bytes=HexFormat.of().parseHex((String)entry.getKey());var decoded=PacketCodec.decode(bytes);assertTrue(PacketCodec.supports(decoded.id()));assertEquals(entry.getValue(),hex(decoded));
            // One truncation walk per structural size/packet kind; loop-id variants share the same shape.
            if(checked.add(decoded.id()+":"+bytes.length))for(int i=0;i<bytes.length;i++){byte[] prefix=Arrays.copyOf(bytes,i);assertThrows(ProtocolException.class,()->PacketCodec.decode(prefix));}
            byte[] trailing=Arrays.copyOf(bytes,bytes.length+1);assertThrows(ProtocolException.class,()->PacketCodec.decode(trailing));
        }
    }
}
