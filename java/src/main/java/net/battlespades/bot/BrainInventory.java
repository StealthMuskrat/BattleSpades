// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.combat.CombatCatalog;
import net.battlespades.player.LifeCatalog;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;

/** Source hand selection, stowed wallets and loadout-authorized traversal capabilities. */
final class BrainInventory {
    private BrainInventory(){}
    private static final org.tomlj.TomlParseResult DATA=net.battlespades.catalog.SourceCatalog.load("bot-live");
    static final Set<Integer> FIREARMS=BotCombatProfiles.FIREARMS;
    static final Set<Integer> MELEE=Set.copyOf(net.battlespades.catalog.SourceCatalog.integers(DATA.getArray("melee")));
    static int weapon(Player p,Double distance){var firearms=p.loadout().stream().filter(FIREARMS::contains).toList();var wallets=new HashMap<Integer,Ammo>();for(var a:p.weaponAmmo())wallets.put(a.tool(),a);var live=firearms.stream().filter(t->{var a=wallets.get(t);return a!=null&&a.clip()+a.reserve()>0;}).toList();if(!live.isEmpty()){int primary=firearms.getFirst(),choice=live.contains(primary)?primary:live.getFirst();var sidearm=live.stream().filter(t->t!=primary).findFirst().orElse(null);if(distance!=null&&sidearm!=null&&choice==primary){boolean holding=p.weaponTool()==sidearm,loaded=wallets.get(sidearm).clip()>0;if(loaded&&wallets.get(primary).clip()<=0&&distance<28)choice=sidearm;else if(CombatCatalog.get(primary).category().equals("sniper")&&distance<(holding?22:13))choice=sidearm;}return choice;}if(p.loadout().contains(p.weaponTool())&&FIREARMS.contains(p.weaponTool()))return p.weaponTool();if(p.loadout().contains(p.tool())&&FIREARMS.contains(p.tool()))return p.tool();return firearms.isEmpty()?p.loadout().isEmpty()?p.tool():p.loadout().getFirst():firearms.getFirst();}
    static Ammo wallet(Player p,int tool){return p.weaponAmmo().stream().filter(a->a.tool()==tool).findFirst().orElse(new Ammo(tool,p.ammoClip(),p.ammoReserve()));}
    static Integer melee(Player p){return !p.canShoot()?null:p.loadout().stream().filter(MELEE::contains).findFirst().orElse(null);}
    static double swingInterval(Integer tool){var profile=tool==null?null:CombatCatalog.WEAPONS.get(tool);return Math.max(.35,profile==null?0:profile.interval());}
    static BotDigProfile dig(Player p){return !p.canShoot()||!p.canDig()?null:BotDigProfile.best(p.loadout()).orElse(null);}
    static double sprintRoom(Player p){return Math.min(9,Math.max(1.75,.65+Math.hypot(p.velocity().x(),p.velocity().y())*10));}
    static boolean sprintsUphill(Player p){return !net.battlespades.catalog.SourceCatalog.integers(DATA.getArray("no_sprint_uphill")).contains(p.classId());}
    static Set<Affordance> movementAbilities(Player p){var abilities=EnumSet.of(Affordance.JUMP);var pack=LifeCatalog.PACKS.get(p.jetpackId());var times=DATA.getTable("pack_flight_seconds");Double seconds=times.getDouble(Integer.toString(p.jetpackId()));if(pack!=null&&seconds!=null){double reserve=pack.activationCost()+pack.drain()*seconds+5;if(p.grounded()&&!p.wade()&&p.jetpackFuel()>=reserve){abilities.add(Affordance.JETPACK);if(net.battlespades.catalog.SourceCatalog.integers(DATA.getArray("climbing_packs")).contains(p.jetpackId()))abilities.add(Affordance.JETPACK_CLIMB);}}if(dig(p)!=null)abilities.add(Affordance.BREACH);return Collections.unmodifiableSet(abilities);}
    static BotProfile fallback(int id){return new BotProfile("Bot"+id,"normal",.55,.55,.5,.55,.5,.32,.12,3.8,13,.6,.6,24,.055,List.of());}
}
