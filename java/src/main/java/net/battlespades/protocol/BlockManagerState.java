// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.ArrayList;
import java.util.List;

/** Actual retail packet38 from server/prefab_actions.py, not reconstructed ServerBlockItem. */
public record BlockManagerState(List<User> users,List<Damaged> damaged) implements GamePacket {
    public record User(int x,int y,int z,double health) { public User { finite(health); } }
    public record Damaged(int x,int y,int z,double health,int originalColor) { public Damaged { finite(health); } }
    public BlockManagerState { users=List.copyOf(users);damaged=List.copyOf(damaged); }
    private static void finite(double value) { if(!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite block health"); }
    private static int quarters(double value,boolean up) { return (int)Math.max(1,Math.min(255,up?Math.ceil(value*4-1e-9):Math.floor(value*4+0.5))); }
    @Override public int id() { return 38; }
    @Override public void write(Wire.Writer w) {
        w.u8(id()).i32(damaged.size());for(var row:damaged) w.i16(row.x).i16(row.y).i16(row.z).u8(quarters(row.health,false)).color(row.originalColor);
        w.i32(users.size());for(var row:users) w.i16(row.x).i16(row.y).i16(row.z).u8(quarters(row.health,true));
        w.i32(0); // No occupied records are emitted by the source repair path.
    }
    static BlockManagerState read(Wire.Reader r) {
        int count=r.i32();if(count<0 || (long)count*10>r.remaining()) throw new ProtocolException("Invalid damaged count");
        var damaged=new ArrayList<Damaged>(count);for(int i=0;i<count;i++) damaged.add(new Damaged(r.i16(),r.i16(),r.i16(),r.u8()/4.0,r.color()));
        count=r.i32();if(count<0 || (long)count*7>r.remaining()) throw new ProtocolException("Invalid user count");
        var users=new ArrayList<User>(count);for(int i=0;i<count;i++) users.add(new User(r.i16(),r.i16(),r.i16(),r.u8()/4.0));
        if(r.i32()!=0) throw new ProtocolException("Occupied BlockManagerState records not implemented");
        return new BlockManagerState(users,damaged);
    }
}
