// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;
import java.util.*;
public final class MatchPackets {
    private MatchPackets(){}
    public record ChangePlayer(int playerId,int action,int value) implements GamePacket {
        public static ChangePlayer marker(int id,boolean visible){return new ChangePlayer(id,8,visible?1:0);}
        @Override public int id(){return 17;}
        @Override public void write(Wire.Writer w){w.u8(id()).i16(playerId).u8(action);if(action==8||action==9)w.u8(value);}
        public static ChangePlayer read(Wire.Reader r){int id=r.i16(),action=r.u8();return new ChangePlayer(id,action,action==8||action==9?r.u8():0);}
    }
    public record LocalisedMessage(int chatType,int localiseParameters,String stringId,List<String> parameters,int overridePrevious) implements GamePacket {
        public LocalisedMessage{Objects.requireNonNull(stringId);parameters=List.copyOf(parameters);if(parameters.size()>255)throw new IllegalArgumentException("Localised parameter count");}
        @Override public int id(){return 50;}
        @Override public void write(Wire.Writer w){w.u8(id()).u8(chatType).u8(localiseParameters).string(stringId).u8(parameters.size());for(var p:parameters)w.string(p);w.u8(overridePrevious);}
        public static LocalisedMessage read(Wire.Reader r){int type=r.u8(),local=r.u8();var id=r.string();int count=r.u8();if(count>r.remaining()-1)throw new ProtocolException("Truncated localised parameters");var params=new ArrayList<String>();for(int i=0;i<count;i++)params.add(r.string());return new LocalisedMessage(type,local,id,params,r.u8());}
    }
}
