// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.function.*;
import net.battlespades.protocol.*;
import net.battlespades.world.MapMetadata;

/** Source per-connection ambience/music attempt. Ordinary faults leave it available for an explicit retry. */
public final class JoinAudio<P> {
    private final Thread owner=Thread.currentThread();private final MusicService music;private final BooleanSupplier bed;private final Supplier<String> modeTrack;private final ToIntFunction<P> id;private final BiConsumer<P,GamePacket> send;private final BiConsumer<String,RuntimeException> failure;private final Map<Integer,P> sent=new HashMap<>();
    public JoinAudio(MusicService music,BooleanSupplier bed,Supplier<String> modeTrack,ToIntFunction<P> id,BiConsumer<P,GamePacket> send,BiConsumer<String,RuntimeException> failure){this.music=Objects.requireNonNull(music);this.bed=Objects.requireNonNull(bed);this.modeTrack=Objects.requireNonNull(modeTrack);this.id=Objects.requireNonNull(id);this.send=Objects.requireNonNull(send);this.failure=Objects.requireNonNull(failure);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Join audio outside owner");}
    public boolean sent(P player){owned();return sent.get(id.applyAsInt(player))==player;}
    public void reveal(P player,List<MapMetadata.Ambient> ambience,Vector3f position){owned();if(sent(player))return;try{MapAmbience.send(ambience,position,packet->send.accept(player,packet));String track=modeTrack.get();if(track==null||track.isEmpty())track=music.gameplayTrack(bed.getAsBoolean());if(track!=null)MusicService.playTo(packet->send.accept(player,packet),track);sent.put(id.applyAsInt(player),player);}catch(RuntimeException error){failure.accept("reveal ambient/music",error);}}
    public void forget(int id){owned();sent.remove(id);}public void reset(){owned();sent.clear();}
}
