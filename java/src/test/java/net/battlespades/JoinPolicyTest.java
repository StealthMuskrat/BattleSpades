// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.util.List;
import net.battlespades.session.*;
import net.battlespades.protocol.MapSyncTransfer;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;
import static org.junit.jupiter.api.Assertions.*;

class JoinPolicyTest {
    @Test void passwordPacingUnicodeAttemptsAndAddressLockoutMatchReferencePolicy() {
        var gate=new JoinPasswordGate(new JoinPasswordGate.Policy("café",3,60000,1000));var session=gate.session();
        assertEquals(JoinPasswordGate.Answer.RETRY,session.answer("host","wrong",0));assertEquals(JoinPasswordGate.Answer.RETRY,session.answer("host","café",499));
        assertEquals(JoinPasswordGate.Answer.REFUSED,session.answer("host","wrong",999));assertTrue(gate.locked("host",1998));assertFalse(gate.locked("other",1998));assertFalse(gate.locked("host",1999));
        assertEquals(JoinPasswordGate.Answer.ACCEPTED,gate.session().answer("host","café",2000));
        assertEquals(JoinPasswordGate.Answer.RETRY,gate.session().answer("other","x".repeat(65),2000));assertFalse(gate.session().cleared());
        assertFalse(new JoinPasswordGate.Policy("hidden",3,60000,0).toString().contains("hidden"));
    }
    @Test void lockoutsHaveFiniteStorageAndOpenSessionsStayCleared() {
        var gate=new JoinPasswordGate(new JoinPasswordGate.Policy("s",1,60000,60000));for(int i=0;i<4097;i++) gate.session().answer("host"+i,"x",i);
        assertTrue(gate.lockCount()<=4096);assertTrue(gate.locked("host4096",5000));assertFalse(gate.locked("host0",5000));
        var open=new JoinPasswordGate(JoinPasswordGate.Policy.open()).session();assertTrue(open.cleared());assertEquals(JoinPasswordGate.Answer.ACCEPTED,open.answer("h","anything",0));
    }
    @Test void reservationsAreDistinctAndReusedOnlyAfterRelease() {
        var slots=new PlayerSlots(2);var a=slots.reserve().orElseThrow();var b=slots.reserve().orElseThrow();assertEquals(0,a.id());assertEquals(1,b.id());assertTrue(slots.reserve().isEmpty());
        a.close();a.close();assertEquals(0,slots.reserve().orElseThrow().id());assertEquals(2,slots.occupied());b.close();assertThrows(IllegalStateException.class,b::id);
    }
    @Test void joinSettingsParseActualSectionsClampSourceRangesAndRejectSecretsSafely() throws Exception {
        var root=JoinSettings.from(Toml.parse(java.nio.file.Path.of("../config.toml")));assertEquals(MapSyncTransfer.Mode.FULL,root.mapMode());assertEquals(0,root.clockLoopBias());assertEquals(1250,root.transitionGraceMillis());
        assertEquals(2500,JoinSettings.from(Toml.parse("[network]\ntransition_grace_seconds=2.5")).transitionGraceMillis());
        var cfg=JoinSettings.from(Toml.parse("[server]\npassword='café'\npassword_max_attempts=99\npassword_timeout_seconds=1.0\npassword_lockout_seconds=-1.0\n[game]\nmap_sync_mode='AUTO'\n[debug]\nclock_sync_loop_bias=1"));
        assertEquals(10,cfg.password().maxAttempts());assertEquals(5000,cfg.password().timeoutMillis());assertEquals(0,cfg.password().lockoutMillis());assertEquals(MapSyncTransfer.Mode.AUTO,cfg.mapMode());assertEquals(1,cfg.clockLoopBias());
        assertEquals(17,JoinSettings.from(Toml.parse("[network]\nmap_air_catchup_batch_limit=17")).replayBudget());
        assertEquals(MapSyncTransfer.Mode.FULL,JoinSettings.from(Toml.parse("[game]\nmap_sync_mode=\"unknown\"")).mapMode());
        for(String secret:List.of("x".repeat(65),"a\0b")) {var error=assertThrows(IllegalArgumentException.class,()->new JoinPasswordGate.Policy(secret,3,60000,0));assertFalse(error.getMessage().contains(secret));}
    }
}
