// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import net.battlespades.player.PlayerLife;
import net.battlespades.physics.PlayerPhysics.Vector;

/** Original Team's roster, cyclic spawns, score lock, counters and intel state. */
public final class TeamState {
    private final Thread owner=Thread.currentThread();
    public final int id;
    public String name;
    public int red,green,blue,spawnIndex;
    public long score,captures,kills;
    public boolean lockedScore,infiniteBlocks;
    private final List<PlayerLife> players=new ArrayList<>();
    private final List<Vector> spawns=new ArrayList<>();
    public Vector intelPosition;
    public PlayerLife intelHolder;
    public TeamState(int id,String name,int red,int green,int blue){this.id=id;this.name=Objects.requireNonNull(name);this.red=red;this.green=green;this.blue=blue;}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Team outside owner");}
    public int color(){owned();return red<<16|green<<8|blue;}
    public List<PlayerLife> players(){owned();return List.copyOf(players);}
    public int playerCount(){owned();return players.size();}
    public void add(PlayerLife player){owned();Objects.requireNonNull(player);if(players.stream().noneMatch(p->p==player))players.add(player);}
    public void remove(PlayerLife player){owned();for(int i=0;i<players.size();i++)if(players.get(i)==player){players.remove(i);return;}}
    public void addSpawn(Vector at){owned();spawns.add(Objects.requireNonNull(at));}
    public List<Vector> spawns(){owned();return List.copyOf(spawns);}
    public Vector spawn(){owned();if(spawns.isEmpty())return new Vector(id==2?64:448,256,58);var at=spawns.get(spawnIndex<0?spawns.size()+spawnIndex:spawnIndex);spawnIndex=Math.floorMod((long)spawnIndex+1,spawns.size());return at;}
    public void addScore(long points){owned();if(!lockedScore)score=Math.addExact(score,points);}
    public void addCapture(){owned();captures=Math.incrementExact(captures);addScore(1);}
    public void addKill(){owned();kills=Math.incrementExact(kills);addScore(1);}
    public void reset(){owned();score=0;captures=0;kills=0;spawnIndex=0;intelHolder=null;}
    public void intel(Vector at){owned();intelPosition=Objects.requireNonNull(at);intelHolder=null;}
    public void pickup(PlayerLife player){owned();intelHolder=player;}
    public void drop(Vector at){intel(at);}
    public void returnHome(Vector at){intel(at);}
}
