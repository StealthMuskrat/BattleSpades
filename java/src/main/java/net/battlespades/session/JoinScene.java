// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.List;
import net.battlespades.protocol.*;
import net.battlespades.world.MutableWorld;

/** Domain boundary: the application owns loaders; modes/players provide their actual state and admission. */
public interface JoinScene extends AsyncPackets {
    record Admission(PlayerPackets.CreatePlayer player,int color,List<GamePacket> afterCreate) {
        public Admission {afterCreate=List.copyOf(afterCreate);}
    }
    MutableWorld world();
    InitialInfo initialInfo(int capability);
    StateData stateData(int playerId,int capability);
    String skybox();
    default int requiredCapability() {return 0;}
    PlayerRoster roster();
    Admission admit(int reservedId,SimplePackets.NewPlayerConnection request,PlayerPackets.SetClassLoadout selection,int capability);
    default Admission admit(int reservedId,SimplePackets.NewPlayerConnection request,PlayerPackets.SetClassLoadout selection) {return admit(reservedId,request,selection,0);}
    default List<GamePacket> reveal(int playerId) {return List.of();}
    default List<GamePacket> reveal(int playerId,int serverLoop) {return reveal(playerId);}
    default void input(int playerId,LiveClientInput input,int receivedServerLoop) {}
    default void roundTripMillis(int playerId,Double measurement) {}
    default void actionLink(int playerId,net.battlespades.player.ActionClock.Link link) {}
    default void packet(int playerId,GamePacket packet) {}
    default void disconnected(int playerId) {}
}
