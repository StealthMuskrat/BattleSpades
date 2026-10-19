// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.ugc;

import java.util.*;
import java.util.zip.Deflater;
import net.battlespades.protocol.*;

/** Prepared lobby VXL stream must finish before MapDataValidation or normal MapSync. */
public final class UgcWire {
    private UgcWire(){}
    public static List<GamePacket> source(byte[] raw){if(raw.length==0||raw.length>256*1024*1024)throw new IllegalArgumentException("Invalid UGC source size");var packets=new ArrayList<GamePacket>();packets.add(new SimplePackets.MapDataStart());var zip=new Deflater();byte[] buffer=new byte[32767];try{for(int at=0;at<raw.length;){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("UGC source preparation cancelled");int count=Math.min(1048,raw.length-at);zip.setInput(raw,at,count);at+=count;while(!zip.needsInput()){int n=zip.deflate(buffer);if(n>0)packets.add(new LoadingPackets.MapChunk(56,(int)((long)at*100/raw.length),Arrays.copyOf(buffer,n)));}}zip.finish();while(!zip.finished()){int n=zip.deflate(buffer);if(n>0)packets.add(new LoadingPackets.MapChunk(56,100,Arrays.copyOf(buffer,n)));}}finally{zip.end();}packets.add(new SimplePackets.MapDataEnd());return List.copyOf(packets);}
    public static List<GamePacket> batches(UgcProject project){var rows=project.placements();var packets=new ArrayList<GamePacket>();for(int at=0;at<rows.size();at+=512)packets.add(new UgcPackets.Batch(rows.subList(at,Math.min(rows.size(),at+512)).stream().map(p->new UgcPackets.Item(UgcProject.modeId(p.mode()),p.x(),p.y(),p.z(),p.item())).toList()));if(packets.isEmpty())packets.add(new UgcPackets.Batch(List.of()));return List.copyOf(packets);}
    public static UgcPackets.Objectives objectives(UgcProject project){var v=project.validation();return new UgcPackets.Objectives(UgcProject.modeId(v.mode()),v.objectives().stream().map(o->new UgcPackets.Objective(o.id(),o.value())).toList());}
}
