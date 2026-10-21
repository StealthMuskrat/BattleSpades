// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static net.battlespades.bot.BotFoundationsTest.*;
import static net.battlespades.bot.BotSourceFixture.*;
import static net.battlespades.bot.BotState.*;

class SimpleBotBrainTest {
    static final Map<String,Object> SOURCE=read("bot-brain");
    static BotNavigationWorld world(boolean wet)throws IOException{byte[] raw;try(var in=new java.util.zip.InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(string(SOURCE.get(wet?"water_raw_zlib":"raw_zlib")))))){raw=in.readAllBytes();}var world=new BotNavigationWorld();world.load(new MapSnapshot(7,0,raw,"tdm","brain",List.of(),"retail"));return world;}
    @Test void sourceCompleteDecisionLoopRetainsLifeFencesWaterOwnershipAndCooperation()throws Exception{
        for(var item:list(SOURCE.get("core"))){var trace=map(item);double[] now={100};var brain=new SimpleBotBrain(world(string(trace.get("kind")).equals("water")),8,()->now[0]);int n=0;
            for(var sample:list(trace.get("rows"))){var row=map(sample);var f=BotPerceptionTestData.frame(row.get("frame"));now[0]=f.createdAt();try{BotRouteExecutionTest.compare(row,brain.decide(f));matches(row.get("metrics"),brain.metrics());}catch(AssertionError failure){throw new AssertionError(trace.get("kind")+" frame "+n,failure);}n++;}
        }
    }
    @Test void sourceCommunityRulesRetainFairCombatViewAndUrgentObjectiveActions()throws Exception{
        for(var item:list(SOURCE.get("community"))){var trace=map(item);double[] now={200};var brain=new SimpleBotBrain(world(false),8,()->now[0]);int n=0;
            for(var sample:list(trace.get("rows"))){var row=map(sample);var f=BotPerceptionTestData.frame(row.get("frame"));now[0]=f.createdAt();try{matches(observe(BotPerceptionTestData.frame(row.get("view"))),BrainUnofficial.combatView(f,f.players().getFirst()));BotRouteExecutionTest.compare(row,brain.decide(f));}catch(AssertionError failure){throw new AssertionError(trace.get("kind")+" variant "+trace.get("variant")+" frame "+n,failure);}n++;}
        }
    }
    @Test void sourceClassWorkRetainsStockCooldownAndChargeSafety()throws Exception{
        for(var item:list(SOURCE.get("work"))){var trace=map(item);String kind=string(trace.get("kind"));double[] now={300};var world=world(false);world.apply(new WorldDelta(7,0,list(trace.get("edits")).stream().map(v->{var e=map(v);return new VoxelChange(integer(e.get("x")),integer(e.get("y")),integer(e.get("z")),bool(e.get("solid")),integer(e.get("color")),5);}).toList()));var brain=new SimpleBotBrain(world,8,()->now[0]);brain.resetForMap(7);var state=new BotBrainState(7,1,1,-1,true);int n=0;
            for(var sample:list(trace.get("rows"))){var row=map(sample);var f=BotPerceptionTestData.frame(row.get("frame"));var p=f.players().getFirst();var d=BotPerceptionTestData.decision(row.get("decision"));now[0]=f.createdAt();var actual=switch(kind){case "medic"->brain.deploy.medic(f,p,state,now[0]);case "turret","mine","radar","disguise","cannon"->brain.deploy.classGadget(f,p,state,now[0],d);case "dynamite","c4"->brain.charges.plant(f,p,state,now[0],d);case "owned_charge"->brain.charges.own(f,p,state,now[0],n%2==0);case "objective_mine"->brain.blocks.mine(f,p,state,now[0]);default->brain.blocks.work(f,p,state,now[0],d);};try{BotRouteExecutionTest.compare(row,actual);}catch(AssertionError failure){throw new AssertionError(kind+" frame "+n,failure);}n++;}
        }
    }
}
