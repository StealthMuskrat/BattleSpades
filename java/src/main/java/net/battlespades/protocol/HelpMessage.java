// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;
import java.util.*;
/** Legacy packet 109 uniquely uses a big-endian float delay. */
public record HelpMessage(float delay,List<String> messages) implements GamePacket {
    public HelpMessage{messages=List.copyOf(messages);if(messages.size()>255)throw new IllegalArgumentException("Help count");}
    public int id(){return 109;}
    public void write(Wire.Writer w){w.u8(id()).i32(Integer.reverseBytes(Float.floatToRawIntBits(delay))).u8(messages.size());for(var s:messages)w.string(s);}
    static HelpMessage read(Wire.Reader r){float delay=Float.intBitsToFloat(Integer.reverseBytes(r.i32()));int count=r.u8();var messages=new ArrayList<String>();for(int i=0;i<count;i++){var s=r.string();if(!s.isEmpty())messages.add(s);}return new HelpMessage(delay,messages);}
}
