// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import java.util.*;
import java.util.concurrent.*;
import net.battlespades.match.*;
import net.battlespades.scene.*;
import net.battlespades.rules.GameRules;
import net.battlespades.session.*;
import net.battlespades.world.PreparedMap;

/** Actual TDM/life domain with the existing explicit test gameplay/service ports; never packaged. */
public final class HostedTdmDomain implements HostedSceneState.Domain {
    public final TdmHarness match;
    private final ConfiguredScene scene;
    private final TransitionRoster roster;
    public int closes;
    public boolean failClose;
    public HostedTdmDomain(String map,String code,PreparedMap.Bound prepared,Executor owner){
        if(!code.equals("tdm"))throw new IllegalArgumentException("Only actual TDM is bound by this fixture");
        match=new TdmHarness(new TdmMode.Settings(10,0,1,0),512,prepared.world());
        var config=new SceneConfig("Owned transition",27015,0,ModeCatalog.get(code),map,false,false,true,10,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
        scene=new ConfiguredScene(config,ScenePackets.Environment.defaults(map,prepared.world().sourceCrc()),match.mode::sceneState,match.life.runtime);roster=new TransitionRoster(match.life.runtime,match.binding,()->match.teams,owner);
    }
    public JoinScene scene(){return scene;}public TransitionMode mode(){return match.mode;}public TransitionRoster transitionRoster(){return roster;}public List<TeamState> teams(){return match.teams;}public ModeEvents events(){return match.events;}
    @Override public void close(){closes++;match.close();if(failClose)throw new IllegalStateException("Controlled domain cleanup failure");}
    /** Terminal bots/vote/repair/journal/HUD IO is deliberately controlled; state/sessions/timers use production bindings. */
    public static final class Bindings implements HostedTransitionServices.Bindings {
        public final List<String> log=new ArrayList<>();public int rebindFailures;public double grace;
        public Runnable resetVoteOperation;public java.util.function.Consumer<String> noteMapOperation;
        private static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
        public void notice(String message){log.add(message);}public void resetVote(){log.add("vote");if(resetVoteOperation!=null)resetVoteOperation.run();}public CompletionStage<Void> prepareBots(){log.add("bots-prepare");return done();}public double graceSeconds(){return grace;}
        public void resetRuntime(){log.add("runtime");}public void resetRepair(){log.add("repair");}public void resetJournal(){log.add("journal");}public void resetHud(){log.add("hud");}public void notePlayed(String map){log.add("played:"+map);if(noteMapOperation!=null)noteMapOperation.accept(map);}
        public CompletionStage<Void> rebindBots(){if(rebindFailures-->0)throw new IllegalStateException("Controlled bot rebind failure");log.add("bots-rebind");return done();}
        public CompletionStage<Void> restartRound(TransitionMode mode){throw new UnsupportedOperationException("No in-place restart bound by this fixture");}public void failure(String label,Throwable error){log.add("failure:"+label);}
    }
}
