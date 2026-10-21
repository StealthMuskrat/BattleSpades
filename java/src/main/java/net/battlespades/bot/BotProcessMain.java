// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.io.*;
import java.util.*;
import net.battlespades.bot.BotState.Observer;
import static net.battlespades.bot.BotMessages.*;
/** Private child entry point. Standard output is exclusively the allowlisted binary pipe. */
public final class BotProcessMain {
    private BotProcessMain(){}
    public static void main(String[] args)throws IOException{
        if(args.length!=2)throw new IllegalArgumentException("Bot worker rates required");double hz=Double.parseDouble(args[0]),rate=Double.parseDouble(args[1]);if(!Double.isFinite(hz)||!Double.isFinite(rate)||hz<1||rate<1)throw new IllegalArgumentException("Invalid bot worker rates");
        BotNavigationWorld world=null;SimpleBotBrain brain=null;
        while(true){Object value;try{value=BotWire.receive(System.in);}catch(EOFException closed){return;}if(!(value instanceof BotProcessSession.Request request))throw new IOException("Unexpected bot worker request");var faults=new ArrayList<String>();boolean rejected=false;long processed=-1;var intents=new ArrayList<Intent>();
            if(request.snapshot()!=null){world=new BotNavigationWorld(new PlanningBudget(rate,hz),()->System.nanoTime()/1e9);brain=new SimpleBotBrain(world,hz,()->System.nanoTime()/1e9);try{world.load(request.snapshot());brain.resetForMap(request.snapshot().mapEpoch());}catch(RuntimeException invalid){rejected=true;faults.add("bot map snapshot: "+invalid);world=null;brain=null;}}
            if(world!=null){if(request.delta()!=null)world.apply(request.delta());var live=new HashSet<Observer>();int roster=0;double latest=-Double.MAX_VALUE;for(var frame:request.frames()){int bots=0;for(var p:frame.players()){if(p.bot())bots++;if(p.id()==frame.observerId()&&p.generation()==frame.observerGeneration()&&p.alive()&&p.spawned())live.add(new Observer(p.id(),p.generation()));}roster=Math.max(roster,bots);latest=Math.max(latest,frame.createdAt());}if(!request.frames().isEmpty()){world.budget().scaleFor(Math.max(roster,live.size()));world.budget().refreshWaiters(live,latest);}
                for(var original:request.frames()){processed=Math.max(processed,original.frameId());if(original.mapEpoch()!=world.epoch()||original.topologyVersion()>world.version())continue;var frame=original.topologyVersion()==world.version()?original:original.withTopology(world.version());world.beginPlanning(new Observer(frame.observerId(),frame.observerGeneration()),frame.createdAt());boolean cancel=false;try{var intent=brain.decide(frame);cancel=intent!=null;if(intent!=null)intents.add(intent);}catch(RuntimeException fault){cancel=true;faults.add("bot decision "+frame.observerId()+": "+fault);brain.resetBot(frame.observerId(),frame.observerGeneration());}finally{world.endPlanning(cancel);}}
            }
            BotWire.send(System.out,new BotProcessSession.Response(rejected,processed,intents,world==null?Map.of():world.budget().snapshot(),brain==null?Map.of():brain.metrics(),faults));
        }
    }
}
