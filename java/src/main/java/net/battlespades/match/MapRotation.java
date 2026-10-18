// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.function.Predicate;
import net.battlespades.player.NameUnicode;

/** Source cycle/retail-cap/recency/size ordering against an immutable worker catalog. */
public final class MapRotation {
    public record Settings(boolean sizeFit,double perPlayer,double minimumArea,int recentExclude,double botWeight,boolean retailCaps){
        public Settings {if(!Double.isFinite(perPlayer)||perPlayer<=0||!Double.isFinite(minimumArea)||minimumArea<0||!Double.isFinite(botWeight))throw new IllegalArgumentException("Map fit settings");}
        public static Settings defaults(){return new Settings(true,8000,24000,2,1,true);}
    }
    private final Thread owner=Thread.currentThread();private final MapCatalog catalog;private final Settings settings;private final ArrayDeque<String> history=new ArrayDeque<>();
    public MapRotation(MapCatalog catalog,Settings settings){this.catalog=Objects.requireNonNull(catalog);this.settings=Objects.requireNonNull(settings);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Rotation outside owner");}
    public MapCatalog catalog(){return catalog;}
    public List<String> history(){owned();return List.copyOf(history);}
    public void notePlayed(String raw){owned();String name=NameUnicode.strip(raw==null?"":raw);if(name.isEmpty()||!history.isEmpty()&&NameUnicode.casefold(history.getLast()).equals(NameUnicode.casefold(name)))return;if(history.size()==16)history.removeFirst();history.addLast(name);}
    public static int fit(Long area,double players,double perPlayer,double minimumArea){if(area==null||area<=0)return 1;double ideal=Math.max(minimumArea,Math.min(262144,players*perPlayer));if(ideal==0)throw new ArithmeticException("Map fit division by zero");double ratio=Math.max(area/ideal,ideal/area);return ratio<=1.75?0:ratio<=3?1:2;}
    public <P> List<String> rank(List<String> available,String current,Iterable<P> roster,Predicate<P> isBot){
        owned();int humans=0;double players=0;for(var player:roster){boolean bot=isBot.test(player);if(!bot)humans++;players+=bot?settings.botWeight():1;}String currentKey=NameUnicode.casefold(current==null?"":current);int pivot=-1;for(int i=0;i<available.size();i++)if(NameUnicode.casefold(available.get(i)).equals(currentKey)){pivot=i;break;}
        var ordered=new ArrayList<String>();if(pivot>=0){ordered.addAll(available.subList(pivot+1,available.size()));ordered.addAll(available.subList(0,pivot));}else ordered.addAll(available);ordered.removeIf(name->NameUnicode.casefold(name).equals(currentKey));
        var earlier=history.stream().map(NameUnicode::casefold).filter(name->!name.equals(currentKey)).toList();var recent=new HashMap<String,Integer>();for(int i=earlier.size()-1,rank=0;i>=0;i--,rank++)if(!recent.containsKey(earlier.get(i))&&recent.size()<Math.max(0,settings.recentExclude()))recent.put(earlier.get(i),rank);
        record Ranked(String name,boolean overCap,boolean recent,int recency,int tier,int index){}
        var ranked=new ArrayList<Ranked>();for(int i=0;i<ordered.size();i++){var name=ordered.get(i);String key=NameUnicode.casefold(name);var cap=catalog.cap(name);var recency=recent.get(key);ranked.add(new Ranked(name,settings.retailCaps()&&cap!=null&&humans>cap,recency!=null,recency==null?0:-recency,settings.sizeFit()?fit(catalog.area(name),players,settings.perPlayer(),settings.minimumArea()):0,i));}
        ranked.sort(Comparator.comparing(Ranked::overCap).thenComparing(Ranked::recent).thenComparingInt(Ranked::recency).thenComparingInt(Ranked::tier).thenComparingInt(Ranked::index));return ranked.stream().map(Ranked::name).toList();
    }
}
