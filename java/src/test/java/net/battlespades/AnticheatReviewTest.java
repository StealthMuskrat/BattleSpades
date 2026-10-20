// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.util.*;
import net.battlespades.combat.*;
import net.battlespades.match.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerMotion;
import net.battlespades.session.HostedReviewCommands;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnticheatReviewTest {
    private static AnticheatReview.Evidence evidence(boolean bot,long hits,long heads,AnticheatReview.AimSnapshot aim){return new AnticheatReview.Evidence(4,"Review",bot,new ShotTally.Snapshot(100,hits,heads,hits,Map.of(6,new ShotTally.Weapon(100,hits,heads),9,new ShotTally.Weapon(100,100,100)),Map.of(42,64L)),30L,20L,aim,Map.of("input_backlog",10L,"input_backlog:observed",10L),0);}
    @Test void thresholdsUseSingleRaysHumanPopulationAndAllReasonsWithoutEnforcement(){
        var s=AnticheatReview.Settings.defaults();var aim=new AnticheatReview.AimSnapshot(100,20,20,4,List.of(),Collections.nCopies(15,50.));var e=evidence(false,60,40,aim);var population=Map.of(6,Collections.nCopies(20,.3));var r=AnticheatReview.analyze(s,e,population,300);
        assertTrue(r.flagged());assertEquals(List.of("headshot_kills","headshot_hits","accuracy:6","aim_snap","reaction","pellet_seed","sustained:input_backlog"),r.reasons().stream().map(AnticheatReview.Reason::check).toList());assertEquals(17.19,r.score());assertNull(AnticheatReview.analyze(s,evidence(true,60,40,aim),population,300));
        var below=AnticheatReview.analyze(s,evidence(false,59,40,AnticheatReview.AimSnapshot.empty()),Map.of(6,Collections.nCopies(19,.3)),299);assertEquals(List.of("headshot_kills","pellet_seed"),below.reasons().stream().map(AnticheatReview.Reason::check).toList());
        var profile=new ProfileStats();profile.add(MatchCatalog.constant("KILL_SCORE_REASON"),100,0);profile.add(MatchCatalog.constant("KILL_SCORE_HEADSHOT_REASON"),20,0);profile.add(MatchCatalog.constant("KILL_SCORE_MELEE_REASON"),10,0);var explosive=MatchCatalog.classKill(5,11);assertNotNull(explosive);profile.add(explosive,60,0);assertArrayEquals(new long[]{30,20},AnticheatReview.profileKills(profile));assertEquals(.795,AnticheatReview.percentile(List.of(.3,.8),99),1e-12);
    }
    @Test void aimHistoryRequiresVisibleFreshEngagementAndBoundsSamplesAndExamples(){
        var s=AnticheatReview.Settings.defaults();var aim=new AnticheatReview.Aim();var origin=new Vector(10,10,10);var target=List.of(new AnticheatReview.Target(7,3,new Vector(20,10,10)));var previous=new PlayerMotion.Eye(origin,new Vector(0,1,0));
        for(int i=0;i<210;i++)aim.observe(s,2,100,origin,new Vector(1,0,0),target,label->previous,(x,y,z)->false,i*4);
        var snapshot=aim.snapshot();assertEquals(210,snapshot.shots());assertEquals(210,snapshot.engaged());assertEquals(210,snapshot.onHead());assertEquals(210,snapshot.snaps());assertEquals(5,snapshot.examples().size());assertEquals(200,snapshot.acquisition().size());assertEquals(1000./60,snapshot.acquisition().getFirst(),1e-12);
        aim.observe(s,2,100,origin,new Vector(1,0,0),target,label->previous,(x,y,z)->false,836.1);assertEquals(200,aim.snapshot().acquisition().size());assertEquals(211,aim.snapshot().snaps());
        var gap=new AnticheatReview.Aim();gap.observe(s,2,100,origin,new Vector(1,0,0),target,label->null,(x,y,z)->false,0);assertEquals(1,gap.snapshot().engaged());assertTrue(gap.snapshot().acquisition().isEmpty());gap.observe(s,2,100,origin,new Vector(1,0,0),target,label->previous,(x,y,z)->x==15,4);assertEquals(1,gap.snapshot().engaged());
        gap.observe(s,3,100,origin,new Vector(1,0,0),target,label->previous,(x,y,z)->false,8);assertEquals(1,gap.snapshot().engaged());
    }
    @Test void configuredThresholdsAndUnicodeChunksStayPortable(){
        assertThrows(IllegalArgumentException.class,()->AnticheatReview.Settings.from(org.tomlj.Toml.parse("[anticheat]\nsnap_ratio=0")));assertThrows(IllegalArgumentException.class,()->AnticheatReview.Settings.from(org.tomlj.Toml.parse("[anticheat]\naccuracy_percentile=nan")));
        var chunks=HostedReviewCommands.chunks("😀".repeat(111)+"  tail");assertEquals(110,chunks.getFirst().codePointCount(0,chunks.getFirst().length()));assertEquals("😀  tail",chunks.get(1));assertEquals("😀".repeat(111)+"  tail",String.join("",chunks));
        assertEquals(List.of("word ".repeat(22).stripTrailing(),"word ".repeat(3).stripTrailing()),HostedReviewCommands.chunks("word ".repeat(25).stripTrailing()));
    }
}
