// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;

/** Original crate consumption, respawn, live support settling and fixed-step parachute drop. */
public final class PickupCrateBehavior extends EntityBehavior implements EntityBehavior.Respawning {
    private static final double STEP=1./60;
    private final Consumer<PlayerLife> refill;private final double respawnDelay,dropStartZ;private final Integer soundId;
    private final BiConsumer<PlayerLife,Integer> sound;private final boolean airdrop;private final Consumer<MapEntity> dropCue;
    public PickupCrateBehavior(Consumer<PlayerLife> refill,double respawnDelay,Integer soundId,BiConsumer<PlayerLife,Integer> sound,boolean airdrop,double dropStartZ,Consumer<MapEntity> dropCue){
        this.refill=Objects.requireNonNull(refill);this.respawnDelay=respawnDelay;this.soundId=soundId;this.sound=soundId==null?sound:Objects.requireNonNull(sound);this.airdrop=airdrop;this.dropStartZ=dropStartZ;this.dropCue=dropCue;touchRadius=2.5;
    }
    public static Integer findSupport(PickupWorld world,int x,int y,int start){for(int z=Math.max(0,start);z<240;z++)if(world.solid(x,y,z))return z;return null;}
    private double dropStart(PickupWorld world,int x,int y,int landing){int top=(int)Math.floor(dropStartZ);for(int z=landing-1;z>Math.max(-1,top-1);z--)if(world.solid(x,y,z))return Math.min(landing,z+2);return dropStartZ<landing?dropStartZ:landing;}
    @Override public void onRespawn(MapEntity e,EntityContext ctx){
        if(!airdrop||ctx.world()==null)return;var world=ctx.world();double hx=e.home.x(),hy=e.home.y(),hz=e.home.z();int x=(int)Math.floor(hx),y=(int)Math.floor(hy);if(x<0||x>=512||y<0||y>=512)return;
        Integer landing=findSupport(world,x,y,(int)Math.floor(hz));
        if(landing==null||landing>238){var anchor=world.drySurfaceAnchor(hx,hy,64);hx=anchor.x();hy=anchor.y();x=(int)Math.floor(hx);y=(int)Math.floor(hy);landing=findSupport(world,x,y,(int)Math.floor(anchor.z()));if(landing==null||landing>238)return;}
        while(landing>0&&world.solid(x,y,landing-1))landing--;double start=dropStart(world,x,y,landing);e.x=hx;e.y=hy;e.z=start;e.velocity=new Vector(0,0,0);e.falling=start<landing;e.fallAccumulator=0;e.fallClock=ctx.now();e.parachuteDeployed=false;e.parachuteRemoved=false;e.terrainSupportZ=null;e.terrainOffsetZ=0;
        if(!e.falling){e.z=landing;e.terrainSupportZ=landing;return;}if(dropCue!=null)try{dropCue.accept(e);}catch(RuntimeException ignored){/* Original cosmetic cue boundary. */}
    }
    private void advanceFall(MapEntity e,double dt,EntityContext ctx){
        var world=ctx.world();int x=(int)Math.floor(e.x),y=(int)Math.floor(e.y);Integer landing=world==null?null:findSupport(world,x,y,Math.max(0,(int)Math.floor(e.z)));if(landing==null)landing=239;
        double vz=e.velocity.z(),z=e.z,now=ctx.now(),clock=e.fallClock,elapsed=clock>0&&now>=clock?now-clock:dt;e.fallClock=now;e.fallAccumulator+=elapsed>0?elapsed:0;int steps=0;boolean landed=false;
        while(e.fallAccumulator>=STEP&&steps<30){e.fallAccumulator-=STEP;steps++;vz+=30*STEP;z+=vz*STEP;if(z>=landing){landed=true;break;}double distance=landing-z;if(!e.parachuteDeployed&&distance<10)e.parachuteDeployed=true;if(e.parachuteDeployed&&!e.parachuteRemoved&&distance<2)e.parachuteRemoved=true;if(e.parachuteDeployed&&!e.parachuteRemoved)vz*=.75;}
        if(steps>=30)e.fallAccumulator=0;
        if(landed){e.z=landing;e.velocity=new Vector(0,0,0);e.falling=false;e.fallAccumulator=0;e.parachuteDeployed=true;e.parachuteRemoved=true;e.terrainSupportZ=landing;e.terrainOffsetZ=0;e.terrainCheckAt=now;return;}e.z=z;e.velocity=new Vector(0,0,vz);
    }
    @Override public void onTick(MapEntity e,double dt,EntityContext ctx){
        if(e.falling){advanceFall(e,dt,ctx);return;}var world=ctx.world();if(world==null||ctx.now()<e.terrainCheckAt)return;e.terrainCheckAt=ctx.now()+.1;int x=(int)Math.floor(e.x),y=(int)Math.floor(e.y);if(x<0||x>=512||y<0||y>=512)return;
        Integer support=e.terrainSupportZ;if(support==null){support=findSupport(world,x,y,(int)Math.floor(e.z));if(support==null)return;e.terrainSupportZ=support;e.terrainOffsetZ=e.z-support;return;}if(world.solid(x,y,support))return;
        Integer next=findSupport(world,x,y,support+1);Vector position;
        if(next!=null&&next<=238)position=new Vector(e.x,e.y,next+e.terrainOffsetZ);
        else{position=world.drySurfaceAnchor(e.x,e.y,64);next=(int)Math.floor(position.z());if(next>238)return;e.terrainOffsetZ=position.z()-next;}
        var previous=new Vector(e.x,e.y,e.z);e.x=position.x();e.y=position.y();e.z=position.z();e.terrainSupportZ=next;e.home=position;if((previous.x()!=position.x()||previous.y()!=position.y()||previous.z()!=position.z())&&ctx.move()!=null)ctx.move().accept(e);
    }
    @Override public boolean onTouch(MapEntity e,PlayerLife player,EntityContext ctx){refill.accept(player);if(soundId!=null)sound.accept(player,soundId);e.alive=false;e.respawnAt=ctx.now()+respawnDelay;if(ctx.destroy()!=null)ctx.destroy().accept(e.id());return true;}
}
