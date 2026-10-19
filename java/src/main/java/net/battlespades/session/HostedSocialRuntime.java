// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.battlespades.match.Announcements;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.transport.enet.Delivery;

/** Canonical chat lanes, separate command budgets, muted channels and the original public player commands. */
public final class HostedSocialRuntime {
    private static final class State {double chat=5,command=5,chatAt,commandAt;Double notice;boolean muted;}
    private final StandardMatchDomain match;private final ApplicationHost host;private final CommandRegistry commands;private final Map<PlayerLife,State> states=new IdentityHashMap<>();
    public HostedSocialRuntime(StandardMatchDomain match,ApplicationHost host,OwnerLoop loop,boolean log){this.match=Objects.requireNonNull(match);this.host=Objects.requireNonNull(host);commands=new CommandRegistry(match,this::notice,loop,log);players();}
    public CommandRegistry commands(){return commands;}private State state(PlayerLife p){return states.computeIfAbsent(p,key->{var s=new State();s.chatAt=s.commandAt=match.monotonic();return s;});}
    public boolean muted(PlayerLife p){return state(p).muted;}public void muted(PlayerLife p,boolean value){state(p).muted=value;}
    public void notice(PlayerLife p,String text){host.sendPlayer(p.id(),new SimplePackets.ChatMessage(255,2,Announcements.resolve(text)));}
    private static String limit(String text){int count=text.codePointCount(0,text.length());return count<=200?text:text.substring(0,text.offsetByCodePoints(0,200));}
    private boolean budget(PlayerLife p,boolean command){if(command&&match.admin(p))return true;var s=state(p);double now=match.monotonic(),tokens=Math.min(5,(command?s.command:s.chat)+Math.max(0,now-(command?s.commandAt:s.chatAt)));boolean allowed=tokens>=1;if(allowed)tokens--;if(command){s.command=tokens;s.commandAt=now;}else{s.chat=tokens;s.chatAt=now;}if(!allowed&&command){match.inputs().account(p,"command_rate_limited",true,Map.of());if(s.notice==null||now-s.notice>=5){s.notice=now;notice(p,"You're sending commands too fast; slow down.");}}return allowed;}
    public CompletionStage<Void> chat(PlayerLife p,SimplePackets.ChatMessage packet){
        String text=packet.value()==null?"":packet.value();if(text.equals("/__local_ugc_title")||text.startsWith("/__local_ugc_title "))return CompletableFuture.completedFuture(null);
        if(text.startsWith("/"))return budget(p,true)?commands.execute(p,text.substring(1)):CompletableFuture.completedFuture(null);
        text=limit(text);if(muted(p)||NameUnicode.strip(text).isEmpty()||!budget(p,false))return CompletableFuture.completedFuture(null);match.conduct().activity(p);relay(p,text,packet.chat_type()==1);return CompletableFuture.completedFuture(null);
    }
    public void relay(PlayerLife p,String text,boolean team){if(match.stopping())return;var packet=new SimplePackets.ChatMessage(p.id(),team?1:0,text);for(var session:host.transitionSessions()){if(!session.inGame()||session.playerId().isEmpty())continue;int id=session.playerId().orElseThrow();var recipient=match.lives().players().stream().filter(v->v.id()==id).findFirst().orElse(null);if(recipient==null||team&&recipient.team()!=p.team())continue;if(recipient!=p&&!session.knownPlayers().contains(p.id()))continue;session.gameplay(packet,Delivery.RELIABLE);}}
    private PlayerLife find(String name){String key=name.toLowerCase(Locale.ROOT);return match.lives().players().stream().filter(p->p.name().toLowerCase(Locale.ROOT).startsWith(key)).findFirst().orElse(null);}
    private void register(String name,List<String> aliases,String usage,String description,Consumer<CommandRegistry.Context> action){commands.register(new CommandRegistry.Command(name,aliases,false,false,usage,description,ctx->{action.accept(ctx);return CompletableFuture.completedFuture(null);}));}
    private void players(){
        register("help",List.of("?","commands"),"/help [command]","Show available commands",ctx->{if(!ctx.args().isEmpty()){var command=commands.get(ctx.args().getFirst());if(command==null){notice(ctx.player(),"Unknown command: "+ctx.args().getFirst());return;}notice(ctx.player(),"/"+command.name()+": "+command.description());if(!command.usage().isEmpty())notice(ctx.player(),"Usage: "+command.usage());if(!command.aliases().isEmpty())notice(ctx.player(),"Aliases: "+String.join(", ",command.aliases()));return;}var visible=commands.all().stream().filter(c->!c.hidden()).toList();notice(ctx.player(),"Commands:");notice(ctx.player(),String.join(", ",visible.stream().filter(c->!c.admin()).map(c->"/"+c.name()).toList()));if(match.admin(ctx.player())&&visible.stream().anyMatch(CommandRegistry.Command::admin)){notice(ctx.player(),"Admin commands:");notice(ctx.player(),String.join(", ",visible.stream().filter(CommandRegistry.Command::admin).map(c->"/"+c.name()).toList()));}});
        register("kill",List.of("suicide"),"/kill","Kill yourself",ctx->{if(!ctx.player().alive())notice(ctx.player(),"You're already dead!");else match.lives().endLifeForTransition(ctx.player().id(),10);});
        register("team",List.of(),"/team <team1|team2|spectator>","Change your team",ctx->{if(ctx.args().isEmpty()){notice(ctx.player(),"Usage: /team <team1|team2|spectator>");return;}Integer team=switch(ctx.args().getFirst().toLowerCase(Locale.ROOT)){case "team1","2"->2;case "team2","3"->3;case "spectator","spec","0"->0;default->null;};if(team==null){notice(ctx.player(),"Invalid team. Use: team1, team2, or spectator");return;}if(match.controls().teams().change(ctx.player(),team,true,false))notice(ctx.player(),"You joined "+(team==0?"the spectators":"team "+(team==2?1:2)));});
        register("score",List.of("scores"),"/score","Show current scores",ctx->{for(var team:match.teams())notice(ctx.player(),team.name+": "+team.score+" points ("+team.playerCount()+" players)");});
        register("players",List.of("who","list"),"/players","List all players",ctx->{notice(ctx.player(),"Players ("+match.lives().players().size()+"/"+host.playerLimit()+"):");for(var team:match.teams()){var names=team.players().stream().map(PlayerLife::name).toList();if(!names.isEmpty())notice(ctx.player(),team.name+": "+String.join(", ",names));}});
        register("pm",List.of("msg","whisper","w"),"/pm <player> <message>","Send a private message",ctx->{if(ctx.args().size()<2){notice(ctx.player(),"Usage: /pm <player> <message>");return;}if(muted(ctx.player())){notice(ctx.player(),"You are muted.");return;}var target=find(ctx.args().getFirst());if(target==null){notice(ctx.player(),"Player not found: "+ctx.args().getFirst());return;}String message=limit(String.join(" ",ctx.args().subList(1,ctx.args().size())));notice(target,"[PM from "+ctx.player().name()+"]: "+message);notice(ctx.player(),"[PM to "+target.name()+"]: "+message);});
        register("me",List.of(),"/me <action>","Describe an action",ctx->{String text=NameUnicode.strip(ctx.raw());if(!text.isEmpty()&&!muted(ctx.player()))relay(ctx.player(),limit("* "+text),false);});
        register("ping",List.of(),"/ping","Show your ping",ctx->{var rtt=ctx.player().flight().roundTripMillis();notice(ctx.player(),rtt.isEmpty()?"Ping unavailable (no active connection).":"Your ping: "+(int)rtt.orElseThrow()+" ms");});
        register("stats",List.of(),"/stats [player]","Show player stats",ctx->{var target=ctx.args().isEmpty()?ctx.player():find(ctx.args().getFirst());if(target==null){notice(ctx.player(),"Player not found: "+ctx.args().getFirst());return;}var score=match.lives().score(target.id());notice(ctx.player(),String.format(Locale.ROOT,"%s - Kills: %d, Deaths: %d, K/D: %.2f",target.name(),score.kills(),score.deaths(),score.kills()/(double)Math.max(1,score.deaths())));});
    }
    public void forget(PlayerLife p){states.remove(p);}public void clear(){states.clear();}
}
