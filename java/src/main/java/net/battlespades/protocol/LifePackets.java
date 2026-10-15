// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Character life packets from shared.packet: fixed XYZ health source and canonical boolean bytes. */
public final class LifePackets {
    private LifePackets(){}
    public record SetHP(int hp,int damageType,Vector3f source) implements GamePacket {
        @Override public int id(){return 5;}
        @Override public void write(Wire.Writer w){w.u8(id()).u8(hp).u8(damageType);source.writeFixed(w);}
        static SetHP read(Wire.Reader r){return new SetHP(r.u8(),r.u8(),Vector3f.readFixed(r));}
    }
    public record KillAction(int playerId,int killerId,int killType,int respawnTime,int killCount,boolean domination,boolean revenge) implements GamePacket {
        @Override public int id(){return 46;}
        @Override public void write(Wire.Writer w){w.u8(id()).u8(playerId).u8(killerId).u8(killType).u8(respawnTime).u8(killCount).u8(domination?1:0).u8(revenge?1:0);}
        static KillAction read(Wire.Reader r){return new KillAction(r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.u8()!=0,r.u8()!=0);}
        public KillAction quiet(){return new KillAction(playerId,killerId,killType,respawnTime,0,false,false);}
    }
    public record WeaponReload(int playerId,int toolId,boolean done) implements GamePacket {
        @Override public int id(){return 76;}
        @Override public void write(Wire.Writer w){w.u8(id()).u8(playerId).u8(toolId).u8(done?1:0);}
        static WeaponReload read(Wire.Reader r){return new WeaponReload(r.u8(),r.u8(),r.u8()!=0);}
    }
}
