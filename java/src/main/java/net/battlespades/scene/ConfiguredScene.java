// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.scene;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.battlespades.physics.MovementProfile;
import net.battlespades.player.ClassCatalog;
import net.battlespades.protocol.*;
import net.battlespades.session.*;
import net.battlespades.world.MutableWorld;

/** Configured metadata and stock atomic selection around a separately owned game domain.
 * The game domain must resolve identity/team/mode restrictions and create an actual life.
 * This binding does not stand in for those implementations.
 */
public final class ConfiguredScene implements JoinScene {
    public interface Gameplay {
        MutableWorld world();
        PlayerRoster roster();
        Admission admit(int id,SimplePackets.NewPlayerConnection request,ClassCatalog.Selection selection,int capability);
        default InitialInfo configureInitialInfo(InitialInfo packet,int capability){return packet;}
        default StateData configureStateData(StateData packet,int capability){return packet;}
        default int requiredCapability(){return 0;}
        default List<GamePacket> reveal(int id){return List.of();}
        default void input(int id,LiveClientInput input,int receivedLoop){}
        default void packet(int id,GamePacket packet){}
        default void disconnected(int id){}
    }
    private final Thread owner=Thread.currentThread();
    private final SceneConfig config;
    private final ScenePackets.Environment environment;
    private final Supplier<ScenePackets.State> state;
    private final Gameplay gameplay;
    public ConfiguredScene(SceneConfig config,ScenePackets.Environment environment,Supplier<ScenePackets.State> state,Gameplay gameplay){
        this.config=Objects.requireNonNull(config);this.environment=Objects.requireNonNull(environment);this.state=Objects.requireNonNull(state);this.gameplay=Objects.requireNonNull(gameplay);
        if(config.modeCode().startsWith("hc_"))throw new IllegalArgumentException("Hardcore scene policy is not implemented");
        if(environment.sourceCrc()!=gameplay.world().sourceCrc())throw new IllegalArgumentException("Metadata CRC differs from committed world");
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Scene accessed outside owner");}
    public MovementProfile movement(int classId){owned();return ClassCatalog.movement(classId,config.modeCode(),config.rules());}
    public double gravity(){owned();return environment.gravity();}
    @Override public MutableWorld world(){owned();return gameplay.world();}
    @Override public PlayerRoster roster(){owned();return gameplay.roster();}
    @Override public int requiredCapability(){owned();return gameplay.requiredCapability();}
    @Override public InitialInfo initialInfo(int capability){owned();return ScenePackets.finishInitial(config,gameplay.configureInitialInfo(ScenePackets.baseInitial(config,environment,new ScenePackets.ServerIdentity(0,0,false,config.port())),capability));}
    @Override public StateData stateData(int id,int capability){owned();return ScenePackets.finishState(config,gameplay.configureStateData(ScenePackets.baseState(config,environment,state.get(),id),capability));}
    @Override public String skybox(){owned();return ScenePackets.skybox(config,environment);}
    @Override public Admission admit(int id,SimplePackets.NewPlayerConnection request,PlayerPackets.SetClassLoadout pending,int capability){
        owned();var selection=ClassCatalog.normalize(pending!=null?pending.classId():request.class_id(),pending!=null?pending.loadout():List.of(),pending!=null?pending.prefabs():List.of(),pending!=null?pending.ugcTools():List.of(),request.class_id(),config.rules().disabledTools());
        return gameplay.admit(id,request,selection,capability);
    }
    @Override public List<GamePacket> reveal(int id){owned();return gameplay.reveal(id);}
    @Override public void input(int id,LiveClientInput input,int loop){owned();gameplay.input(id,input,loop);}
    @Override public void packet(int id,GamePacket packet){owned();gameplay.packet(id,packet);}
    @Override public void disconnected(int id){owned();gameplay.disconnected(id);}
}
