// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.*;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.support.*;
import net.battlespades.world.MutableWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class BlastCommitGoldenTest {
    static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    static String bits(double value){return String.format(Locale.ROOT,"%016x",Double.doubleToRawLongBits(value));}
    static String vec(Vector p){return bits(p.x())+","+bits(p.y())+","+bits(p.z());}
    static IntStream groups(){return IntStream.range(0,36);}
    @ParameterizedTest @MethodSource("groups") void originalSharedBlastHealthImpulseAndEntityCommit(int group)throws Exception{
        List<String[]> rows;try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/blast-commit.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
        for(var r:rows.subList(group*16,(group+1)*16)){
            var rules=GameRules.serverDefaults().compatibility(Map.of("RULE_SPAWN_PROTECTION_TIME",number(r[9]),"RULE_WEAPON_DAMAGE",number(r[11])));
            try(var h=new LifeHarness(rules)){
                var ports=new BlastPorts(h);ports.build=false;ports.friendly=r[4].equals("1");var wire=new ArrayList<String>();h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){if(p.id()==5||p.id()==46)wire.add("B:"+HexFormat.of().formatHex(p.encode()));}public void owner(int id,GamePacket p){if(p.id()==5||p.id()==46)wire.add("O"+id+":"+HexFormat.of().formatHex(p.encode()));}public boolean inGame(int id){return true;}};
                for(int id=0;id<3;id++){h.admit(id,id==1?3:2);var p=h.runtime.player(id);if(id!=0)p.apply(new ClassCatalog.Selection(Integer.parseInt(r[8]),List.of(5,6,23),List.of(),List.of()));p.body().position(new Vector(36+id*3,36,97));p.orientation(1,0,0);p.health(Integer.parseInt(r[14]));p.motion().controls(id==1&&r[15].equals("1")?32:0,0);p.god(id==1&&r[13].equals("1"));}
                if(!r[12].equals("none"))h.damageModifier=Optional.of((v,a,s,k)->r[12].equals("veto")?0:a*(r[12].equals("half")?.5:2.));
                int wall=Integer.parseInt(r[7]);for(int y=35;y<39;y++)for(int z=96;z<100;z++)if(wall==1||wall==2&&z==97||wall==3&&z>=98)h.world.set(new MutableWorld.Cell(40,y,z),true,0x406080,null);
                for(int i=0;i<2;i++){var placement=new EntityRegistry.Placement();placement.behavior=new DamageableEntityBehavior(Integer.parseInt(r[14]));ports.registry.place(36,42+i,37,97,placement);}
                h.monotonic+=number(r[10]);h.wall+=number(r[10]);wire.clear();ports.events.clear();var request=new BlastRuntime.Request(new Vector(39,36,97),number(r[2]),0,Integer.parseInt(r[1]),r[18].equals("-")?null:h.runtime.player(Integer.parseInt(r[18])));request.radius=number(r[3]);request.minimum=.1;request.maximum=.4;request.ignorePlayerLos=r[5].equals("1");request.predictionFrames=r[6].equals("-")?null:Integer.valueOf(r[6]);request.selfMinimum=r[16].equals("-")?null:number(r[16]);request.selfMaximum=r[17].equals("-")?null:number(r[17]);ports.blasts.apply(request);
                var states=new ArrayList<String>();for(var p:h.runtime.players()){var pending=p.motion().pendingExplosions().stream().map(e->e.targetInputSequence()+","+vec(e.origin())+","+bits(e.radius())+","+bits(e.minimum())+","+bits(e.maximum())).toList();var score=h.runtime.score(p.id());states.add(p.health()+"|"+(p.alive()?1:0)+"|"+(p.spawned()?1:0)+"|"+vec(PlayerMotion.clipped(p.body().state().velocity()))+"|"+(p.motion().snapshot().ownBlast()==null?"-":bits(p.motion().snapshot().ownBlast()))+"|"+(pending.isEmpty()?"-":String.join(";",pending))+"|"+score.kills()+"|"+score.deaths());}
                assertEquals(r[19],String.join("~",states),"state case "+r[0]);var entities=ports.registry.all().stream().map(e->e.id()+","+bits(((DamageableEntityBehavior)e.behavior).health())).toList();assertEquals(r[20],entities.isEmpty()?"-":String.join(";",entities),"entities "+r[0]);assertEquals(r[21],ports.events.isEmpty()?"-":String.join(";",ports.events),"events "+r[0]);assertEquals(r[22],wire.isEmpty()?"-":String.join("|",wire),"wire "+r[0]);
            }
        }
    }
    @Test void originalAchievementScopeAndStimulusFaultBoundaries()throws Exception{
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/blast-commit.tsv.gz")))){for(var line:new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().toList()){var r=line.split("\t");if(!r[0].equals("scope"))continue;int flags=Integer.parseInt(r[1]);try(var h=new LifeHarness()){var p=new BlastPorts(h);p.build=false;p.scopeEnabled=true;p.failBegin=(flags&1)!=0;p.failEnd=(flags&2)!=0;p.failStimulus=(flags&4)!=0;var request=new BlastRuntime.Request(new Vector(0,0,0),10,0,254,null);request.ignorePlayerLos=true;String result="ok";try{p.blasts.apply(request);}catch(IllegalStateException failure){result="failure";}assertEquals(r[2],result);assertEquals(r[3],String.join(";",p.events));}}}
    }

}
