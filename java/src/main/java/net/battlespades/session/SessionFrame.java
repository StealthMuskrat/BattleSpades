// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import net.battlespades.protocol.*;
import net.battlespades.transport.enet.Delivery;

/** One complete application packet, with directional server framing computed once. */
public final class SessionFrame {
    private final byte[] body,wire;
    private final int prefix;
    private final Delivery delivery;
    public SessionFrame(GamePacket packet) {this(0x30,packet.encode(),Delivery.RELIABLE);}
    public SessionFrame(int prefix,byte[] body,Delivery delivery) {
        if(body.length==0 || body[0]==3 || body[0]==65) throw new ProtocolException("Unsafe outbound packet");
        this.body=body.clone();this.prefix=prefix;this.delivery=java.util.Objects.requireNonNull(delivery);wire=Framing.outbound(prefix,body);
    }
    public int id() {return body[0]&255;}
    public int prefix() {return prefix;}
    public Delivery delivery() {return delivery;}
    public byte[] body() {return body.clone();}
    public byte[] encode() {return wire.clone();}
    public int wireBytes(){return wire.length;}
    int memoryBytes() {return Math.addExact(body.length,wire.length);}
}
