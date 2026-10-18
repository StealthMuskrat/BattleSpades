// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.*;

/** Packet 47 carries opaque retail localization literals; client text is never evaluated. */
public record VotePacket(int playerId,int messageType,List<Candidate> candidates,String title,String description,int allowRevote,int canVote) implements GamePacket {
    public record Candidate(String name,int votes){public Candidate{Objects.requireNonNull(name);}}
    public VotePacket {candidates=List.copyOf(candidates);Objects.requireNonNull(title);Objects.requireNonNull(description);if(candidates.size()>Short.MAX_VALUE)throw new IllegalArgumentException("Vote candidate count");}
    @Override public int id(){return 47;}
    @Override public void write(Wire.Writer w){w.u8(id()).u8(playerId).u8(messageType).i16(candidates.size());for(var candidate:candidates)w.string(candidate.name()).i32(candidate.votes());w.string(title).string(description).u8(allowRevote|(canVote<<2));}
    static VotePacket read(Wire.Reader r){int player=r.u8(),type=r.u8(),count=r.i16();if(count>Math.max(0,(r.remaining()-3)/5))throw new ProtocolException("Truncated vote candidates");var candidates=new ArrayList<Candidate>();for(int i=0;i<count;i++)candidates.add(new Candidate(r.string(),r.i32()));var title=r.string();var description=r.string();int flags=r.u8();return new VotePacket(player,type,candidates,title,description,flags&1,(flags&4)!=0?1:0);}
}
