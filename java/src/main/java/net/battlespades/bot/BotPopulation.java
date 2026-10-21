// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import java.util.function.*;
import net.battlespades.player.SourceRandom;

/** Owner population policy; identities remain leased until ordinary departure completes. */
public final class BotPopulation {
    public record Settings(String mode,int fillTarget,int maxBots,int reserveHumans,int maxPlayers){}
    public record Player(int id,int generation,int team,int classId,boolean bot,boolean alive,boolean carries,boolean visibleCombat,boolean vip,boolean solePatientZero){}
    public interface Services {
        List<Player> players();int loadingReservations();boolean ownsPopulation();boolean reconnecting();
        boolean add();boolean remove(Player player,boolean force);boolean retireSafe(Player player);int retireRank(Player player);
    }
    private final Thread owner=Thread.currentThread();private final Services services;private Settings settings;private double next;
    public BotPopulation(Settings settings,Services services){this.settings=Objects.requireNonNull(settings);this.services=Objects.requireNonNull(services);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Bot population outside owner");}
    public void settings(Settings value){owned();settings=Objects.requireNonNull(value);next=0;}
    public boolean due(double now){owned();return !services.ownsPopulation()&&!services.reconnecting()&&now>=next;}
    public void maintain(double now){owned();if(!due(now))return;next=now+1;var players=services.players();int humans=(int)players.stream().filter(p->!p.bot()).count(),playing=(int)players.stream().filter(p->!p.bot()&&(p.team()==2||p.team()==3)).count();Integer desired=desired(settings,humans,playing,services.loadingReservations());if(desired==null)return;int bots=(int)players.stream().filter(Player::bot).count();while(bots<desired){if(!services.add())break;bots++;}int excess=bots-desired;var tried=new HashSet<List<Integer>>();while(excess>0){var roster=services.players();var sizes=new HashMap<Integer,Integer>();for(var p:roster)if(p.team()==2||p.team()==3)sizes.merge(p.team(),1,Integer::sum);var candidate=roster.stream().filter(Player::bot).filter(p->!tried.contains(List.of(p.id(),p.generation()))).min(Comparator.<Player>comparingInt(p->-sizes.getOrDefault(p.team(),0)).thenComparing(Player::alive).thenComparing(Player::carries).thenComparingInt(Player::id)).orElse(null);if(candidate==null)break;tried.add(List.of(candidate.id(),candidate.generation()));if(safe(candidate)&&services.remove(candidate,false))excess--;}}
    public static Integer desired(Settings settings,int humans,int playingHumans,int loading){String mode=settings.mode().toLowerCase(Locale.ROOT);if(mode.equals("admin"))return null;int maximum=Math.max(0,settings.maxBots()),desired=mode.equals("fixed")?maximum:Math.max(0,settings.fillTarget()-playingHumans);return Math.min(maximum,Math.min(desired,Math.max(0,settings.maxPlayers()-humans-loading-Math.max(1,settings.reserveHumans()))));}
    public boolean safe(Player p){owned();if(p.carries()||p.vip()||p.solePatientZero()||p.alive()&&p.visibleCombat())return false;try{return services.retireSafe(p);}catch(RuntimeException failure){return false;}}
    private int rank(Player p){try{return services.retireRank(p);}catch(RuntimeException failure){return 0;}}
    public boolean makeRoom(){owned();var candidates=services.players().stream().filter(Player::bot).sorted(Comparator.comparing((Player p)->!safe(p)).thenComparing(Player::alive).thenComparingInt(this::rank).thenComparingInt(Player::id)).toList();for(var p:candidates)if(services.remove(p,true))return true;return false;}
    /** Caller supplies the live mode's filtered roster, including survivor/infected and Hardcore restrictions. */
    public static Integer chooseClass(String mode,boolean hardcore,List<Integer> candidates,Map<Integer,Integer> counts,List<Integer> preferred,Integer requested,SourceRandom random){if(candidates.isEmpty())return null;if(requested!=null&&candidates.contains(requested))return requested;double[] weights=new double[candidates.size()];for(int i=0;i<candidates.size();i++){int id=candidates.get(i),count=counts.getOrDefault(id,0);if(hardcore){weights[i]=1./(1+count);continue;}double base=1;if(Set.of("ctf","cctf").contains(mode))base=switch(id){case 1->1.25;case 12->1.4;case 3->1.15;case 17->1.25;default->1;};else if(mode.equals("arena"))base=switch(id){case 0->1.25;case 17->1.35;case 16->1.15;default->1;};else if(mode.equals("zom"))base=switch(id){case 12->1.5;case 3,17->1.3;default->1;};else base=switch(id){case 0->1.2;case 16,17->1.15;default->1;};if(mode.equals("tdm")&&id==1&&count>=2)base=.2;weights[i]=base/(1+count*.85)*(preferred.contains(id)?1.35:1);}return candidates.get(random.weighted(weights));}
}
