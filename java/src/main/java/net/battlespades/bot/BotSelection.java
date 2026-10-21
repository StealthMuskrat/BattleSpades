// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.math.BigInteger;
import java.util.*;
import net.battlespades.player.*;
/** Source join/lifetime item policy. The range second opinion never advances the roster stream. */
public final class BotSelection {
    public static final List<Integer> DEFAULT_CLASSES=List.of(0,1,12,3,2,16,17);
    private BotSelection(){}
    public static List<String> prefabs(Collection<String> available,SourceRandom random,int limit){var pool=new ArrayList<>(new TreeSet<>(available));if(pool.isEmpty()||limit<=0)return List.of();if(pool.stream().allMatch(BotPrefabPolicy::zombie))return random.sample(pool,Math.min(limit,pool.size()));var selected=new ArrayList<String>();for(var purpose:List.of("traversal","cover")){if(selected.size()>=limit)break;var tokens=purpose.equals("traversal")?List.of("bridge","corridor","ladder","steps","stair","platform","tube"):List.of("barricade","wall","barrier","bunker","shield","caltrop");var candidates=pool.stream().filter(name->tokens.stream().anyMatch(name.toLowerCase(Locale.ROOT)::contains)&&BotPrefabPolicy.suitable(name,purpose)).toList();if(!candidates.isEmpty()){String chosen=random.choice(candidates);selected.add(chosen);pool.remove(chosen);}}var variety=pool.stream().filter(name->BotPrefabPolicy.suitable(name,"variety")).toList();int remaining=Math.min(Math.max(0,limit-selected.size()),variety.size());if(remaining>0)selected.addAll(random.sample(variety,remaining));return List.copyOf(selected);}
    public static List<Integer> loadout(int classId,SourceRandom random,Collection<Integer> disabled){var selected=new ArrayList<Integer>();for(var slot:ClassCatalog.get(classId).slots()){var options=slot.stream().filter(tool->!disabled.contains(tool)).toList();if(options.isEmpty())continue;int choice=random.choice(options);double reach=range(choice);final int original=choice;var longer=options.stream().filter(tool->tool!=original&&range(tool)>=30).toList();if(reach>0&&reach<30&&!longer.isEmpty()){long seed=random.stateHash()^(choice*7919L+selected.size());var second=new SourceRandom(BigInteger.valueOf(seed));if(second.random()<.6)choice=second.choice(longer);}selected.add(choice);}return List.copyOf(selected);}
    private static double range(int tool){var profile=BotCombatProfiles.FIREARMS.contains(tool)?net.battlespades.combat.CombatCatalog.WEAPONS.get(tool):null;return profile==null?0:profile.range();}
}
