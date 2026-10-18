// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import net.battlespades.player.SourceRandom;
import net.battlespades.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JoinAudioBoundaryTest {
    @Test void identityResetFatalErrorsAndOwnerGuardPreservePerSceneAudioState()throws Exception{
        var sent=new ArrayList<Integer>();var music=new MusicService(p->fail(),new SourceRandom(0));boolean[] fatal={false};var audio=new JoinAudio<Object>(music,()->false,()->{if(fatal[0])throw new AssertionError("fatal mode picker");return null;},p->0,(p,packet)->sent.add(packet.id()),(label,error)->fail(label,error));Object first=new Object(),replacement=new Object();audio.reveal(first,List.of(),new Vector3f(0,0,0));audio.reveal(first,List.of(),new Vector3f(0,0,0));assertEquals(List.of(23),sent);audio.reveal(replacement,List.of(),new Vector3f(0,0,0));assertEquals(List.of(23,23),sent);assertFalse(audio.sent(first));assertTrue(audio.sent(replacement));audio.reset();assertFalse(audio.sent(replacement));fatal[0]=true;assertThrows(AssertionError.class,()->audio.reveal(replacement,List.of(),new Vector3f(0,0,0)));assertFalse(audio.sent(replacement));
        try(var worker=Executors.newSingleThreadExecutor()){assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,()->worker.submit(()->audio.forget(0)).get(4,TimeUnit.SECONDS)).getCause());}
    }
}
