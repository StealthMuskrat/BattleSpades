// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import net.battlespades.support.TransitionHarness;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class TransitionGoldenTest {
    private static final List<?> ROWS=load();
    private static List<?> load(){try(var in=Objects.requireNonNull(TransitionGoldenTest.class.getResourceAsStream("/golden/transition.json"))){return (List<?>)((Map<?,?>)MetadataDocument.json(new String(in.readAllBytes(),StandardCharsets.UTF_8))).get("cases");}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    static Stream<Map<?,?>> rollovers(){return ROWS.stream().<Map<?,?>>map(r->(Map<?,?>)r).filter(r->!r.get("domain").equals("normalize"));}
    @ParameterizedTest @MethodSource("rollovers") void originalFreezeRetirementCommitGatherAndRecovery(Map<?,?> row){
        var h=new TransitionHarness(((Number)row.get("case")).intValue());var service=new MatchTransitions<>(h,(map,code)->java.util.concurrent.CompletableFuture.completedFuture(h.candidate),Runnable::run);
        if(h.scenario>=28)h.map="CustomMap";var result=(h.scenario>=24?service.changeAfterScores("Other",new int[]{-2,0,7,130}[h.scenario%4],h.scenario%3==0?null:99):service.changeMap("Other")).toCompletableFuture().join();assertEquals(row.get("log"),h.log,"case "+h.scenario);assertEquals(row.get("ok"),result.ok());assertEquals(row.get("reconnect"),result.reconnectRequired());assertEquals(row.get("message"),result.message());assertEquals(row.get("map"),h.map);assertEquals(row.get("mode"),h.active==h.old?"old":"new");assertEquals(row.get("world"),h.world==h.oldWorld?"old":"new");assertEquals(row.get("fog"),h.fog);assertEquals(row.get("busy"),service.busy());assertEquals(((Number)row.get("pending")).intValue(),h.events.size());assertEquals(((Number)row.get("packets")).intValue(),h.packets);assertEquals(row.get("players"),h.players.keySet().stream().map(Long::valueOf).toList());assertEquals(row.get("scores"),h.teams.stream().map(t->t.score).toList());
    }
    @Test void originalProtocolMapStemNormalization(){for(var value:ROWS){var row=(Map<?,?>)value;if(row.get("domain").equals("normalize"))assertEquals(row.get("output"),WorldPreflight.normalize((String)row.get("input")));}}
}
