// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.List;
import java.util.function.Predicate;

/** Owner-thread loading cursor. Advances only after bounded transport enqueue succeeds. */
public final class MapSyncTransfer {
    public enum State { WAIT_VALIDATION, REPLY, DELAY, PREPARING, STREAMING, COMPLETE, TIMED_OUT, CANCELLED }
    public enum Mode { FULL, AUTO }
    public record Frame(int prefix,GamePacket packet) {
        public byte[] encode() { return Framing.outbound(prefix,packet.encode()); }
    }
    public record Preparation(long generation,boolean full) {}
    private final Thread owner=Thread.currentThread();
    private final long generation,started;
    private final int serverCrc;
    private final boolean requireValidation,shipsSourceMap;
    private final Mode mode;
    private State state=State.WAIT_VALIDATION;
    private Integer clientCrc;
    private long lastNow,replyAt;
    private List<byte[]> chunks=List.of();
    private int cursor=-1;
    public MapSyncTransfer(long generation,long nowMillis,int serverCrc,Mode mode,boolean requireValidation,boolean shipsSourceMap) {
        if(generation<0 || nowMillis<0) throw new IllegalArgumentException("Transfer generation/time");
        this.generation=generation;started=lastNow=nowMillis;this.serverCrc=serverCrc;this.mode=java.util.Objects.requireNonNull(mode);
        this.requireValidation=requireValidation;this.shipsSourceMap=shipsSourceMap;
    }
    private void owned() { if(Thread.currentThread()!=owner) throw new IllegalStateException("Map transfer accessed outside owner"); }
    public State state() { owned();return state; }
    public void validation(int crc,long nowMillis) {
        owned();advance(nowMillis);if(state==State.WAIT_VALIDATION) {clientCrc=crc;state=State.REPLY;}
    }
    private void advance(long nowMillis) {
        if(nowMillis<lastNow) throw new IllegalArgumentException("Transfer clock moved backwards");lastNow=nowMillis;
        if(state==State.WAIT_VALIDATION && nowMillis-started>=5000) state=requireValidation?State.TIMED_OUT:State.REPLY;
    }
    public Preparation preparation() {
        owned();if(state!=State.PREPARING) throw new IllegalStateException("Snapshot not requested");
        boolean delta=clientCrc!=null && serverCrc!=0 && clientCrc==serverCrc && mode==Mode.AUTO && !shipsSourceMap;
        return new Preparation(generation,!delta);
    }
    /** Compression may run on a worker; delivery of its result must return to this owner. */
    public boolean supplyChunks(long preparedGeneration,List<byte[]> prepared) {
        owned();if(state!=State.PREPARING || preparedGeneration!=generation) return false;
        if(prepared.size()>65536) throw new IllegalArgumentException("Too many map chunks");
        for(var chunk:prepared) if(chunk.length<1 || chunk.length>1024) throw new IllegalArgumentException("Map chunk bounds");
        chunks=prepared.stream().map(byte[]::clone).toList();state=State.STREAMING;return true;
    }
    public void cancel() { owned();if(state!=State.COMPLETE && state!=State.TIMED_OUT) {state=State.CANCELLED;chunks=List.of();} }
    public int pump(long nowMillis,int packetBudget,Predicate<Frame> sink) {
        owned();if(packetBudget<0) throw new IllegalArgumentException("Transfer budget");advance(nowMillis);
        int sent=0;
        if(state==State.REPLY && packetBudget>0) {
            if(!sink.test(new Frame(0x31,new SimplePackets.MapDataValidation(serverCrc)))) return 0;
            replyAt=nowMillis;state=State.DELAY;sent++;
        }
        if(state==State.DELAY && nowMillis-replyAt>=100) state=State.PREPARING;
        while(state==State.STREAMING && sent<packetBudget) {
            Frame frame;
            if(cursor==-1) frame=new Frame(0x32,new LoadingPackets.MapSyncStart());
            else if(cursor<chunks.size()) frame=new Frame(0x31,new LoadingPackets.MapChunk(57,(int)(((double)cursor/Math.max(chunks.size(),1))*100)+1,chunks.get(cursor)));
            else frame=new Frame(0x31,new SimplePackets.MapSyncEnd());
            if(!sink.test(frame)) break;
            sent++;cursor++;
            if(cursor>chunks.size()) {state=State.COMPLETE;chunks=List.of();}
        }
        return sent;
    }
}
