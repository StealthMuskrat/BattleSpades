// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.stream.IntStream;
import net.battlespades.player.*;
import net.battlespades.physics.CollisionMap;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.rules.GameRules;
import net.battlespades.support.LifeHarness;
import net.battlespades.world.MutableWorld;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
final class ObjectiveGuardGoldenTest {
    static IntStream traces(){return IntStream.range(0,16);}
    @ParameterizedTest @MethodSource("traces") void originalLiveNativeTerrainLineOfSightTrapAndResettle(int trace)throws Exception{
        try(var h=new LifeHarness()){
            h.admit(0,2);var p=h.runtime.player(0);p.body().position(new Vector(36.5,36.5,97.75));var guard=new ObjectiveGuard(h.world,new ObjectiveGuard.Settings(5,60,trace%8!=7,true),()->h.monotonic,player->false);
            for(var r:Fixtures.rows("objective-guard")){if(Integer.parseInt(r[1])!=trace)continue;if(r[0].equals("edit")){h.world.set(new MutableWorld.Cell(Integer.parseInt(r[2]),Integer.parseInt(r[3]),Integer.parseInt(r[4])),r[5].equals("1"),0x808080,null);continue;}if(!r[0].equals("query"))continue;
                var at=Fixtures.vector(r[4]);boolean space=r[3].equals("1");String why=guard.trapped(at,space);assertEquals(r[5],why==null?"-":why,"trap "+trace+":"+r[2]);assertEquals(r[6],Fixtures.vec(guard.resettle(at,space)),"resettle "+trace+":"+r[2]);assertEquals(r[7].equals("1"),guard.pickupVisible(p,at,space,.5),"LOS "+trace+":"+r[2]);
            }
        }
    }
    @ParameterizedTest @MethodSource("traces") void originalIdentitySensitiveIdlePresenceAndTrapTimers(int trace)throws Exception{
        try(var h=new LifeHarness()){
            boolean[] flagged={false};var settings=new ObjectiveGuard.Settings(5,new double[]{60,0,-1,1}[trace%4],true,true);var guard=new ObjectiveGuard(h.world,settings,()->h.monotonic,p->flagged[0]);var timer=new ObjectiveGuard.TrapTimer<Integer>();PlayerLife p=null;
            for(var r:Fixtures.rows("objective-guard")){if(!r[0].equals("presence")||Integer.parseInt(r[1])!=trace)continue;if(r[14].equals("1")){p=new PlayerLife(0,2,0,"presence","tdm",CollisionMap.of(h.world),GameRules.serverDefaults(),new ClassCatalog.Selection(0,List.of(5,6,23),List.of(),List.of()));p.spawn(new Vector(36.5,36.5,97.75),h.monotonic,1,0);}Objects.requireNonNull(p).body().position(Fixtures.vector(r[3]));var orientation=Fixtures.vector(r[4]);p.orientation(orientation.x(),orientation.y(),orientation.z());double now=Fixtures.number(r[5]);flagged[0]=r[7].equals("1");assertEquals(r[8],Fixtures.bits(guard.idleSeconds(p,now)),"idle "+trace+":"+r[2]);assertEquals(r[9].equals("1"),guard.presenceEligible(p,now),"presence "+trace+":"+r[2]);assertEquals(r[13].equals("1"),timer.due(Integer.parseInt(r[10]),r[11].equals("1"),now,Fixtures.number(r[12])),"timer "+trace+":"+r[2]);if(r[15].equals("1"))timer.clear();}
        }
    }
}
