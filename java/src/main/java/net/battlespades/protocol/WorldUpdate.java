// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.ArrayList;
import java.util.List;

/** Packet 2: player rows, embedded entities and signed-short turret IDs. */
public record WorldUpdate(int loopCount,List<PlayerRow> players,List<EntityRecord> entities,List<TurretRow> turrets) implements GamePacket {
    public record PlayerRow(int playerId,Vector3f position,Vector3f orientation,Vector3f velocity,
            int ping,int pong,int health,int input,int action,int state,int tool,int pickup,
            double jetpackFuel,double protectionTimer,double deploymentYaw) {
        public void write(Wire.Writer w) {
            w.u8(playerId);position.write(w);orientation.write(w);velocity.write(w);
            w.i16(ping).i32(pong).i16(health).u8(input).u8(action).u8(state).u8(tool).u8(pickup & 255)
                .i16(Fixed.encodePosition(jetpackFuel)).i16(Fixed.encodePosition(protectionTimer)).i16(Fixed.encodePosition(deploymentYaw));
        }
        static PlayerRow read(Wire.Reader r) {
            int id=r.u8();var p=Vector3f.read(r);var o=Vector3f.read(r);var v=Vector3f.read(r);
            return new PlayerRow(id,p,o,v,r.i16(),r.i32(),r.i16(),r.u8(),r.u8(),r.u8(),r.u8(),r.i8(),
                Fixed.decodePosition(r.i16()),Fixed.decodePosition(r.i16()),Fixed.decodePosition(r.i16()));
        }
    }
    public record TurretRow(int entityId,double yaw,double pitch) {}
    public WorldUpdate {
        players=List.copyOf(players);entities=List.copyOf(entities);turrets=List.copyOf(turrets);
        if(players.size()>32767 || entities.size()>32767 || turrets.size()>32767) throw new ProtocolException("Snapshot row count exceeds int16");
    }
    @Override public int id() { return 2; }
    @Override public void write(Wire.Writer w) {
        w.u8(id()).i32(loopCount).i16(players.size());for(var player:players) player.write(w);
        w.i16(entities.size());for(var entity:entities) entity.write(w);
        w.i16(turrets.size());for(var turret:turrets) w.i16(turret.entityId).i16(Fixed.encodePosition(turret.yaw)).i16(Fixed.encodePosition(turret.pitch));
    }
    private static int count(Wire.Reader r,int minSize) {
        int n=r.i16();if(n<0 || (long)n*minSize>r.remaining()) throw new ProtocolException("Invalid snapshot count");return n;
    }
    static WorldUpdate read(Wire.Reader r) {
        int loop=r.i32();int n=count(r,56);var players=new ArrayList<PlayerRow>();
        for(int i=0;i<n;i++) players.add(PlayerRow.read(r));
        n=count(r,33);var entities=new ArrayList<EntityRecord>();for(int i=0;i<n;i++) entities.add(EntityRecord.read(r));
        n=count(r,6);var turrets=new ArrayList<TurretRow>();for(int i=0;i<n;i++) turrets.add(new TurretRow(r.i16(),Fixed.decodePosition(r.i16()),Fixed.decodePosition(r.i16())));
        return new WorldUpdate(loop,players,entities,turrets);
    }
}
