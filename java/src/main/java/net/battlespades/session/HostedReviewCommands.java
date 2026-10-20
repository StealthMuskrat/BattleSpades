// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.battlespades.player.*;

/** Original administrator review commands, with Unicode-safe retail chat chunks. */
public final class HostedReviewCommands {
    private final StandardMatchDomain match;
    public HostedReviewCommands(StandardMatchDomain match){this.match=Objects.requireNonNull(match);register("acreport","/acreport [player]","Anti-cheat suspicion scores (detection only)",this::report);register("acstats","/acstats <player>","Raw anti-cheat counters for a player",this::stats);}
    private void register(String name,String usage,String description,Consumer<CommandRegistry.Context> action){match.social().commands().register(new CommandRegistry.Command(name,List.of(),true,false,usage,description,c->{action.accept(c);return CompletableFuture.completedFuture(null);}));}
    private PlayerLife find(String raw){String key=NameUnicode.strip(raw).toLowerCase(Locale.ROOT);var players=match.lives().players();var exact=players.stream().filter(p->p.name().toLowerCase(Locale.ROOT).equals(key)).findFirst().orElse(null);if(exact!=null)return exact;var prefix=players.stream().filter(p->p.name().toLowerCase(Locale.ROOT).startsWith(key)).findFirst().orElse(null);if(prefix!=null)return prefix;try{int id=Integer.parseInt(key.replaceFirst("^#+",""));return players.stream().filter(p->p.id()==id).findFirst().orElse(null);}catch(NumberFormatException invalid){return null;}}
    public static List<String> chunks(String text){var out=new ArrayList<String>();while(text.codePointCount(0,text.length())>110){int bound=text.offsetByCodePoints(0,110),cut=text.lastIndexOf(' ',bound-1);if(cut<=0)cut=bound;out.add(text.substring(0,cut));text=NameUnicode.stripLeading(text.substring(cut));}if(!text.isEmpty())out.add(text);return List.copyOf(out);}
    private void say(CommandRegistry.Context c,String text){chunks(text).forEach(line->match.social().notice(c.player(),line));}
    private static String f(String format,Object... values){return String.format(Locale.ROOT,format,values);}
    private PlayerLife target(CommandRegistry.Context c){var p=find(c.raw());if(p==null)say(c,"Player not found: "+c.raw());return p;}
    private void report(CommandRegistry.Context c){
        if(!c.args().isEmpty()){var p=target(c);if(p==null)return;var report=match.review().analyze(match,p);if(report==null){say(c,p.name()+" is a bot (never analysed).");return;}say(c,f("AC %s (#%d) score %.2f %s",p.name(),p.id(),report.score(),report.flagged()?"FLAGGED":"not flagged"));if(report.reasons().isEmpty())say(c,"  no check over threshold (or samples too small)");for(var reason:report.reasons())say(c,f("  [%.1f] %s: %s",reason.score(),reason.check(),reason.detail()));return;}
        var reports=match.review().analyzeAll(match);var suspects=reports.stream().filter(r->r.score()>0).limit(5).toList();if(suspects.isEmpty()){say(c,"AC: no suspicious players ("+reports.size()+" humans analysed).");return;}say(c,"AC top "+suspects.size()+" of "+reports.size()+" humans:");for(var report:suspects)say(c,f("%s#%d %s score %.2f: %s",report.flagged()?"!":" ",report.playerId(),report.name(),report.score(),String.join(", ",report.reasons().stream().map(r->r.check()).toList())));
    }
    private static String fmt(Map<?,?> values){return String.join(" ",values.entrySet().stream().sorted(Comparator.comparing(e->e.getKey().toString())).map(e->e.getKey()+"="+e.getValue()).toList());}
    @SuppressWarnings("unchecked") private void stats(CommandRegistry.Context c){
        if(c.args().isEmpty()){say(c,"Usage: /acstats <player>");return;}var p=target(c);if(p==null)return;var raw=match.review().raw(match,p);say(c,"AC stats "+p.name()+" (#"+p.id()+")"+(p.bot()?" [bot]":""));say(c,"K/D "+raw.get("kills")+"/"+raw.get("deaths")+" hitscan kills "+nullable(raw.get("hitscan_kills"))+" HS kills "+nullable(raw.get("headshot_kills")));say(c,"shots "+raw.get("shots")+" hits "+raw.get("hits")+" headshots "+raw.get("headshots")+" pellet hits "+raw.get("pellet_hits")+" seed shots "+raw.get("pellet_seed_shots"));
        var weapons=(Map<Integer,Map<String,Number>>)raw.get("weapons");for(var entry:weapons.entrySet()){var w=entry.getValue();long shots=w.get("shots").longValue(),hits=w.get("hits").longValue();say(c,"  tool "+entry.getKey()+": "+shots+" shots "+hits+" hits ("+(shots==0?"-":f("%.0f%%",100.*hits/shots))+") "+w.get("headshots")+" hs");}
        var aim=(Map<String,Object>)raw.get("aim");say(c,"aim: engaged "+aim.get("engaged")+" on-head "+aim.get("on_head")+" snaps "+aim.get("snaps")+" acq "+aim.get("acquisition_samples")+" median "+nullable(aim.get("acquisition_median_ms"))+" ms");for(var key:List.of("origin_error","origin_error_fallback","aim_angle","aim_angle_fallback","violations","rejected")){var values=(Map<?,?>)raw.get(key);if(!values.isEmpty())say(c,key+": "+fmt(values));}var queue=(Map<?,?>)raw.get("input_queue_delay");if(!queue.isEmpty())say(c,"input queue delay: n="+queue.get("samples")+" p50="+queue.get("p50")+" max="+queue.get("max")+" ticks");
    }
    private static String nullable(Object v){return v==null?"None":v.toString();}
}
