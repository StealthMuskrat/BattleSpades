// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import net.battlespades.match.GameplaySounds;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.world.MetadataDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class GameplayServicesGoldenTest {
    static Map<?,?> fixture()throws Exception{try(var input=Objects.requireNonNull(GameplayServicesGoldenTest.class.getResourceAsStream("/golden/gameplay-services.json"))){return (Map<?,?>)MetadataDocument.json(new String(input.readAllBytes(),StandardCharsets.UTF_8));}}
    static Stream<Map<?,?>> balance()throws Exception{return ((List<?>)fixture().get("balance")).stream().map(v->(Map<?,?>)v);}static int n(Object v){return ((Number)v).intValue();}static double d(Object v){return ((Number)v).doubleValue();}
    @ParameterizedTest @MethodSource("balance") void intactSourceBalanceAndForcedTeamMoveTraces(Map<?,?> row){try(var h=new BalanceHarness()){
        for(var item:(List<?>)row.get("actions")){var step=(Map<?,?>)item;var a=(List<?>)step.get("action");String op=(String)a.getFirst();Object result=null;
            switch(op){
                case "tick"->{h.h.monotonic=d(a.get(1));result=h.balance.tick(h.h.monotonic).toCompletableFuture().join();}case "force"->result=(long)h.balance.force().toCompletableFuture().join();case "config"->h.config.put((String)a.get(1),a.get(2));case "mode"->h.mode.put((String)a.get(1),a.get(2));case "assign"->h.assigns=true;case "transition"->h.busy=(Boolean)a.get(1);
                case "bot"->{var p=h.h.runtime.player(n(a.get(1)));p.bot(true);h.bots.add(p);}case "alive"->{var p=h.h.runtime.player(n(a.get(1)));p.spawn(new Vector(36,36,97),h.h.monotonic,1,0x123456);}case "dead"->h.h.runtime.die(n(a.get(1)),null,0);case "deny"->h.denied.add(n(a.get(1)));case "retire_deny"->h.retireDenied.add(n(a.get(1)));case "director_deny"->h.directorDenied.add(n(a.get(1)));case "remove_false"->h.removeFalse.add(n(a.get(1)));case "add_failure"->h.addFailure=true;case "reconnecting"->h.reconnecting=true;case "director_stopped"->h.stopped=true;case "loading"->h.h.loading.add(n(a.get(1)));case "vip"->h.vips.add(h.h.runtime.player(n(a.get(1))));case "zero"->h.zero.add(n(a.get(1)));case "last"->h.last=n(a.get(1));case "pickup"->h.h.runtime.player(n(a.get(1))).carry(7,false,0);case "score"->{h.scores.put(n(a.get(1)),(long)n(a.get(2)));h.captures.put(n(a.get(1)),(long)n(a.get(3)));}case "moved"->h.h.runtime.player(n(a.get(1))).balanceMovedAt(d(a.get(2)));case "new"->h.add(n(a.get(1)),n(a.get(2)),(Boolean)a.get(3),false);case "replace"->{var old=h.h.runtime.player(n(a.get(1)));h.detach(old);h.add(n(a.get(1)),n(a.get(2)),false,false);}case "new_balancer"->h.newBalancer();default->fail(op);
            }
            assertEquals(step.get("result"),result,op);var actual=h.h.runtime.players().stream().map(p->Arrays.asList((Object)(long)p.id(),(long)p.team(),p.alive(),p.balanceMovedAt(),h.balance.joinedAt(p))).toList();assertEquals(step.get("players"),actual,op);assertEquals(step.get("moves"),List.of((long)h.balance.botMoves(),(long)h.balance.humanMoves()),op);assertEquals(step.get("log"),h.log,op);assertEquals(step.get("direct"),h.direct,op);
        }
    }}
    record Listener(int id,int team,boolean connected,boolean settled){}
    static GameplaySounds.Position position(Object value){if(value==null)return null;var v=(List<?>)value;return new GameplaySounds.Position(d(v.get(0)),d(v.get(1)),d(v.get(2)));}
    @Test void intactAudioRoutingBytesAndHearingOrder()throws Exception{
        var wire=new ArrayList<List<Object>>();var hearing=new ArrayList<List<Object>>();var players=List.of(new Listener(0,2,true,true),new Listener(1,2,true,false),new Listener(2,3,true,true),new Listener(3,0,true,true),new Listener(4,2,false,true));
        var sounds=new GameplaySounds<>(new GameplaySounds.Ports<Listener>(){
            public List<Listener> players(){return players;}public int id(Listener p){return p.id();}public int team(Listener p){return p.team();}public boolean connected(Listener p){return p.connected();}public boolean settled(Listener p){return p.settled();}
            public void broadcast(GamePacket packet,Listener exclude,boolean reliable){wire.add(List.of("broadcast",(long)(exclude==null?-1:exclude.id()),reliable,HexFormat.of().formatHex(packet.encode())));}public void send(Listener p,GamePacket packet,boolean reliable){wire.add(List.of("direct",(long)p.id(),reliable,HexFormat.of().formatHex(packet.encode())));}public boolean hearingEnabled(){return true;}public void blockDestroyed(GameplaySounds.Position c,int id,int team,double life){hearing.add(List.of("BLOCK_DESTROYED",List.of(c.x(),c.y(),c.z()),(long)id,(long)team,life));}
        });
        for(var item:(List<?>)fixture().get("audio")){var step=(Map<?,?>)item;var a=(List<?>)step.get("action");Object result=null;
            switch((String)a.getFirst()){
                case "play"->sounds.play(new GameplaySounds.Cue(n(a.get(1)),(float)d(a.get(2)),position(a.get(3)),(float)d(a.get(4))),a.get(5)==null?null:players.get(n(a.get(5))),(Boolean)a.get(6),a.get(7)==null?null:players.get(n(a.get(7))));case "personal"->sounds.personal(players.get(n(a.get(1))),new GameplaySounds.Cue(n(a.get(2)),(float)d(a.get(3)),position(a.get(4)),(float)d(a.get(5))),(Boolean)a.get(6));case "team"->result=(long)sounds.team(n(a.get(1)),new GameplaySounds.Cue(n(a.get(2))),a.get(3)==null?null:players.get(n(a.get(3))));case "relative"->sounds.relative(n(a.get(1)),n(a.get(2)),n(a.get(3)),(float)d(a.get(4)));case "stop"->sounds.stop(n(a.get(1)));case "stop_to"->sounds.stop(players.get(n(a.get(1))),n(a.get(2)));default->fail();
            }assertEquals(step.get("result"),result);assertEquals(step.get("wire"),wire);assertEquals(step.get("hearing"),hearing);
        }
        var choices=(Map<?,?>)fixture().get("choices");for(var item:(List<?>)choices.get("skyboxes")){var row=(List<?>)item;assertEquals(n(row.get(1)),GameplaySounds.flyby((String)row.get(0)));}for(var item:(List<?>)choices.get("explosions")){var row=(List<?>)item;assertEquals(n(row.get(1)),GameplaySounds.explosion(row.get(0)==null?null:d(row.get(0)),d(choices.get("water_level")),18,17));}assertEquals(18,GameplaySounds.explosion(Double.NaN,d(choices.get("water_level")),18,17));
    }
}
