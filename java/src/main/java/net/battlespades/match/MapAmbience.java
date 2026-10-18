// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.function.Consumer;
import net.battlespades.protocol.*;
import net.battlespades.world.MapMetadata;

/** Map metadata already supplies validated definitions/defaults; replay only after world reveal. */
public final class MapAmbience {
    private MapAmbience(){}
    public static void send(List<MapMetadata.Ambient> definitions,Vector3f listener,Consumer<GamePacket> send){
        Objects.requireNonNull(listener);send.accept(TransitionPackets.audioFlush());
        int loop=0;for(var definition:definitions.subList(0,Math.min(255,definitions.size()))){loop++;var points=definition.points().stream().map(p->new AudioPackets.AmbientPoint((int)p.x(),(int)p.y(),(int)p.z())).toList();send.accept(new AudioPackets.CreateAmbient(definition.name(),loop,points));boolean local=!points.isEmpty();send.accept(new AudioPackets.PlayAmbient(definition.name(),true,local,(float)definition.volume(),0,loop,local?listener:new Vector3f(0,0,0),local?(float)definition.attenuation():0));}
    }
}
