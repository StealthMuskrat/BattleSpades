// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.match.Announcements;
import net.battlespades.match.RoundEndSequence;
import net.battlespades.player.NameUnicode;
import net.battlespades.protocol.SimplePackets;
import net.battlespades.protocol.UgcPackets;
import net.battlespades.transport.enet.Delivery;

/** Server controls on the real command, mode, transition, scene and safe-balancer owners. */
final class HostedServerCommands {
    private final StandardMatchDomain match;private final ApplicationHost host;private final CommandRegistry commands;
    HostedServerCommands(StandardMatchDomain match,ApplicationHost host){
        this.match=match;this.host=host;commands=match.social().commands();
        sync("map",List.of("changemap"),"/map <mapname>","Change the current map",this::map);
        sync("mode",List.of("gamemode"),"/mode <tdm|ctf|cctf|zom|vip|mh|tc|dia|dem|oc|arena>","Change the game mode",this::mode);
        sync("endround",List.of("endgame","forceend"),"/endround [team]","Force the current round to end",this::end);
        sync("say",List.of(),"/say <message>","Send a server announcement",this::say);
        sync("fog",List.of(),"/fog <r> <g> <b>","Set fog color",this::fog);
        sync("time",List.of(),"/time [seconds]","Show or set remaining time",this::time);
        commands.register(new CommandRegistry.Command("balance",List.of(),true,false,"/balance","Force team balance",this::balance));
    }
    private static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
    private void sync(String name,List<String> aliases,String usage,String description,Consumer<CommandRegistry.Context> action){commands.register(new CommandRegistry.Command(name,aliases,true,false,usage,description,c->{action.accept(c);return done();}));}
    private void notice(CommandRegistry.Context c,String text){match.social().notice(c.player(),text);}
    private boolean isolated(CommandRegistry.Context c){String code=match.modeCode();if(code.equals("ugc")){notice(c,"Not available in the Map Creator session.");return true;}if(code.equals("tut")||code.equals("tutorial")){notice(c,"Not available in the Tutorial.");return true;}return false;}
    private void transitionReply(CommandRegistry.Context c,RoundEndSequence.Result result){if(!result.ok()||!result.reconnectRequired())notice(c,result.message());}
    private void map(CommandRegistry.Context c){
        if(isolated(c))return;if(c.args().isEmpty()){notice(c,"Current map: "+match.mapName());notice(c,"Usage: /map <mapname>");return;}
        var transitions=host.matchTransitions();if(transitions.isEmpty()){notice(c,"Match transitions are unavailable");return;}
        transitionReply(c,transitions.get().requestMap(NameUnicode.strip(c.raw()),text->notice(c,text)));
    }
    private void mode(CommandRegistry.Context c){
        if(isolated(c))return;if(c.args().isEmpty()){notice(c,"Current mode: "+match.modeCode().toUpperCase(Locale.ROOT));notice(c,"Usage: /mode <tdm|ctf|cctf|zom|vip|mh|tc|dia|dem|oc|arena>");return;}
        var transitions=host.matchTransitions();if(transitions.isEmpty()){notice(c,"Match transitions are unavailable");return;}
        transitionReply(c,transitions.get().requestMode(c.args().getFirst(),text->notice(c,text)));
    }
    private void end(CommandRegistry.Context c){
        if(isolated(c))return;var mode=match.mode();if(mode.clock().ended){notice(c,"No active round to end.");return;}int team=0;
        if(!c.args().isEmpty())try{team=Integer.parseInt(c.args().getFirst());}catch(NumberFormatException ignored){}
        if(team==1||team==2)mode.endByScore(team+1);else mode.endByTime();
    }
    private void say(CommandRegistry.Context c){
        String text=NameUnicode.strip(c.raw());if(text.isEmpty()){notice(c,"Usage: /say <message>");return;}
        if(text.codePointCount(0,text.length())>256){notice(c,"Announcement is too long (max 256 characters)");return;}
        // The wire builder also bounds the resolved [SERVER] prefix, which the original command's precheck misses.
        SimplePackets.ChatMessage packet;try{packet=Announcements.overlay("[SERVER] "+text);}catch(IllegalArgumentException tooLong){notice(c,"Announcement is too long (max 256 characters including prefix)");return;}
        host.broadcast(packet,Delivery.RELIABLE);
    }
    private void fog(CommandRegistry.Context c){
        if(c.args().size()!=3){notice(c,"Usage: /fog <r> <g> <b>");return;}int r,g,b;
        try{r=Integer.parseInt(c.args().get(0));g=Integer.parseInt(c.args().get(1));b=Integer.parseInt(c.args().get(2));}catch(NumberFormatException invalid){notice(c,"Invalid RGB values");return;}
        if(r<0||r>255||g<0||g>255||b<0||b>255){notice(c,"RGB values must be in range 0-255");return;}
        int color=r<<16|g<<8|b;match.fogColor(color);host.broadcast(new UgcPackets.Fog(color),Delivery.RELIABLE);notice(c,"Fog color set to ("+r+", "+g+", "+b+")");
    }
    private void time(CommandRegistry.Context c){
        var clock=match.mode().clock();if(c.args().isEmpty()){long remaining=(long)Math.max(0,clock.timeLimit-clock.elapsed);notice(c,"Time remaining: "+remaining/60+":"+String.format(Locale.ROOT,"%02d",remaining%60));return;}
        long seconds;try{seconds=Long.parseLong(c.args().getFirst());if(seconds<0)throw new NumberFormatException();}catch(NumberFormatException invalid){notice(c,"Invalid time value");return;}
        clock.timeLimit=seconds;clock.startTime=match.wall();clock.elapsed=0;notice(c,"Time remaining set to "+seconds+" seconds");
    }
    private CompletionStage<Void> balance(CommandRegistry.Context c){
        if(match.playerRuntime().isEmpty()){notice(c,"Team balancing is unavailable");return done();}
        var balancer=match.runtime().gameplay().balance();var counts=balancer.counts();if(Math.abs(counts.get(2)-counts.get(3))<=1){notice(c,"Teams are already balanced");return done();}
        return balancer.force().thenAccept(moved->{var after=balancer.counts();int remaining=Math.abs(after.get(2)-after.get(3));notice(c,"Teams balanced: "+moved+" player(s) moved"+(remaining<=1?"":"; still "+remaining+" apart (live, carrying or mode-locked players are never moved)"));});
    }
}
