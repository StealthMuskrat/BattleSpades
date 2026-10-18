// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import net.battlespades.protocol.*;

/** Source one-shot routing. Sound transport precedes the optional voxel-centred bot hearing stimulus. */
public final class GameplaySounds<P> {
    public record Position(double x,double y,double z){}
    public record Cue(int id,float volume,Position position,float attenuation) {
        public Cue(int id){this(id,1,null,1);}
        public AudioPackets.PlaySound packet(){var p=position==null?new Vector3f(0,0,0):new Vector3f((float)position.x(),(float)position.y(),(float)position.z());return new AudioPackets.PlaySound(id,false,position!=null,volume,0,0,p,position==null?0:attenuation);}
    }
    public interface Ports<P> {
        List<P> players();int id(P p);int team(P p);boolean connected(P p);boolean settled(P p);
        void broadcast(GamePacket packet,P exclude,boolean reliable);void send(P p,GamePacket packet,boolean reliable);
        boolean hearingEnabled();void blockDestroyed(Position center,int sourceId,int team,double lifetime);
    }
    private final Thread owner=Thread.currentThread();private final Ports<P> ports;
    public GameplaySounds(Ports<P> ports){this.ports=Objects.requireNonNull(ports);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Sound outside gameplay owner");}
    public void play(Cue cue,P exclude,boolean reliable,P source){owned();ports.broadcast(cue.packet(),exclude,reliable);if(ports.hearingEnabled()&&source!=null&&cue.position()!=null){var p=cue.position();ports.blockDestroyed(new Position(p.x()+.5,p.y()+.5,p.z()+.5),ports.id(source),ports.team(source),1);}}
    public void personal(P player,Cue cue,boolean reliable){owned();if(ports.connected(player)&&!ports.settled(player))return;ports.send(player,cue.packet(),reliable);}
    public int team(int team,Cue cue,P exclude){owned();var packet=cue.packet();int excluded=exclude==null?-1:ports.id(exclude),sent=0;for(var p:List.copyOf(ports.players())){if(ports.team(p)!=team||excluded>=0&&ports.id(p)==excluded||!ports.connected(p)||!ports.settled(p))continue;ports.send(p,packet,true);sent++;}return sent;}
    public void relative(int goodTeam,int good,int bad,float volume){owned();if(goodTeam!=2&&goodTeam!=3)return;team(goodTeam,new Cue(good,volume,null,1),null);team(5-goodTeam,new Cue(bad,volume,null,1),null);}
    private static SimplePackets.StopSound stopPacket(int loop){if(loop<0||loop>255)throw new IllegalArgumentException("Sound loop id");return new SimplePackets.StopSound(loop);}
    public void stop(int loop){owned();ports.broadcast(stopPacket(loop),null,true);}public void stop(P player,int loop){owned();ports.send(player,stopPacket(loop),true);}
    public static int flyby(String skybox){return Set.of("LunarBase.txt","User_Lunar.txt").contains(skybox==null?"":skybox)?11:10;}
    public static int explosion(Double z,double waterLevel,int dry,int wet){return z!=null&&z>waterLevel?wet:dry;}
}
