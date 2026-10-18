// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.*;

/** Native GameStats(67) signed int rows and ShowTextMessage(73) float-to-fixed fields. */
public final class ScorePackets {
    private ScorePackets(){}
    public record Row(int playerId,int award){}
    public record GameStats(int count,int team,List<Row> rows) implements GamePacket {
        public GameStats{rows=List.copyOf(rows);}
        public GameStats(int team,List<Row> rows){this(rows.size(),team,rows);}
        @Override public int id(){return 67;}
        @Override public void write(Wire.Writer w){w.u8(id()).i32(count).i32(team);for(int i=0;i<count;i++){var row=rows.get(i);w.i32(row.playerId()).i32(row.award());}}
        public static GameStats read(Wire.Reader r){int count=r.i32(),team=r.i32();if(count>r.remaining()/8)throw new ProtocolException("Truncated GameStats rows");var rows=new ArrayList<Row>();for(int i=0;i<count;i++)rows.add(new Row(r.i32(),r.i32()));return new GameStats(count,team,rows);}
    }
    public record ShowTextMessage(int messageId,float duration) implements GamePacket {
        @Override public int id(){return 73;}
        @Override public void write(Wire.Writer w){w.u8(id()).u8(messageId).i16(Fixed.encodePosition(duration));}
        public static ShowTextMessage read(Wire.Reader r){return new ShowTextMessage(r.u8(),(float)Fixed.decodePosition(r.i16()));}
    }
}
