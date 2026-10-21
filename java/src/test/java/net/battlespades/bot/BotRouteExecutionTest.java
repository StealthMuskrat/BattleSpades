// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.io.*;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.battlespades.bot.BotFoundationsTest.*;
import static net.battlespades.bot.BotSourceFixture.*;
import static net.battlespades.bot.BotState.*;

class BotRouteExecutionTest {
    static final Map<String,Object> SOURCE=read("bot-routes");
    static RouteStep step(Object value){var r=map(value);Breach b=null;if(r.get("breach")!=null){var v=map(r.get("breach"));b=new Breach(cell(v.get("source")),cell(v.get("destination")),cell(v.get("target_cell")),list(v.get("blocking_cells")).stream().map(BotFoundationsTest::cell).toList(),integer(v.get("tool_id")),bool(v.get("secondary")),real(v.get("fire_interval")),integer(v.get("estimated_swings")));}Edge edge=null;if(r.get("entry_edge")!=null){var e=list(r.get("entry_edge"));edge=new Edge(cell(e.get(0)),cell(e.get(1)));}return new RouteStep(vector(r.get("waypoint")),BotPerceptionTestData.affordance(r.get("affordance")),b,edge);}
    static BotGoal goal(Object value){var g=map(value);return new BotGoal(new ArrayList<>(list(g.get("key"))),vector(g.get("position")),string(g.get("role")),real(g.get("arrival_radius")),bool(g.get("sprint")),BotPerceptionTestData.nullableVector(g.get("watch_position")));}
    static BotNavigationWorld world()throws IOException{return world("raw_zlib",true);}
    static BotNavigationWorld world(String field,boolean wall)throws IOException{byte[] raw;try(var in=new java.util.zip.InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(string(SOURCE.get(field)))))){raw=in.readAllBytes();}var world=new BotNavigationWorld();world.load(new MapSnapshot(7,0,raw,"tdm","routes",List.of(),"retail"));if(wall)world.apply(new WorldDelta(7,1,List.of(new VoxelChange(253,250,38,true,0x123456,9))));return world;}
    static void compare(Map<String,Object> row,BotMessages.Intent actual){matches(row.get("intent")==null?null:observe(BotPerceptionTestData.intent(row.get("intent"))),actual);}
    @Test void sourceOrdinaryExecutionKeepsNativeLandingAndTakeoffContracts()throws Exception{
        for(var item:list(SOURCE.get("reached"))){var r=map(item);assertEquals(bool(r.get("result")),BrainRouteProgress.reached(step(r.get("step")),vector(r.get("at")),bool(r.get("water")),BotPerceptionTestData.nullableVector(r.get("previous")),bool(r.get("last")),bool(r.get("current"))));}
        for(var item:list(SOURCE.get("walk"))){var trace=map(item);var world=world();double[] now={100};var executor=new BrainWalk(world,new BrainIntents(()->now[0]));var state=new BotBrainState(7,1,1,-1,true);state.mapEpoch=7;state.modeEpoch=1;state.lifeId=1;state.route=list(trace.get("route")).stream().map(BotRouteExecutionTest::step).toList();state.routeTopologyVersion=1;state.waypointProgressAt=100;state.goal=goal(trace.get("goal"));var original=state.route;
            for(var sample:list(trace.get("rows"))){var r=map(sample);var f=BotPerceptionTestData.frame(r.get("frame"));var p=f.players().getFirst();now[0]=f.createdAt();var step=state.routeIndex<state.route.size()?state.route.get(state.routeIndex):original.getFirst();compare(r,executor.follow(f,p,state,state.goal,step,now[0],false));matches(r.get("route_index"),state.routeIndex);matches(r.get("remaining"),state.route.size());matches(r.get("note"),state.stepNote);}
        }
    }
    @Test void sourceExcavationCadenceAndFiniteFollowerQueuesRetainOneOwner()throws Exception{
        for(var item:list(SOURCE.get("breach"))){var trace=map(item);double[] now={100};var executor=new BrainBreach(world(),new BrainIntents(()->now[0]));var state=new BotBrainState(7,1,1,-1,true);state.mapEpoch=7;state.modeEpoch=1;state.lifeId=1;var step=step(trace.get("step"));state.route=List.of(step);state.routeTopologyVersion=1;state.goal=goal(trace.get("goal"));
            for(var sample:list(trace.get("rows"))){var r=map(sample);var f=BotPerceptionTestData.frame(r.get("frame"));var p=f.players().getFirst();now[0]=f.createdAt();BotMessages.Intent actual;
                if(string(trace.get("kind")).equals("assist")){var digger=f.players().get(1);boolean wait=executor.waits(f,p,state,state.goal,digger,now[0]);matches(r.get("wait"),wait);actual=wait?executor.assist(f,p,state,state.goal,digger):null;}else actual=executor.dig(f,p,state,state.goal,step,now[0]);
                compare(r,actual);matches(r.get("route_index"),state.routeIndex);matches(r.get("remaining"),state.route.size());matches(r.get("note"),state.stepNote);
            }
        }
    }
    @Test void sourceNavigationAndWaterRecoveryKeepOneRetainedMovementOwner()throws Exception{
        for(var item:list(SOURCE.get("navigation"))){var trace=map(item);var world=world();double[] now={200};var intents=new BrainIntents(()->now[0]);var skills=new RecoveryDriver(world);var navigation=new BrainNavigation(world,intents,skills,new BrainSkills(skills,intents));var state=new BotBrainState(7,1,1,-1,true);var goal=goal(trace.get("goal"));
            for(var sample:list(trace.get("rows"))){var row=map(sample);var frame=BotPerceptionTestData.frame(row.get("frame"));now[0]=frame.createdAt();var actual=navigation.navigate(frame,frame.players().getFirst(),state,goal,now[0],string(trace.get("kind")).equals("wet")?true:null);compare(row,actual);matches(row.get("route_index"),state.routeIndex);matches(row.get("remaining"),state.route.size());matches(row.get("note"),state.stepNote);}
        }
        for(var item:list(SOURCE.get("water"))){var trace=map(item);var world=world("water_raw_zlib",false);double[] now={300};var intents=new BrainIntents(()->now[0]);var water=new BrainWater(world,intents,new BrainBreach(world,intents));var state=new BotBrainState(7,1,1,-1,true);state.goal=goal(trace.get("goal"));
            for(var sample:list(trace.get("rows"))){var row=map(sample);var frame=BotPerceptionTestData.frame(row.get("frame"));now[0]=frame.createdAt();compare(row,water.exit(frame,frame.players().getFirst(),state,row.get("step")==null?null:step(row.get("step")),now[0],bool(row.get("force"))));}
        }
    }

}
