// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import net.battlespades.protocol.*;
import net.battlespades.protocol.DropPickup.Position;

/** Original carry/drop helper policy. Objective mode ownership and dropped-object simulation are separate. */
public final class ObjectiveCarry {
    private ObjectiveCarry(){}
    public record Vectors(Position position,Position velocity){}
    public record Dropped(int type,int state,Position position,Position velocity){}
    public static boolean validType(int type){return type>=14&&type<=16;}
    public static boolean pickup(PlayerLife player,int type,boolean burdensome,int state,Consumer<GamePacket> reliable) {
        Objects.requireNonNull(player);Objects.requireNonNull(reliable);
        if(!validType(type)||player.pickupId()!=null)return false;
        player.carry(type,burdensome,state);
        reliable.accept(new SimplePackets.PickPickup(player.id(),type,burdensome?1:0));
        return true;
    }
    public static Vectors sanitize(PlayerLife player,int type,Position position,Position velocity) {
        Objects.requireNonNull(player);
        if(position==null||velocity==null||!finite(position)||!finite(velocity))return null;
        var current=player.position();double dx=position.x()-current.x(),dy=position.y()-current.y(),dz=position.z()-current.z();
        for(double delta:new double[]{dx,dy,dz})if(Double.isFinite(delta)&&Double.isInfinite(delta*delta))throw new ArithmeticException("Objective position square overflow");
        if(SpawnMath.sumSquares(dx,dy,dz)>25)return null;
        if(!validType(type))throw new IllegalArgumentException("Unknown objective pickup");
        double speed=Math.sqrt(SpawnMath.sumSquares(velocity.x(),velocity.y(),velocity.z())),maximum=type==14?10:15;
        if(speed>maximum&&speed>1e-6){double scale=maximum/speed;velocity=new Position(velocity.x()*scale,velocity.y()*scale,velocity.z()*scale);}
        return new Vectors(position,velocity);
    }
    private static boolean finite(Position value){return Double.isFinite(value.x())&&Double.isFinite(value.y())&&Double.isFinite(value.z());}
    public static Dropped drop(PlayerLife player,Position position,Position velocity,int serverLoop,Consumer<GamePacket> reliable) {
        return drop(player,position,velocity,()->serverLoop,reliable);
    }
    /** Sample the server stamp only after validation, including when an async mode has yielded. */
    public static Dropped drop(PlayerLife player,Position position,Position velocity,IntSupplier serverLoop,Consumer<GamePacket> reliable) {
        Objects.requireNonNull(player);Objects.requireNonNull(reliable);Objects.requireNonNull(serverLoop);Integer type=player.pickupId();
        if(type==null||!validType(type))return null;
        var vectors=sanitize(player,type,position,velocity);if(vectors==null)return null;
        reliable.accept(new DropPickup(serverLoop.getAsInt(),player.id(),type,vectors.position,vectors.velocity));
        int state=player.pickupState()==null?0:player.pickupState();player.carry(null,false,null);
        return new Dropped(type,state,vectors.position,vectors.velocity);
    }
    /** Dedicated carrier initialization is independent of generic map-entity replication. */
    public static List<GamePacket> reveal(List<PlayerLife> players) {
        var packets=new ArrayList<GamePacket>();for(var player:players)if(player.pickupId()!=null)packets.add(new SimplePackets.PickPickup(player.id(),player.pickupId(),player.pickupBurdensome()?1:0));return List.copyOf(packets);
    }
}
