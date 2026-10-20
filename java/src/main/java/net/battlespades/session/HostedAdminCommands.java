// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.admin.BanStore;
import net.battlespades.match.Announcements;
import net.battlespades.player.PlayerLife;
import net.battlespades.transport.enet.Delivery;

/** Source administration commands on the canonical command queue and real host connections. */
final class HostedAdminCommands {
    private final StandardMatchDomain match;private final ApplicationHost host;private final HostedAdministration admin;private final CommandRegistry commands;
    HostedAdminCommands(StandardMatchDomain match,ApplicationHost host,HostedAdministration admin){this.match=match;this.host=host;this.admin=admin;this.commands=match.social().commands();
        register("admin",List.of("login"),false,false,"/admin <password>","Login as admin",c->admin.credential(c.player().id(),c.player().name(),c.raw(),false));
        register("claimhost",List.of(),false,true,"/claimhost <token>","Room creator's one-time admin claim",c->admin.credential(c.player().id(),c.player().name(),c.raw(),true));
        sync("roompassword",List.of("roompass"),"/roompassword","Show this player-hosted room's admin password",c->admin.roomPassword(c.player().id()));
        sync("kick",List.of("k"),"/kick <player> [reason]","Kick a player from the server",this::kick);
        register("ban",List.of("b"),true,false,"/ban <player> [duration] [reason]","Ban a player from the server",this::ban);
        sync("mute",List.of(),"/mute <player>","Mute a player",c->mute(c,true));sync("unmute",List.of(),"/unmute <player>","Unmute a player",c->mute(c,false));
        sync("god",List.of(),"/god [player]","Toggle god mode",this::god);
    }
    private void register(String name,List<String> aliases,boolean restricted,boolean hidden,String usage,String description,Function<CommandRegistry.Context,CompletionStage<Void>> handler){commands.register(new CommandRegistry.Command(name,aliases,restricted,hidden,usage,description,handler));}
    private void sync(String name,List<String> aliases,String usage,String description,Consumer<CommandRegistry.Context> handler){register(name,aliases,true,false,usage,description,c->{handler.accept(c);return done();});}
    private static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
    private void notice(PlayerLife p,String text){match.social().notice(p,text);}
    private PlayerLife target(CommandRegistry.Context c,String usage){if(c.args().isEmpty()){notice(c.player(),"Usage: "+usage);return null;}String key=c.args().getFirst().toLowerCase(Locale.ROOT);var target=match.lives().players().stream().filter(p->p.name().toLowerCase(Locale.ROOT).startsWith(key)).findFirst().orElse(null);if(target==null)notice(c.player(),"Player not found: "+c.args().getFirst());return target;}
    private static String reason(List<String> args,int start,String fallback){return args.size()>start?String.join(" ",args.subList(start,args.size())):fallback;}
    private void overlay(String text){host.broadcast(Announcements.overlay(text),Delivery.RELIABLE);}
    private void kick(CommandRegistry.Context c){var target=target(c,"/kick <player> [reason]");if(target==null)return;String message=target.name()+" was kicked: "+reason(c.args(),1,"Kicked by admin");Announcements.overlay(message);overlay(message);host.disconnectPlayer(target.id(),2);}
    private CompletionStage<Void> ban(CommandRegistry.Context c){var target=target(c,"/ban <player> [duration] [reason]");if(target==null)return done();int start=1;long duration=0;if(c.args().size()>1){long parsed=BanStore.parseDuration(c.args().get(1));if(parsed>=0){duration=parsed;start=2;}}String reason=reason(c.args(),start,"Banned by admin"),when=duration<=0?"permanently":"for "+c.args().get(1),message=target.name()+" was banned "+when+": "+reason;Announcements.overlay(message);return admin.ban(target.id(),target.name(),reason,duration,()->overlay(message));}
    private void mute(CommandRegistry.Context c,boolean muted){var target=target(c,muted?"/mute <player>":"/unmute <player>");if(target==null)return;match.social().muted(target,muted);notice(c.player(),(muted?"Muted ":"Unmuted ")+target.name());notice(target,muted?"You have been muted by an admin.":"You have been unmuted.");}
    private void god(CommandRegistry.Context c){var target=c.args().isEmpty()?c.player():target(c,"/god [player]");if(target==null)return;target.god(!target.god());String state=target.god()?"ON":"OFF";notice(c.player(),"God mode "+state+" for "+target.name());if(target!=c.player())notice(target,"An admin set your god mode "+state+".");}
}
