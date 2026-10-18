// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.function.*;
import net.battlespades.match.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.MapMetadata;

/** Binds the services to actual host delivery and canonical live actors; candidate ranking remains explicit. */
public final class HostedMedia {
    private final ApplicationHost host;private final Supplier<LifeRuntime> lives;
    private final MusicService music;private final MapBallot<PlayerLife> ballot;
    private final HostedVoting voting;
    private JoinAudio<PlayerLife> joinAudio;
    private final Map<Integer,PlayerLife> revealed=new HashMap<>();
    public HostedMedia(ApplicationHost host,Supplier<LifeRuntime> lives,List<String> available,SourceRandom random,OwnerLoop loop){
        this(host,lives,available,random,loop,null);
    }
    public HostedMedia(ApplicationHost host,Supplier<LifeRuntime> lives,SourceRandom random,OwnerLoop loop,HostedVoting voting){this(host,lives,List.of(),random,loop,Objects.requireNonNull(voting));}
    private HostedMedia(ApplicationHost host,Supplier<LifeRuntime> lives,List<String> available,SourceRandom random,OwnerLoop loop,HostedVoting voting){
        this.host=Objects.requireNonNull(host);this.lives=Objects.requireNonNull(lives);music=new MusicService(p->host.broadcast(p,Delivery.RELIABLE),random);
        this.voting=voting;ballot=voting==null?new MapBallot<>(available,this::players,PlayerLife::id,p->!p.bot()&&host.inGame(p.id()),()->loop.nowNanos()/1_000_000_000.0,loop::sleep,p->host.broadcast(p,Delivery.RELIABLE),loop):voting.service().map();
    }
    private Map<Integer,PlayerLife> players(){var result=new LinkedHashMap<Integer,PlayerLife>();for(var player:lives.get().players())result.put(player.id(),player);return result;}
    /** Bind before the first admission. Scene catch-up invokes this while reliable direct delivery is already available. */
    public void joinAudio(Supplier<MapMetadata> metadata,Supplier<? extends BaseRoundMode> mode,BooleanSupplier bed,BiConsumer<String,Throwable> failure){
        if(joinAudio!=null)throw new IllegalStateException("Join audio already bound");Objects.requireNonNull(metadata);Objects.requireNonNull(mode);Objects.requireNonNull(failure);
        var audio=new JoinAudio<PlayerLife>(music,bed,()->{var active=mode.get();return active==null?null:active.joinMusicTrack(music);},PlayerLife::id,(p,packet)->host.sendPlayer(p.id(),packet),(label,error)->failure.accept(label,error));
        lives.get().joinAudio(p->{
            if(players().get(p.id())!=p||p.bot())return;MapMetadata map;Vector3f position;
            try{map=metadata.get();var at=p.position();position=new Vector3f((float)at.x(),(float)at.y(),(float)at.z());}catch(RuntimeException error){failure.accept("reveal ambient/music",error);return;}
            audio.reveal(p,map.ambientSounds(),position);
        });joinAudio=audio;
    }
    public MusicService music(){return music;}public MapBallot<PlayerLife> ballot(){return ballot;}
    public void packet(PlayerLife actor,GamePacket packet){if(voting==null)ballot.packet(actor,packet);else voting.packet(actor,packet);}
    /** Invoke after first-input world reveal, once per canonical actor/scene. */
    public void reveal(PlayerLife player,MapMetadata metadata){
        if(players().get(player.id())!=player||!host.inGame(player.id())||revealed.get(player.id())==player)return;var position=player.position();if(joinAudio==null)MapAmbience.send(metadata.ambientSounds(),new Vector3f((float)position.x(),(float)position.y(),(float)position.z()),packet->host.sendPlayer(player.id(),packet));if(voting==null)ballot.reveal(packet->host.sendPlayer(player.id(),packet));else voting.reveal(player);revealed.put(player.id(),player);
    }
    public void forget(int id){revealed.remove(id);if(joinAudio!=null)joinAudio.forget(id);if(voting==null)ballot.forget(id);else voting.forget(id);}
    public void resetVote(){if(voting==null){ballot.cancel();ballot.consume();}else voting.reset();}
}
