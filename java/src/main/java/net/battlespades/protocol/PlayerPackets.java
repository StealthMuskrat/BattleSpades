// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.ArrayList;
import java.util.List;

public final class PlayerPackets {
    private PlayerPackets() {}
    static void strings(Wire.Writer w,List<String> values) { w.u8(values.size());for(String value:values) w.string(value); }
    static List<String> strings(Wire.Reader r) { int count=r.u8();var list=new ArrayList<String>(count);for(int i=0;i<count;i++) list.add(r.string());return list; }
    public record SetClassLoadout(int playerId,int classId,int instant,List<Integer> loadout,List<String> prefabs,List<Integer> ugcTools) implements GamePacket {
        public SetClassLoadout { loadout=InitialInfo.counted(loadout,255);prefabs=InitialInfo.counted(prefabs,255);ugcTools=InitialInfo.counted(ugcTools,255); }
        @Override public int id() { return 13; }
        @Override public void write(Wire.Writer w) { w.u8(id()).u8(playerId).u8(classId).u8(instant);InitialInfo.writeBytes(w,loadout);strings(w,prefabs);InitialInfo.writeBytes(w,ugcTools); }
        static SetClassLoadout read(Wire.Reader r) {
            return new SetClassLoadout(r.u8(),r.u8(),r.u8(),InitialInfo.readBytes(r),strings(r),InitialInfo.readBytes(r));
        }
        /** Observed retail variant omits only the final empty UGC count; receive counts are capped at64. */
        public static SetClassLoadout receive(byte[] body) {
            var r=new Wire.Reader(body);if(r.u8()!=13) throw new ProtocolException("Expected SetClassLoadout");
            int player=r.u8(),cls=r.u8(),instant=r.u8();var loadout=InitialInfo.readBytes(r);var prefabs=strings(r);
            var tools=r.remaining()==0?List.<Integer>of():InitialInfo.readBytes(r);r.end();
            if(loadout.size()>64 || prefabs.size()>64 || tools.size()>64) throw new ProtocolException("Client loadout count exceeds64");
            return new SetClassLoadout(player,cls,instant,loadout,prefabs,tools);
        }
    }
    public record CreatePlayer(int playerId,int demoPlayer,int classId,int team,int dead,int localLanguage,
            Vector3f position,Vector3f orientation,String name,List<Integer> loadout,List<String> prefabs) implements GamePacket {
        public CreatePlayer { loadout=InitialInfo.counted(loadout,255);prefabs=InitialInfo.counted(prefabs,255); }
        @Override public int id() { return 28; }
        @Override public void write(Wire.Writer w) {
            w.u8(id()).u8(playerId).u8(demoPlayer).u8(classId).u8(team).u8(dead).u8(localLanguage);
            position.writeFixed(w);orientation.writeFixed(w);w.string(name);InitialInfo.writeBytes(w,loadout);strings(w,prefabs);
        }
        static CreatePlayer read(Wire.Reader r) {
            return new CreatePlayer(r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),Vector3f.readFixed(r),Vector3f.readFixed(r),r.string(),InitialInfo.readBytes(r),strings(r));
        }
    }
    /** Wire codec only. Normal roster reveal uses CreatePlayer to avoid the retail pickup sentinel bug. */
    public record ExistingPlayer(int playerId,int demoPlayer,int team,int classId,int tool,int pickup,int dead,
            int score,int forcedTeam,int localLanguage,int color,String name,List<Integer> loadout,List<String> prefabs) implements GamePacket {
        public ExistingPlayer { loadout=InitialInfo.counted(loadout,255);prefabs=InitialInfo.counted(prefabs,255); }
        @Override public int id() { return 14; }
        @Override public void write(Wire.Writer w) {
            w.u8(id()).u8(playerId).u8(demoPlayer).u8(team).u8(classId).u8(tool).u8(pickup).u8(dead).i32(score).u8(forcedTeam).u8(localLanguage).color(color).string(name);
            InitialInfo.writeBytes(w,loadout);strings(w,prefabs);
        }
        static ExistingPlayer read(Wire.Reader r) {
            return new ExistingPlayer(r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.i32(),r.u8(),r.u8(),r.color(),r.string(),InitialInfo.readBytes(r),strings(r));
        }
    }
}
