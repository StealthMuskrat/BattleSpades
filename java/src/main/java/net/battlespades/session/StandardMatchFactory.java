// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.function.Function;
import net.battlespades.player.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.scene.SceneConfig;
import net.battlespades.world.*;
import org.tomlj.TomlTable;

/** Creates TDM/Arena domains from the exact immutable metadata already validated by worker preflight. */
public final class StandardMatchFactory implements HostedSceneState.Factory {
    public record Creation(String map,String code,PreparedMap.Bound prepared,MapMetadata metadata){}
    /** These services belong to one candidate; Services.close owns their external resource cleanup. */
    public record Ports(LifeRuntime.Effects life,LifeRuntime.Inputs inputs,StandardMatchDomain.Services services,HostedPlayerRuntime.Ports players,HostedEntityRuntime.Ports entities){
        public Ports{Objects.requireNonNull(life);Objects.requireNonNull(inputs);Objects.requireNonNull(services);Objects.requireNonNull(players);Objects.requireNonNull(entities);}
    }
    private final Thread owner=Thread.currentThread();private final SceneConfig config;private final TomlTable raw;
    private final LifeRuntime.Clock clock;private final SourceRandom random;private final OwnerLoop loop;private final List<String> maps;private final Function<Creation,Ports> ports;
    public StandardMatchFactory(SceneConfig config,TomlTable raw,LifeRuntime.Clock clock,SourceRandom random,OwnerLoop loop,List<String> maps,Function<Creation,Ports> ports){
        this.config=Objects.requireNonNull(config);this.raw=Objects.requireNonNull(raw);this.clock=Objects.requireNonNull(clock);this.random=Objects.requireNonNull(random);this.loop=Objects.requireNonNull(loop);this.maps=List.copyOf(maps);this.ports=Objects.requireNonNull(ports);
    }
    public Map<String,HostedSceneState.Factory> modes(){return Map.of("tdm",this,"arena",this);}
    @Override public StandardMatchDomain create(String map,String code,PreparedMap.Bound prepared,MapMetadata metadata){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Match factory outside owner");
        if(!Set.of("tdm","arena").contains(code)||!metadata.activeMode().equals(code)||metadata.environment().sourceCrc()!=prepared.world().sourceCrc())throw new IllegalArgumentException("Factory requires matching prepared TDM/Arena metadata");
        var selected=config.forMap(map,code);var settings=HostedPlayerRuntime.Settings.from(raw,selected,metadata);var entitySettings=HostedEntityRuntime.Settings.from(raw);var external=Objects.requireNonNull(ports.apply(new Creation(map,code,prepared,metadata)));StandardMatchDomain domain=null;
        try{domain=new StandardMatchDomain(selected,metadata,prepared,raw,clock,random,loop,maps,external.life(),external.inputs(),external.services());var match=domain;match.entities(host->new HostedEntityRuntime(host,match,prepared,metadata,selected,entitySettings,clock,random,external.entities()));match.runtime(host->new HostedPlayerRuntime(host,match,selected,settings,clock,loop,external.players()));return match;}
        catch(RuntimeException|Error failure){try{if(domain!=null)domain.close();else external.services().close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
}
