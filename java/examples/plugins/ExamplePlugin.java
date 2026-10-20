// SPDX-License-Identifier: AGPL-3.0-or-later
package example;
import java.util.*;
import java.util.concurrent.*;
import net.battlespades.match.*;
import net.battlespades.plugin.RuntimePlugin;
/** Opt-in port of plugins/_example_plugin.py. Package separately with a ServiceLoader descriptor. */
public final class ExamplePlugin implements RuntimePlugin {
    private final Map<Integer,Integer> streaks=new HashMap<>();private Context context;
    public String name(){return "Example";}
    public String author(){return "BattleSpades";}
    public String description(){return "Template plugin — logs on load and announces kill streaks.";}
    public CompletionStage<Void> onLoad(Context context){this.context=context;streaks.clear();context.log("[Example plugin] loaded");return CompletableFuture.completedFuture(null);}
    public CompletionStage<Void> onEvent(ModeEvents.Event event){if(!event.name().equals("on_player_kill"))return CompletableFuture.completedFuture(null);var a=event.arguments();var killer=(Scoreboard.Player)a.get(0);var victim=(Scoreboard.Player)a.get(1);if(killer==null||killer==victim)return CompletableFuture.completedFuture(null);int n=streaks.merge(killer.id(),1,Integer::sum);streaks.put(victim.id(),0);String label=switch(n){case 3->"on a spree";case 5->"dominating";case 10->"unstoppable";default->null;};if(label!=null)context.broadcast(killer.life.name()+" is "+label+"! ("+n+" kills)");return CompletableFuture.completedFuture(null);}
}
