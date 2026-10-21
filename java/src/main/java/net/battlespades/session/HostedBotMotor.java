// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;
import java.util.*;
import java.util.function.Consumer;
import net.battlespades.bot.*;
import net.battlespades.bot.BotMessages.*;
import net.battlespades.bot.BotState.Cell;
import net.battlespades.player.*;
import net.battlespades.physics.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.LiveClientInput;

/** Concrete motor input/action binding to the same player, physics and gameplay owners as humans. */
public final class HostedBotMotor {
    public record Staged(BotMotor motor,Action action,double at){}
    private final Thread owner=Thread.currentThread();private final HostedBotRoster roster;private final HostedBotRoster.Member member;private final StandardMatchDomain match;private final HostedBotSnapshots snapshots;private final BotMotor motor;private int movement,actions,lastMovement=-1,lastActions=-1,lastTool=-1;private long lastLife=-1;
    public HostedBotMotor(HostedBotRoster roster,HostedBotRoster.Member member,HostedBotSnapshots snapshots,BotProfile profile,SourceRandom random,BotMotor.Budget budget,Consumer<Staged> stage){
        this.roster=Objects.requireNonNull(roster);this.member=Objects.requireNonNull(member);match=member.match();this.snapshots=Objects.requireNonNull(snapshots);var p=member.player();double yaw=Math.atan2(p.aim().y(),p.aim().x());motor=new BotMotor(p.id(),member.generation(),profile,random,yaw,new BotMotor.Services(){
            public Player player(){return HostedBotMotor.this.snapshots.liveTarget(p);}public boolean current(){return roster.current(member);}public boolean crouch(){return (p.motion().snapshot().movement()&32)!=0;}public Player target(int id){var target=match.lives().players().stream().filter(q->q.id()==id).findFirst().orElse(null);return target==null?null:HostedBotMotor.this.snapshots.liveTarget(target);}public long loop(){return match.frameLoop();}
            public void orientation(Vector v){p.orientation(v.x(),v.y(),v.z());}public void movement(int flags){movement=flags;flush();}public void actions(boolean primary,boolean secondary,boolean zoom,boolean hover){actions=(primary?1:0)|(secondary?2:0)|(zoom?4:0)|(p.alive()&&p.spawned()?16:0)|(hover?128:0);flush();}
            public boolean select(int tool){return match.botActions().select(p,tool);}public boolean execute(Action action){return match.botActions().gateway().execute(p,action);}public int stock(int tool){return p.inventory().rawDeployableStock(tool);}public double nextShot(){return p.fire().state().nextShot();}public double fireInterval(){return p.fire().profile().interval();}
            public Cell ray(Vector eye,Vector direction,double distance){return WorldQueries.ray(CollisionMap.of(match.world()),eye,direction,distance,false).map(hit->new Cell(hit.cube().x(),hit.cube().y(),hit.cube().z())).orElse(null);}public boolean drySafe(Vector at){return match.botLandingSafe(at);}public void buriedDeath(){match.binding().row(p).deathPenaltyExempt=true;match.lives().die(p.id(),null,LifeCatalog.FALL_KILL);}public void stage(BotMotor motor,Action action,double at){stage.accept(new Staged(motor,action,at));}
        },BotLiveSteering.world(match.world()),budget);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Hosted bot motor outside owner");}
    public BotMotor motor(){owned();return motor;}public HostedBotRoster.Member member(){owned();return member;}
    private void flush(){owned();if(!roster.current(member))return;var p=member.player();int tool=p.inventory().tool();if(lastMovement==movement&&lastActions==actions&&lastTool==tool&&lastLife==p.generation()&&p.motion().snapshot().movement()==movement&&p.motion().snapshot().actions()==actions)return;var aim=p.aim();var input=new LiveClientInput(match.frameLoop(),p.id(),false,tool,aim.x(),aim.y(),aim.z(),0,movement,actions,0);if(match.lives().botInput(p,input,match.frameLoop())){lastMovement=movement;lastActions=actions;lastTool=tool;lastLife=p.generation();}}
    public void observePhysics(double now){owned();motor.observePhysics(now);}
    public void tick(double now,double dt){owned();motor.tick(now,dt);}
}
