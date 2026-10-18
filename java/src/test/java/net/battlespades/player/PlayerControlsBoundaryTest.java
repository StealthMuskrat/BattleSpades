// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.concurrent.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerControlsBoundaryTest {
    @Test void paletteCancellationAndIdentityGuardRetireOldTimerBeforeSlotReuse()throws Exception{
        try(var h=new LifeHarness();var loop=new OwnerLoop(0,16,16,error->fail(error))){
            var sent=new ArrayList<Integer>();var palette=new PaletteRelay(h.runtime,()->true,()->h.monotonic,loop,(p,packet)->sent.add(((SimplePackets.SetColor)packet).value()));h.admit(0,2);var old=h.runtime.player(0);palette.set(old,1);h.monotonic+=.04;palette.set(old,2);assertEquals(1,loop.pending());assertEquals(2,old.color());palette.forget(old);assertEquals(0,loop.pending());h.runtime.transitionDetach(0);h.admit(0,2);var fresh=h.runtime.player(0);palette.set(fresh,3);palette.set(old,4);loop.pump(1_000_000_000);loop.pump(1_000_000_000);assertEquals(List.of(1,3),sent);assertEquals(3,fresh.color());assertEquals(0,loop.pending());
            try(var worker=Executors.newSingleThreadExecutor()){var error=assertThrows(ExecutionException.class,()->worker.submit(()->palette.set(fresh,5)).get(4,TimeUnit.SECONDS));assertInstanceOf(IllegalStateException.class,error.getCause());}palette.reset();
        }
    }
}
