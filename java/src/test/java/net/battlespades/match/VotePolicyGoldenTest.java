// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.util.zip.*;
import net.battlespades.player.SourceRandom;
import net.battlespades.protocol.*;
import net.battlespades.world.MetadataDocument;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class VotePolicyGoldenTest {
    @TempDir Path temp;
    static Stream<Map<?,?>> cases()throws Exception{try(var in=Objects.requireNonNull(VotePolicyGoldenTest.class.getResourceAsStream("/golden/vote-policy.json"))){return ((List<?>)((Map<?,?>)MetadataDocument.json(new String(in.readAllBytes(),StandardCharsets.UTF_8))).get("cases")).stream().map(r->(Map<?,?>)r);}}
    private static int n(Object value){return ((Number)value).intValue();}private static double d(Object value){return ((Number)value).doubleValue();}private static Long integer(Object value){return value==null?null:((Number)value).longValue();}
    @SuppressWarnings("unchecked") private static List<String> strings(Object value){return (List<String>)value;}
    private static String hex(GamePacket p){return HexFormat.of().formatHex(p.encode());}
    @ParameterizedTest @MethodSource("cases") void actualSourceNavigationCatalogRotationAndKickPolicy(Map<?,?> row)throws Exception{
        switch((String)row.get("domain")){case "navigation"->{Path path=temp.resolve("nav.botnav");if(row.get("data")!=null)Files.write(path,HexFormat.of().parseHex((String)row.get("data")));assertEquals(integer(row.get("area")),Optional.ofNullable(NavigationAreas.read(path)).map(Integer::longValue).orElse(null));}case "catalog"->catalog(row);case "kick"->kick(row);case "fit"->{for(var value:(List<?>)row.get("vectors")){var r=(Map<?,?>)value;assertEquals(n(r.get("tier")),MapRotation.fit(integer(r.get("area")),d(r.get("players")),100,100));}}default->fail("Unknown domain");}
    }
    static byte[] nav(int dry)throws IOException{
        int count=4096;byte[] payload=new byte[20*count];Arrays.fill(payload,2*count,2*count+dry,(byte)2);Arrays.fill(payload,2*count+dry,3*count,(byte)1);var encoded=new ByteArrayOutputStream();try(var deflate=new DeflaterOutputStream(encoded)){deflate.write(payload);}var header=ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);header.put(new byte[]{'B','S','N','A','V','0','1',0}).putShort((short)1).putShort((short)64).putShort((short)64).putShort((short)238).put(new byte[16]).putInt(payload.length).putInt(encoded.size());var out=new ByteArrayOutputStream();out.write(header.array());out.write(encoded.toByteArray());return out.toByteArray();
    }
    private void catalog(Map<?,?> row)throws Exception{
        var names=strings(row.get("names"));var sizes=(List<?>)row.get("sizes");for(int i=0;i<names.size();i++){Files.write(temp.resolve(names.get(i)+".vxl"),new byte[0]);if(sizes.get(i)!=null)Files.write(temp.resolve(names.get(i)+".botnav"),nav(n(sizes.get(i))));}Files.writeString(temp.resolve("retail_map_info.json"),(String)row.get("retail"));
        var overrides=new HashMap<String,Object>();((Map<?,?>)row.get("overrides")).forEach((k,v)->overrides.put((String)k,v));var catalog=MapCatalog.load(temp,strings(row.get("requested")),(Boolean)row.get("shuffle"),overrides,new SourceRandom(100+n(row.get("case"))));assertEquals(row.get("available"),catalog.available());assertEquals(row.get("mode_available"),catalog.forMode((String)row.get("mode"),strings(row.get("stock"))));
        for(var entry:((Map<?,?>)row.get("areas")).entrySet())assertEquals(integer(entry.getValue()),catalog.area((String)entry.getKey()),entry.getKey().toString());
        var settings=(Map<?,?>)row.get("settings");var rotation=new MapRotation(catalog,new MapRotation.Settings((Boolean)settings.get("sizeFit"),d(settings.get("perPlayer")),d(settings.get("minimum")),n(settings.get("recent")),d(settings.get("botWeight")),(Boolean)settings.get("caps")));for(var name:strings(row.get("history_inputs")))rotation.notePlayed(name);assertEquals(row.get("history"),rotation.history());assertEquals(row.get("ranked"),rotation.rank(catalog.forMode((String)row.get("mode"),strings(row.get("stock"))),(String)row.get("current"),n(row.get("humans")),n(row.get("bots"))));
    }
    private static final class Actor {final int id;Integer team;String name,host;boolean bot,live,protectedHost,failHost;Actor(int id){this.id=id;team=id<4?2:id==5?0:3;name=id==0?"S{é}":id==1?"T'曲":"P"+id;host="10.0.0."+(id+1);bot=id==4;live=id!=6;}}
    private static final class Context implements KickBallot.Context<Actor> {
        final Map<Integer,Actor> players=new LinkedHashMap<>();final List<List<Object>> direct=new ArrayList<>(),disconnected=new ArrayList<>();VotingService<Actor> service;
        Context(){for(int i=0;i<7;i++)players.put(i,new Actor(i));}
        public Map<Integer,Actor> players(){return players;}public int id(Actor p){return p.id;}public Integer team(Actor p){return p.team;}public String name(Actor p){return p.name;}public boolean bot(Actor p){return p.bot;}public boolean eligible(Actor p){return !p.bot&&p.live&&p.id!=4;}
        public String host(Actor p){return p.host;}public boolean protectedHost(Actor p){if(p.failHost)throw new IllegalStateException("Advisory lookup failed");return p.protectedHost;}public void send(Actor p,GamePacket packet){direct.add(List.of((long)p.id,hex(packet)));}public void disconnect(Actor p,int reason){disconnected.add(List.of((long)p.id,(long)reason));p.live=false;players.remove(p.id);service.forget(p.id);}
    }
    private void kick(Map<?,?> row)throws Exception{
        for(var name:List.of("First","Other","Third"))Files.write(temp.resolve(name+".vxl"),new byte[0]);var catalog=MapCatalog.load(temp,List.of(),false,Map.of(),new SourceRandom(0));var context=new Context();double[] clock={100,1000};var wire=new ArrayList<String>();var service=new VotingService<>(new MapRotation(catalog,MapRotation.Settings.defaults()),context,KickBallot.Settings.defaults(),()->"First",()->"tdm",List::of,()->clock[0],()->clock[1],seconds->new CompletableFuture<Void>(),packet->wire.add(hex(packet)),(label,error)->{},Runnable::run);context.service=service;var stale=new HashMap<Integer,Actor>();
        for(var value:(List<?>)row.get("steps")){var step=(Map<?,?>)value;var action=(List<?>)step.get("action");String op=(String)action.getFirst();Object result=null;switch(op){
            case "start","stale_start"->result=service.kick().start((op.equals("stale_start")?stale:context.players).get(n(action.get(1))),context.players.get(n(action.get(2))),n(action.get(3)),clock[1]);
            case "cast"->service.kick().cast(context.players.get(n(action.get(1))),n(action.get(2)));case "wire"->service.kick().castWire(context.players.get(n(action.get(1))),(String)action.get(2));
            case "set"->{var p=context.players.get(n(action.get(1)));switch((String)action.get(2)){case "protected"->p.protectedHost=(Boolean)action.get(3);case "fail_host"->p.failHost=(Boolean)action.get(3);case "is_bot"->p.bot=(Boolean)action.get(3);case "host"->p.host=(String)action.get(3);case "name"->p.name=(String)action.get(3);default->fail("set");}}
            case "replace"->{int id=n(action.get(1));var old=context.players.get(id);var fresh=new Actor(id);fresh.host=old.host;stale.put(id,old);context.players.put(id,fresh);}
            case "cancel"->service.kick().cancel((Boolean)action.get(1),action.get(2)==null?null:d(action.get(2)));case "forget"->service.forget(n(action.get(1)));case "tick"->service.tick(action.get(1)==null?null:d(action.get(1)));case "mono"->clock[0]=d(action.get(1));case "wall"->clock[1]=d(action.get(1));case "map"->result=service.ensureMapVote(clock[1]);case "round_end"->result=service.ensureRoundEndVote(clock[1]);case "reset"->service.reset();case "reveal"->service.reveal(packet->context.direct.add(List.of(-1L,hex(packet))));default->fail(op);
        }
        String kind=service.kick().active()?"kick":service.map().active()?"map":null;var votes=new LinkedHashMap<String,Long>();(service.kick().active()?service.kick().votes():service.map().votes()).forEach((id,index)->votes.put(id.toString(),index.longValue()));var cooldowns=new HashMap<String,Double>();service.kick().cooldowns().forEach((key,until)->cooldowns.put(key.toString(),until));var match=new HashMap<String,Long>();service.kick().matchKicks().forEach((key,reason)->match.put(key,reason.longValue()));
        assertEquals(step.get("result"),result,op);assertEquals(step.get("active"),kind!=null,op);assertEquals(step.get("kind"),kind,op);assertEquals(integer(step.get("starter")),service.kick().starter()==null?null:service.kick().starter().longValue(),op);assertEquals(integer(step.get("target")),service.kick().target()==null?null:service.kick().target().longValue(),op);assertEquals(step.get("votes"),votes,op);assertEquals(step.get("cooldowns"),cooldowns,op);assertEquals(step.get("match_kicks"),match,op);assertEquals(step.get("next"),service.map().nextMap(),op);assertEquals(step.get("wire"),wire,op);assertEquals(step.get("direct"),context.direct,op);assertEquals(step.get("disconnected"),context.disconnected,op);
        }
    }
}
