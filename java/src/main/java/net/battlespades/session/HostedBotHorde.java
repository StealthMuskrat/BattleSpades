// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.nio.file.*;
import net.battlespades.bot.*;
import net.battlespades.bot.BotMessages.Objective;
import net.battlespades.bot.BotState.Cell;
import net.battlespades.player.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.world.MutableWorld;

/** Gameplay-thread Zombie glue: sanctioned target set, budgeted siege work and resumable refuge election. */
public final class HostedBotHorde implements AutoCloseable {
    private record Refuge(Vector position,double elected,int generation){}
    private record Failed(Vector position,double at){}
    private record Job(String reason,ZombieRefuge.Election election,Refuge previous){}
    private final Thread owner=Thread.currentThread();private final StandardMatchDomain match;private final LongSupplier mapEpoch,modeEpoch;private final Function<PlayerLife,BotMotor> motors;private final DoubleSupplier workClock;private final ZombieSiegeService siege;private final ZombieSiegeService.World world;
    private final CompletableFuture<IntBinaryOperator> regions=new CompletableFuture<>();private final List<Failed> history=new ArrayList<>();private Refuge refuge;private Job job;private boolean closed;
    public HostedBotHorde(StandardMatchDomain match,LongSupplier mapEpoch,LongSupplier modeEpoch,Function<PlayerLife,BotMotor> motors,DoubleSupplier workClock,Path directory,String mapName,byte[] raw){this.match=Objects.requireNonNull(match);this.mapEpoch=Objects.requireNonNull(mapEpoch);this.modeEpoch=Objects.requireNonNull(modeEpoch);this.motors=Objects.requireNonNull(motors);this.workClock=Objects.requireNonNull(workClock);siege=new ZombieSiegeService(.001,match::monotonic,workClock,match::failure);var terrain=match.world();world=new ZombieSiegeService.World(){public boolean solid(int x,int y,int z){return terrain.solid(x,y,z);}public double health(Cell cell){return terrain.initialHealth(new MutableWorld.Cell(cell.x(),cell.y(),cell.z()));}public AutoCloseable subscribe(Consumer<Cell> listener){var lease=terrain.observeCommits(change->listener.accept(new Cell(change.cell().x(),change.cell().y(),change.cell().z())),error->match.failure("horde terrain listener",error));return lease::close;}};
        if(directory==null||mapName.isEmpty()||raw.length==0)regions.complete(null);else{byte[] retained=raw.clone();String stem=Path.of(mapName).getFileName().toString();if(stem.toLowerCase(Locale.ROOT).endsWith(".vxl"))stem=stem.substring(0,stem.length()-4);Path path=directory.resolve(stem+".botnav");try{Thread.ofPlatform().daemon().name("bot-refuge-regions").start(()->{try(var in=Files.newInputStream(path)){var atlas=NavigationAtlas.decode(in.readNBytes(32*1024*1024+1),NavigationAtlas.sourceDigest(retained));regions.complete((x,y)->x>=0&&x<atlas.width()&&y>=0&&y<atlas.height()?atlas.region(y*atlas.width()+x):0);}catch(Exception unavailable){regions.complete(null);}});}catch(RuntimeException unavailable){regions.complete(null);}}
    }
    private void owned(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("Bot horde outside active owner");}
    private List<PlayerLife> living(int team){return match.lives().players().stream().filter(p->p.team()==team&&p.alive()&&p.spawned()).toList();}
    public List<Objective> orders(){owned();if(match.zombie().isEmpty())return List.of();double now=match.monotonic();var survivors=living(3).stream().map(p->new HordeCoordinator.Target(p.id(),p.position())).toList();var zombies=living(2).stream().map(p->{var motor=p.bot()?motors.apply(p):null;var f=motor==null?null:motor.state().feedback();boolean working=f!=null&&f.kind().equals("melee")&&f.accepted()&&now-f.at()<=1.5;return new HordeCoordinator.Member(p.id(),p.position(),p.bot(),p.selection().loadout().stream().anyMatch(net.battlespades.combat.CombatCatalog.SPADES::contains),p.selection().loadout().contains(5)||p.selection().loadout().contains(28),working);}).toList();return siege.objectives(mapEpoch.getAsLong(),modeEpoch.getAsLong(),match.zombie().orElseThrow().active(),2,zombies,survivors,world,now);}
    public List<Objective> refuge(){owned();if(match.zombie().isEmpty())return List.of();try{var phase=match.zombie().orElseThrow().phase();if(phase==HostedZombieRuntime.Phase.WAITING){refuge=null;history.clear();job=null;}if(phase!=HostedZombieRuntime.Phase.COUNTDOWN&&phase!=HostedZombieRuntime.Phase.ACTIVE)return List.of();double now=match.monotonic();var survivors=living(3).stream().map(PlayerLife::position).toList();if(survivors.isEmpty()){refuge=null;job=null;return List.of();}var zombies=living(2).stream().map(PlayerLife::position).toList();history.removeIf(item->now-item.at()>=90);String reason=refuge==null?"elect":ZombieRefuge.breached(refuge.position(),zombies,6,3)?"breached":now-refuge.elected()>45&&survivors.stream().noneMatch(p->flat(p,refuge.position())<=12)?"unreached":"";
            if(!reason.isEmpty()&&job==null){var excluded=new ArrayList<>(history.stream().map(Failed::position).toList());if(refuge!=null&&!reason.equals("elect")){excluded.add(refuge.position());history.add(new Failed(refuge.position(),now));}job=new Job(reason,new ZombieRefuge.Election(match.world()::surfaceZ,survivors,zombies,excluded,regions.getNow(null),56,4,1200),refuge);}
            if(job!=null){double deadline=workClock.getAsDouble()+.002;boolean done;do{done=job.election().advance(1);}while(!done&&workClock.getAsDouble()<deadline);if(done){var complete=job;job=null;var chosen=complete.election().result();if(chosen!=null)refuge=new Refuge(chosen,now,complete.previous()==null?1:complete.previous().generation()+1);else if(!complete.reason().equals("elect"))refuge=null;}}
            return refuge==null?List.of():List.of(HostedBotObjectives.simple("zombie_refuge",3,refuge.position(),-1,refuge.generation()));
        }catch(RuntimeException failure){refuge=null;job=null;return List.of();}}
    private static double flat(Vector a,Vector b){return Math.hypot(a.x()-b.x(),a.y()-b.y());}
    public void reset(){owned();siege.reset();refuge=null;history.clear();job=null;}
    public ZombieSiegeService siege(){owned();return siege;}
    @Override public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Bot horde outside owner");if(closed)return;siege.close();refuge=null;job=null;history.clear();closed=true;}
}
