// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.battlespades.match.TeamState;
import net.battlespades.protocol.*;
import net.battlespades.transport.enet.Delivery;

/** Scene-owned runtime HUD rules. Explicit false values also survive late admission. */
public final class HostedTeamRules {
    private final StandardMatchDomain match;private final ApplicationHost host;
    private final Map<Integer,Boolean> locks=new LinkedHashMap<>(),wallets=new LinkedHashMap<>();
    public HostedTeamRules(StandardMatchDomain match,ApplicationHost host){this.match=Objects.requireNonNull(match);this.host=Objects.requireNonNull(host);
        register("lockscore",List.of(),"Score lock","Freeze a team's score (TeamLockScore)",true);
        register("infiniteblocks",List.of("infblocks"),"Infinite blocks","Give a team an unlimited block wallet (TeamInfiniteBlocks)",false);
    }
    private TeamState team(int id){return match.teams().stream().filter(t->t.id==id).findFirst().orElse(null);}
    public boolean infinite(int id){var t=team(id);return t!=null&&t.infiniteBlocks;}
    public boolean locked(int id){var t=team(id);return t!=null&&t.lockedScore;}
    public boolean set(int id,boolean enabled,boolean lock){var t=team(id);if(t==null)return false;
        if(lock)t.lockedScore=enabled;else t.infiniteBlocks=enabled;
        (lock?locks:wallets).put(id,enabled);for(var session:host.transitionSessions())if(session.inGame())session.gameplay(packet(id,enabled,lock),Delivery.RELIABLE);
        if(lock&&!enabled)match.scoreboard().teamScore(t,0);return true;
    }
    private static GamePacket packet(int id,boolean enabled,boolean lock){return lock?new SimplePackets.TeamLockScore(id,enabled?1:0):new SimplePackets.TeamInfiniteBlocks(id,enabled?1:0);}
    public List<GamePacket> reveal(){var packets=new ArrayList<GamePacket>();locks.forEach((id,value)->packets.add(packet(id,value,true)));wallets.forEach((id,value)->packets.add(packet(id,value,false)));return List.copyOf(packets);}
    private StateData.Team state(StateData.Team t,int id){return new StateData.Team(t.name(),t.color(),t.score(),t.locked(),t.canSeeOther(),t.showScore(),t.showMaxScore(),infinite(id),t.lockedClass(),locked(id),t.classes());}
    public StateData state(StateData p){return new StateData(p.playerId(),p.fogColor(),p.gravity(),p.lighting(),p.timeScale(),p.scoreLimit(),p.modeType(),p.teamHeadcountType(),state(p.team1(),2),state(p.team2(),3),p.lockTeamSwap(),p.lockSpectatorSwap(),p.prefabs(),p.entities(),p.cameraPoints(),p.cameraRotations(),p.hasMapEnded());}
    private List<Integer> parse(String value){String token=value.strip().toLowerCase(Locale.ROOT);return switch(token){case "all","both","*"->List.of(2,3);case "1","team1"->List.of(2);case "2","team2"->List.of(3);default->match.teams().stream().filter(t->t.name.toLowerCase(Locale.ROOT).equals(token)).map(t->t.id).findFirst().map(List::of).orElse(null);};}
    private void register(String name,List<String> aliases,String label,String description,boolean lock){String usage="/"+name+" <1|2|all> [on|off]";
        match.social().commands().register(new CommandRegistry.Command(name,aliases,true,false,usage,description,c->{
            if(c.args().isEmpty()){notice(c,"Usage: "+usage);return CompletableFuture.completedFuture(null);}var ids=parse(c.args().getFirst());
            if(ids==null){notice(c,"Unknown team '"+c.args().getFirst()+"'. "+usage);return CompletableFuture.completedFuture(null);}
            if(c.args().size()==1){notice(c,label+" - "+String.join(", ",ids.stream().map(id->team(id).name+": "+((lock?locked(id):infinite(id))?"on":"off")).toList()));return CompletableFuture.completedFuture(null);}
            Boolean enabled=switch(c.args().get(1).toLowerCase(Locale.ROOT)){case "on","1","true","yes","enable","enabled"->true;case "off","0","false","no","disable","disabled"->false;default->null;};
            if(enabled==null){notice(c,"Usage: "+usage);return CompletableFuture.completedFuture(null);}
            var changed=ids.stream().filter(id->set(id,enabled,lock)).map(id->team(id).name).toList();notice(c,label+(changed.isEmpty()?" unchanged":" "+(enabled?"on":"off")+" for "+String.join(", ",changed)));return CompletableFuture.completedFuture(null);
        }));
    }
    private void notice(CommandRegistry.Context c,String text){match.social().notice(c.player(),text);}
}
