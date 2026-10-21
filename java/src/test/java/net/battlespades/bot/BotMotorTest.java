// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.SourceRandom;
import net.battlespades.combat.CombatCatalog;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotFoundationsTest.*;
import static net.battlespades.bot.BotSourceFixture.*;

class BotMotorTest {
    static final Map<String,Object> SOURCE=read("bot-motor");
    static BotProfile profile(){return new BotProfile("Bot","normal",.55,.55,.5,.55,.5,.32,.12,3.8,13,.6,.6,24,.055,List.of());}
    static final class World implements BotLiveSteering.World {
        String kind;World(String kind){this.kind=kind;}public long topology(){return 0;}
        public boolean solid(int x,int y,int z){if(x<0||x>=512||y<0||y>=512||z<0||z>=240)return false;if(z==239)return true;if(kind.equals("water"))return false;if(kind.equals("hole")&&x>=252&&x<=254)return false;if(kind.equals("wall")&&x==252&&y>=246&&y<=254&&z>=36&&z<=39)return true;if(kind.equals("roof")&&x>=251&&(z==39||z==36))return true;return z==40;}
        public boolean water(int x,int y){for(int z=0;z<239;z++)if(solid(x,y,z))return false;return true;}
        public boolean clip(double x,double y,double z){if(x<0||x>=512||y<0||y>=512)return true;if(z<0)return false;int h=(int)Math.floor(z);if(h==239)h=238;else if(h>=240)return true;return solid((int)Math.floor(x),(int)Math.floor(y),h);}
        Cell ray(Vector p,Vector d,double length){for(int i=1;i<=(int)(length/.05);i++){var c=new Cell((int)Math.floor(p.x()+d.x()*i*.05),(int)Math.floor(p.y()+d.y()*i*.05),(int)Math.floor(p.z()+d.z()*i*.05));if(solid(c.x(),c.y(),c.z()))return c;}return null;}
    }
    static final class Ports implements BotMotor.Services {
        Player player;final World world;long loop;int movement;boolean[] actions={false,false,false,false};Action queued;double queuedAt;boolean dead;final List<Action> executions=new ArrayList<>();
        Ports(World world){this.world=world;}
        public Player player(){return player;}public boolean current(){return true;}public boolean crouch(){return (movement&32)!=0;}
        public Player target(int id){return id==8?targetPlayer(player):null;}public long loop(){return loop;}
        public void orientation(Vector v){player=copy(player,v,player.tool());}public void movement(int flags){movement=flags;}public void actions(boolean p,boolean s,boolean z,boolean h){actions=new boolean[]{p,s,z,h};}
        public boolean select(int tool){player=copy(player,player.orientation(),tool);return true;}public boolean execute(Action action){executions.add(action);return true;}public int stock(int tool){return 3;}public double nextShot(){return 0;}public double fireInterval(){return CombatCatalog.get(player.tool()).interval();}
        public Cell ray(Vector p,Vector d,double distance){return world.ray(p,d,distance);}public boolean drySafe(Vector p){return !world.water((int)Math.floor(p.x()),(int)Math.floor(p.y()))&&!world.clip(p.x(),p.y(),p.z())&&!world.clip(p.x(),p.y(),p.z()+1);}public void buriedDeath(){dead=true;player=BrainUnofficial.policy(player,false,player.blocks(),player.ammoClip(),player.ammoReserve(),player.team(),player.canDig());}
        public void stage(BotMotor motor,Action action,double at){if(queued==null){queued=action;queuedAt=at;}}
        void drain(BotMotor motor,double now){if(queued!=null){var action=queued;queued=null;motor.commit(action,Math.max(queuedAt,now));}}
    }
    static Player targetPlayer(Player p){return Player.basic(8,3,3,0,true,true,new Vector(260.5,250.5,37.75),new Vector(260.5,250.5,37.75),new Vector(1,0,0),new Vector(0,0,0),100,6,50,10,40,true);}
    @Test void unchangedDirectorMotorRetainsActionTimingKeysAndWaterLeases(){
        for(var item:list(SOURCE.get("motor"))){var trace=map(item);String kind=string(trace.get("kind"));var world=new World(kind.equals("water")?"water":"flat");var ports=new Ports(world);var budget=new BotMotor.Budget();var motor=new BotMotor(1,2,profile(),new SourceRandom(421),0,ports,world,budget);int index=0;
            for(var sample:list(trace.get("rows"))){var row=map(sample);ports.player=BotPlayerTestData.player(row.get("player"));world.kind=string(row.get("world"));ports.loop=number(row.get("loop"));double now=real(row.get("now"));budget.reset();try{if(row.get("intent")!=null)assertTrue(motor.accept(BotPerceptionTestData.intent(row.get("intent")),7,1,0,now));motor.tick(now,real(row.get("dt")));ports.drain(motor,now);matches(row.get("movement"),ports.movement);matches(row.get("actions"),List.of(ports.actions[0],ports.actions[1],ports.actions[2],ports.actions[3]));matches(row.get("orientation"),ports.player.orientation());matches(row.get("tool"),ports.player.tool());matches(row.get("pending")==null?null:observe(BotPerceptionTestData.action(row.get("pending"))),motor.state().pending());var f=motor.state().feedback();matches(row.get("feedback"),Arrays.asList(f.kind(),f.accepted(),f.position(),f.frame(),f.at(),f.request(),f.reason(),f.taskAccepted(),f.taskAt()));matches(list(row.get("executions")).stream().map(BotPerceptionTestData::action).map(BotSourceFixture::observe).toList(),ports.executions);matches(row.get("wade_until"),motor.wadeObservedUntil());if(row.get("landed") instanceof String)assertTrue(motor.waterLandedAt()<-1e200);else matches(row.get("landed"),motor.waterLandedAt());}catch(AssertionError failure){throw new AssertionError(kind+" frame "+index,failure);}index++;}
        }
    }
    @Test void unchangedBodyWidthSteeringRetainsBrakingRoofAndWaterCollisionGates(){
        for(var item:list(SOURCE.get("steering"))){var trace=map(item);var world=new World(string(trace.get("kind")));var budget=new BotLiveSteering.Budget();var steering=new BotLiveSteering(world,budget);int index=0;for(var sample:list(trace.get("rows"))){var row=map(sample);var p=BotPlayerTestData.player(row.get("player"));var body=new BotLiveSteering.Body(p.id(),p.generation(),p.position(),p.velocity(),p.grounded(),p.wade(),bool(row.get("crouch")),p.jetpackId());budget.reset();try{matches(row.get("result"),steering.direction(body,vector(row.get("direction")),BotPerceptionTestData.affordance(row.get("affordance")),integer(row.get("drop")),real(row.get("now"))));}catch(AssertionError failure){throw new AssertionError(trace.get("kind")+" frame "+index,failure);}index++;}}
    }
    static Player copy(Player p,Vector aim,int tool){return new Player(p.id(),p.generation(),p.team(),p.classId(),p.alive(),p.spawned(),p.position(),p.eye(),aim,p.velocity(),p.health(),tool,p.blocks(),p.ammoClip(),p.ammoReserve(),p.bot(),p.weaponTool(),p.loadout(),p.prefabs(),p.orientedStock(),p.carriedEntityId(),p.jetpackId(),p.jetpackFuel(),p.grounded(),p.wade(),p.reloading(),p.lastActionKind(),p.lastActionAccepted(),p.lastActionPosition(),p.lastActionFrame(),p.lastActionAt(),p.lastDamageAt(),p.lastDamageSourceId(),p.lastDamageSourcePosition(),p.lifeId(),p.deployableStock(),p.lastActionRequestId(),p.lastActionReason(),p.lastTaskAccepted(),p.lastTaskAt(),p.canShoot(),p.weaponAmmo(),p.spawnProtected(),p.lastDamageKind(),p.canDig());}
}
