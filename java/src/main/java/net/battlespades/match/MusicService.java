// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.function.Consumer;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.player.SourceRandom;
import net.battlespades.protocol.*;

/** Source music family state and reliable flush/stop/specific-track ordering. */
public final class MusicService {
    private static final List<String> GAMEPLAY=SourceCatalog.strings(SourceCatalog.load("round-media").getArray("gameplay")),ENDING=SourceCatalog.strings(SourceCatalog.load("round-media").getArray("ending"));
    private static final Set<Integer> DIGITS=Set.copyOf(SourceCatalog.integers(SourceCatalog.load("round-media").getArray("familyDigits")));
    private final Thread owner=Thread.currentThread();private final Consumer<GamePacket> broadcast;private final SourceRandom random;private String family;
    public MusicService(Consumer<GamePacket> broadcast,SourceRandom random){this.broadcast=Objects.requireNonNull(broadcast);this.random=Objects.requireNonNull(random);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Music outside owner");}
    public String family(){owned();return family;}
    public static String family(String track){if(track==null||track.isEmpty())return null;int split=track.lastIndexOf('_');return split>0&&split<track.length()-1&&track.substring(split+1).codePoints().allMatch(DIGITS::contains)?track.substring(0,split):track;}
    public static void playTo(Consumer<GamePacket> send,String track){send.accept(TransitionPackets.audioFlush());send.accept(new SimplePackets.StopMusic());send.accept(new AudioPackets.Music(track,0));}
    public void play(String track){owned();playTo(broadcast,track);family=family(track);}
    public void stop(){owned();broadcast.accept(new SimplePackets.StopMusic());family=null;}
    public String gameplayTrack(boolean enabled){owned();return enabled?random.choice(GAMEPLAY):null;}
    public String joinTrack(boolean started,boolean ended,boolean ending,boolean retiring,boolean timeout){owned();return ended&&ending&&!retiring||started&&!ended&&timeout?random.choice(ENDING):null;}
    public void gameplay(boolean enabled){owned();var track=gameplayTrack(enabled);if(track!=null)play(track);else stop();}
    public boolean lastMan(){owned();if(Objects.equals(family,family(GAMEPLAY.getFirst())))return false;play(random.choice(GAMEPLAY));return true;}
    public boolean ending(){owned();if(Objects.equals(family,family(ENDING.getFirst())))return false;play(random.choice(ENDING));return true;}
    public void timeout(){owned();play(random.choice(ENDING));}
}
