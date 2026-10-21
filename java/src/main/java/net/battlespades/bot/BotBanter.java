// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import net.battlespades.player.SourceRandom;

/** Sparse event-driven chat proposals with typing delay, backlog limits and combat deferral. */
public final class BotBanter {
    private static final Map<String,List<String>> LINES=load();private static Map<String,List<String>> load(){var table=net.battlespades.catalog.SourceCatalog.load("bot-live").getTable("banter");var lines=new LinkedHashMap<String,List<String>>();for(var key:table.keySet())lines.put(key,net.battlespades.catalog.SourceCatalog.strings(table.getArray(key)));return Collections.unmodifiableMap(lines);}
    private static final class Voice{final double chance;double nextAt;int nemesis=-1,nemesisCount;String last="";Voice(double chance){this.chance=chance;}}
    public record Queued(double dueAt,int playerId,String text,boolean whileAlive){}public record Line(int playerId,String text){}
    private final Thread owner=Thread.currentThread();private final SourceRandom random;private final LinkedHashMap<Integer,Voice> voices=new LinkedHashMap<>();private final ArrayList<Queued> queue=new ArrayList<>();private double nextGlobalAt;
    public BotBanter(long seed){random=new SourceRandom(seed^0x5eed);}
    void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Bot banter outside owner");}
    public void register(int id,String name){owned();var crc=new CRC32();crc.update(name.getBytes(StandardCharsets.UTF_8));double roll=(crc.getValue()&65535)/65535.;voices.put(id,new Voice(roll<.35?0:.1+.3*(roll-.35)/.65));}
    public void forget(int id){owned();voices.remove(id);queue.removeIf(q->q.playerId()==id);}public void reset(){owned();queue.clear();for(var v:voices.values()){v.nemesis=-1;v.nemesisCount=0;}}
    public void killed(double now,int killer,int victim,String killerName,String victimName,int streak,double distance,boolean melee,boolean explosive){owned();var victimVoice=voices.get(victim);var killerVoice=voices.get(killer);if(killer==victim){say(now,victim,"suicide",Map.of(),1.5,false);return;}if(killerVoice!=null){boolean revenge=killerVoice.nemesis==victim;if(revenge){killerVoice.nemesis=-1;killerVoice.nemesisCount=0;}String kind=revenge?"revenge":streak>=3&&streak%2==1?"streak":melee?"kill_melee":explosive?"kill_explosive":distance>=70?"kill_long":"kill";say(now,killer,kind,Map.of("victim",victimName,"count",streak),Set.of("revenge","streak","kill_long").contains(kind)?2:1,true);}if(victimVoice!=null){boolean repeat=victimVoice.nemesis==killer;victimVoice.nemesisCount=repeat?victimVoice.nemesisCount+1:1;victimVoice.nemesis=killer;String kind=victimVoice.nemesisCount>=2?"death_repeat":explosive?"death_explosive":"death";say(now,victim,kind,Map.of("killer",killerName),kind.equals("death_repeat")?2:1.2,false);}}
    public void phase(double now,String kind){owned();var speakers=new ArrayList<Integer>();voices.forEach((id,v)->{if(v.chance>0)speakers.add(id);});random.shuffle(speakers);for(var id:speakers.subList(0,Math.min(2,speakers.size())))say(now,id,kind,Map.of(),3,true);}
    void say(double now,int id,String kind,Map<String,Object> names,double boost,boolean alive){var v=voices.get(id);if(v==null||v.chance<=0||now<v.nextAt||queue.size()>=3||random.random()>v.chance*boost)return;var options=LINES.get(kind).stream().filter(s->!s.equals(v.last)).toList();String text=random.choice(options);for(var e:names.entrySet())text=text.replace("{"+e.getKey()+"}",e.getValue().toString());int count=text.codePointCount(0,text.length());if(count>60)text=text.substring(0,text.offsetByCodePoints(0,60));v.last=text;v.nextAt=now+random.uniform(25,55);double typing=1.2+.11*text.codePointCount(0,text.length())+random.uniform(0,1.5);queue.add(new Queued(now+typing,id,text,alive));}
    public List<Line> due(double now,Set<Integer> busy){owned();queue.removeIf(q->now-q.dueAt()>7);if(now<nextGlobalAt)return List.of();for(int i=0;i<queue.size();i++){var q=queue.get(i);if(q.dueAt()>now||q.whileAlive()&&busy.contains(q.playerId()))continue;queue.remove(i);nextGlobalAt=now+6;return List.of(new Line(q.playerId(),q.text()));}return List.of();}
    public List<Queued> queued(){owned();return List.copyOf(queue);}
}
