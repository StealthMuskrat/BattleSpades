// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.bot.BotState.Observer;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;
import static net.battlespades.bot.BotCombatTactics.*;

/** Per-life reactions licensed by visible terrain, heard events and the victim's damage indicator. */
public final class BotAwareness {
    public record Reaction(String role,Vector heading,Vector goal,Vector look,boolean crouch,boolean sprint,Priority priority,Vector suspect) {}
    private record Hazard(int key,Vector centre,double reach,double radius,double until,String kind) {}
    private record Shot(Vector position,double at) {}
    private record Health(double at,int value) {}
    private record Odds(double distance,Vector eye) {}
    private static final Set<Integer> ZOMBIES,UNDIRECTED;
    private static final double FIRE_REACH,FIRE_SPREAD,FIRE_SECONDS,MOLOTOV_SPEED,HEARING_DISTANCE;
    private static final int MOLOTOV_TOOL;
    static {
        var c=SourceCatalog.load("bot-combat");ZOMBIES=Set.copyOf(SourceCatalog.integers(c.getArray("zombie_classes")));
        UNDIRECTED=Set.copyOf(SourceCatalog.integers(c.getArray("undirected_damage")));
        FIRE_REACH=c.getDouble("fire_reach");FIRE_SPREAD=c.getDouble("fire_spread");FIRE_SECONDS=c.getDouble("fire_seconds");MOLOTOV_SPEED=c.getDouble("molotov_speed");MOLOTOV_TOOL=Math.toIntExact(c.getLong("molotov_tool"));HEARING_DISTANCE=c.getDouble("hearing_distance");
    }
    private static final class Shelter {
        Vector spot;boolean reached;double best=Double.POSITIVE_INFINITY,progressAt,nextSearchAt;
        void clear() {spot=null;reached=false;}
    }
    private static final class Life {
        final CombatFootwork.Identity identity;double seenAt,hitAt,fireNoticedAt,fireUntil,dashUntil,lookBackUntil,sightedAt;
        int hits,shelterHits,dashes,sightedId=-1;Vector fireFrom,dash;String fireCause="under_fire";Shelter shelter=new Shelter();
        double oddsAt,foesAt,nearestFoe=Double.POSITIVE_INFINITY,outmatchedSince,outmatchedAt,retreatUntil,retreatWait,retreatHitAt,retreatRestUntil,deadGroundAt;
        List<Vector> foes=List.of();List<Health> healthLog=List.of();String retreat="";Shelter refuge=new Shelter();Vector rally,deadGround;
        Vector glance,noise,checked;double glanceFrom,glanceUntil,watchUntil,glanceReadyAt,heardAt,alert,alertAt,noiseAt,checkedAt,checkedRange;
        final LinkedHashMap<Integer,Shot> shots=new LinkedHashMap<>();List<Hazard> hazards=List.of();double hazardsAt;Player body;
        final LinkedHashMap<Integer,Double> seenFires=new LinkedHashMap<>();Hazard pyre;final Map<Integer,Double> steerSide=new HashMap<>();
        Life(Player p) {identity=new CombatFootwork.Identity(p.id(),p.generation(),p.lifeId());hitAt=p.lastDamageAt();}
    }
    private final Thread owner=Thread.currentThread();private final BotNavigationWorld world;
    private final LinkedHashMap<Observer,Life> lives=new LinkedHashMap<>();private List<Entity> fireEntities=List.of();private List<Hazard> fires=List.of();
    public BotAwareness(BotNavigationWorld world) {this.world=Objects.requireNonNull(world);}
    private void owned() {if(Thread.currentThread()!=owner)throw new IllegalStateException("Bot awareness outside worker");}
    public void reset() {owned();lives.clear();fireEntities=List.of();fires=List.of();}
    public void forget(int id,int generation) {owned();lives.remove(new Observer(id,generation));}
    public int lifeCount() {owned();return lives.size();}
    private Life life(Player p,double now) {
        var key=new Observer(p.id(),p.generation());var life=lives.get(key);var identity=new CombatFootwork.Identity(p.id(),p.generation(),p.lifeId());
        if(life==null||!life.identity.equals(identity)) {
            if(life==null&&lives.size()>=128) {
                var oldest=lives.entrySet().stream().min(Comparator.comparingDouble(e->e.getValue().seenAt)).orElseThrow().getKey();lives.remove(oldest);
            }
            life=new Life(p);lives.put(key,life);
        }
        life.seenAt=now;return life;
    }
    public static double noticeDelay(BotProfile p) {return p.reactionTime()*(1+2*(1-p.skill()));}
    private static boolean flightRole(Player p,BotModeDecision d) {
        return p.carriedEntityId()>=0||!p.canShoot()||d!=null&&d.objectivePriority()>=.9&&(d.posture()==BotModeDecision.Posture.EVASIVE||d.posture()==BotModeDecision.Posture.SURVIVE);
    }
    private static boolean holdsGround(Player p,BotModeDecision d) {
        if(flightRole(p,d))return true;if(d==null||d.objectivePriority()<.9)return false;
        return d.posture()==BotModeDecision.Posture.ESCORT||distance(p.position(),d.position())<=Math.max(12,2*d.arrivalRadius());
    }
    private static Reaction reaction(String role,Vector heading,Vector goal,Vector look,boolean crouch,boolean sprint) {return new Reaction(role,heading,goal,look,crouch,sprint,Priority.COMBAT,null);}
    public Reaction react(Perception frame,Player p,BotProfile profile,Player visible,BotModeDecision decision,double now) {
        owned();var life=life(p,now);watchHazards(frame,p,profile,life,now);var burning=leaveFire(p,life,now);if(burning!=null)return burning;
        if(ZOMBIES.contains(p.classId()))return null;
        listen(frame,p,profile,life,visible,decision,now);
        var hunted=underFire(frame,p,profile,life,visible,decision,now);var retreat=outmatched(frame,p,profile,life,visible,decision,now);
        if(retreat!=null) {life.fireFrom=null;return retreat;}return hunted;
    }
    public Intent overlay(Perception frame,Intent intent) {
        owned();var life=lives.get(new Observer(intent.botId(),intent.botGeneration()));if(life==null)return intent;double now=frame.createdAt();
        if(!life.hazards.isEmpty()&&now-life.hazardsAt<=.5)intent=steer(life,intent,now);
        if(life.glance==null)return intent;
        if(now>=Math.max(life.glanceUntil,life.watchUntil)) {life.glance=null;return intent;}
        if(now<life.glanceFrom||!headFree(intent))return intent;
        if(now>=life.glanceUntil&&Math.hypot(intent.movement().direction().x(),intent.movement().direction().y())>.1)return intent;
        return amend(intent,intent.movement(),new Look(life.glance,false,-1,0,0,true),intent.debugRole());
    }
    private static Intent amend(Intent i,Movement m,Look look,String role) {
        return new Intent(i.botId(),i.botGeneration(),i.frameId(),i.mapEpoch(),i.modeEpoch(),i.topologyVersion(),i.createdAt(),i.expiresAt(),m,look,i.toolId(),i.action(),i.priority(),i.secondaryFire(),i.zoom(),i.debugGoal(),i.debugPath(),role,i.lifeId(),i.debugNavigation());
    }
    private static Movement movement(Movement m,Vector direction,boolean sprint) {
        return new Movement(direction,m.jump(),m.crouch(),m.sneak(),sprint,m.affordance(),m.jetpackThrust(),m.travelSource(),m.travelWaypoint(),m.gazeWaypoint(),m.walkDrop());
    }
    public void threw(Player p,int tool,Vector target,double now) {
        owned();if(tool!=MOLOTOV_TOOL)return;var life=life(p,now);double flight=distance(p.position(),target)/MOLOTOV_SPEED,reach=FIRE_SPREAD+FIRE_REACH;
        life.pyre=new Hazard(-1,target,reach,reach+1,now+flight+1+FIRE_SECONDS+1,"fire");
    }
    private static final class Patch {
        Vector centre;final List<Entity> members=new ArrayList<>();Patch(Entity e) {centre=e.position();members.add(e);}
        void add(Entity e) {members.add(e);int n=members.size();centre=new Vector((centre.x()*(n-1)+e.position().x())/n,(centre.y()*(n-1)+e.position().y())/n,(centre.z()*(n-1)+e.position().z())/n);}
    }
    private List<Hazard> burningPatches(Perception frame) {
        if(frame.entities()==fireEntities)return fires;var patches=new ArrayList<Patch>();
        for(var entity:frame.entities()) {
            if(!entity.kind().equals("blockfire")||!entity.alive())continue;
            Patch found=null;for(var patch:patches)if(distance(patch.centre,entity.position())<=4.5) {found=patch;break;}
            if(found==null)patches.add(new Patch(entity));else found.add(entity);
        }
        var result=new ArrayList<Hazard>();
        for(var patch:patches) {
            double extent=0,reach=0,until=Double.NEGATIVE_INFINITY;int key=Integer.MAX_VALUE;
            for(var e:patch.members) {extent=Math.max(extent,flat(patch.centre,e.position()));reach=Math.max(reach,e.blastRadius());until=Math.max(until,e.detonateAt());key=Math.min(key,e.id());}
            result.add(new Hazard(key,patch.centre,extent+reach,extent+reach,until+.3,"fire"));
        }
        fireEntities=frame.entities();fires=List.copyOf(result);return fires;
    }
    private void watchHazards(Perception frame,Player p,BotProfile profile,Life life,double now) {
        life.body=p;life.hazardsAt=now;var known=new ArrayList<Hazard>();double margin=.4+.8*profile.skill();int rays=0;
        for(var fire:frame.entities().isEmpty()?List.<Hazard>of():burningPatches(frame)) {
            double gap=flat(fire.centre,p.position()),head=p.position().z(),height=fire.centre.z()-Math.min(Math.max(fire.centre.z(),head),head+2.25);
            if(gap>fire.radius+16||now>=fire.until||Math.abs(height)>FIRE_REACH)continue;
            Double seen=life.seenFires.get(fire.key);
            if(seen==null) {
                if(gap>fire.radius+1) {
                    if(rays>=4)continue;rays+=2;var at=fire.centre;
                    if(!world.lineOfSight(p.eye(),new Vector(at.x(),at.y(),at.z()-1))&&!world.lineOfSight(p.eye(),new Vector(at.x(),at.y(),at.z()-3.5)))continue;
                }
                if(life.seenFires.size()>=16)life.seenFires.remove(life.seenFires.keySet().iterator().next());seen=now;life.seenFires.put(fire.key,seen);
            }
            if(now-seen>=.25*noticeDelay(profile)||gap<=fire.radius+1)known.add(new Hazard(fire.key,fire.centre,fire.reach,fire.radius+margin,fire.until,fire.kind));
        }
        if(life.pyre!=null) {if(now>=life.pyre.until)life.pyre=null;else known.add(life.pyre);}
        life.hazards=List.copyOf(known);
    }
    private Reaction leaveFire(Player p,Life life,double now) {
        for(var hazard:life.hazards) {
            if(!hazard.kind.equals("fire"))continue;double dx=p.position().x()-hazard.centre.x(),dy=p.position().y()-hazard.centre.y(),gap=Math.hypot(dx,dy);
            if(gap>=hazard.reach+.2)continue;
            double out=gap>1e-3?Math.atan2(dy,dx):mix(p.id(),p.lifeId(),hazard.key)*2*Math.PI;
            for(double turn:new double[]{0,.7,-.7,1.4,-1.4}) {
                var heading=new Vector(Math.cos(out+turn),Math.sin(out+turn),0);
                if(!p.grounded()||walkableHeading(world,p,heading,2))return new Reaction("fire_escape",heading,null,ahead(p,heading),false,true,Priority.SURVIVAL,null);
            }
        }
        return null;
    }
    private static double wrap(double angle) {double period=2*Math.PI;double shifted=(angle+Math.PI)%period;if(shifted<0)shifted+=period;return shifted-Math.PI;}
    private Intent steer(Life life,Intent intent,double now) {
        var m=intent.movement();double length=Math.hypot(m.direction().x(),m.direction().y());var body=life.body;
        if(length<=.1||body==null||intent.priority().value()>=Priority.SURVIVAL.value()||Set.of(Affordance.SWIM,Affordance.JETPACK,Affordance.JETPACK_CLIMB).contains(m.affordance()))return intent;
        double dx=m.direction().x()/length,dy=m.direction().y()/length;
        for(var h:life.hazards) {
            double x=h.centre.x()-body.position().x(),y=h.centre.y()-body.position().y(),gap=Math.hypot(x,y),along=x*dx+y*dy;
            if(gap<=h.reach+.2||along<=0||now>=h.until)continue;
            double speed=32*Math.hypot(body.velocity().x(),body.velocity().y()),berth=h.radius+.2*speed,reach=Math.min(along,4+.6*speed),miss=Math.hypot(x-dx*reach,y-dy*reach);
            if(miss>=berth)continue;String role=intent.debugRole()+":avoid_"+h.kind;
            if(m.affordance()==Affordance.WALK&&!m.jump()) {
                double bearing=Math.atan2(y,x),spread=Math.asin(Math.min(1,berth/gap))+.15;
                double side=life.steerSide.getOrDefault(h.key,wrap(Math.atan2(dy,dx)-bearing)>=0?1.:-1.);
                for(double turn:new double[]{side,-side}) {
                    double angle=bearing+turn*spread;var heading=new Vector(Math.cos(angle),Math.sin(angle),0);
                    if(walkableHeading(world,body,heading,2)) {
                        if(life.steerSide.size()>=8&&!life.steerSide.containsKey(h.key))life.steerSide.clear();life.steerSide.put(h.key,turn);
                        return amend(intent,movement(m,new Vector(heading.x()*length,heading.y()*length,0),m.sprint()&&gap>berth+4),intent.look(),role);
                    }
                }
            }
            return amend(intent,new Movement(ZERO,false,m.crouch(),false,false,Affordance.WALK,true,null,null,null,1),intent.look(),role);
        }
        return intent;
    }
    private void listen(Perception frame,Player p,BotProfile profile,Life life,Player visible,BotModeDecision decision,double now) {
        if(life.alert>0)life.alert*=Math.exp(-Math.max(0,now-life.alertAt)/6);life.alertAt=now;
        double newest=life.heardAt,loudness=Double.NEGATIVE_INFINITY,heardDistance=0;Stimulus loudest=null,fallen=null;
        for(var e:frame.stimuli()) {
            if(e.createdAt()<=life.heardAt)continue;newest=Math.max(newest,e.createdAt());
            if(e.kind()==StimulusKind.DEATH) {if(e.team()==p.team())fallen=e;continue;}
            double base=switch(e.kind()) {case EXPLOSION->1;case SHOT->.8;case BLOCK_DESTROYED->.5;case FOOTSTEP->.6;default->-1;};
            if(base<0||e.team()==p.team())continue;
            if(e.kind()==StimulusKind.SHOT) {
                if(life.shots.size()>=6&&!life.shots.containsKey(e.sourceId()))life.shots.remove(life.shots.entrySet().stream().min(Comparator.comparingDouble(v->v.getValue().at)).orElseThrow().getKey());
                life.shots.put(e.sourceId(),new Shot(e.position(),e.createdAt()));
            }
            double distance=distance(e.position(),p.position()),fade=Math.max(0,1-distance/HEARING_DISTANCE),value=base*(e.kind()==StimulusKind.FOOTSTEP?fade*fade:fade);
            if(loudest==null||value>loudness) {loudest=e;loudness=value;heardDistance=distance;}
        }
        life.heardAt=newest;
        if(fallen!=null&&allyDown(frame,p,profile,life,fallen,visible,decision,now)||loudest==null)return;
        double threshold=.06+.22*(1-profile.skill())+(visible!=null?.3:0)-.1*life.alert;
        if(loudness<threshold||inView(frame,p,loudest.sourceId()))return;
        life.alert=Math.min(1,life.alert+loudness);life.noise=loudest.position();life.noiseAt=now;
        if(visible!=null||now<life.glanceReadyAt||flightRole(p,decision))return;
        if(life.checked!=null&&now-life.checkedAt<=8&&distance(life.checked,loudest.position())<=12&&heardDistance>.7*life.checkedRange)return;
        life.checked=loudest.position();life.checkedAt=now;life.checkedRange=heardDistance;double dwell=.6+.6*profile.caution();
        life.glance=new Vector(loudest.position().x(),loudest.position().y(),p.eye().z());life.glanceFrom=now+.5*noticeDelay(profile);life.glanceUntil=life.glanceFrom+dwell;life.watchUntil=life.glanceFrom+3*dwell;life.glanceReadyAt=life.glanceUntil+1+2.5*(1-profile.caution());
    }
    private boolean allyDown(Perception frame,Player p,BotProfile profile,Life life,Stimulus event,Player visible,BotModeDecision decision,double now) {
        double distance=distance(event.position(),p.position());
        if(distance>25||sameTeam(frame,p,event.sourceId())||distance>10&&!world.lineOfSight(p.eye(),event.position()))return false;
        life.alert=Math.min(1,life.alert+.6);var heard=life.shots.get(event.sourceId());Vector origin;
        if(heard!=null&&event.createdAt()-heard.at>=0&&event.createdAt()-heard.at<=2)origin=heard.position;
        else {
            var anchor=frame.objectives().stream().filter(o->o.kind().equals("team_anchor")&&o.team()!=p.team()).map(Objective::position).findFirst().orElse(null);
            if(anchor==null)return false;var toward=unit(anchor.x()-event.position().x(),anchor.y()-event.position().y());
            origin=new Vector(event.position().x()+toward.x()*40,event.position().y()+toward.y()*40,event.position().z());
        }
        if(visible!=null||flightRole(p,decision)||!life.retreat.isEmpty())return true;
        double delay=noticeDelay(profile)*(1-.5*life.alert),dwell=1+.6*profile.caution();
        life.glance=new Vector(origin.x(),origin.y(),p.eye().z());life.glanceFrom=now+delay;life.glanceUntil=life.glanceFrom+dwell;life.watchUntil=life.glanceFrom+3*dwell;life.glanceReadyAt=life.glanceUntil+1;
        life.checked=origin;life.checkedAt=now;life.checkedRange=distance(origin,p.position());
        boolean committed=decision!=null&&decision.objectivePriority()>=.9,careful=profile.caution()>=.45||profile.skill()>=.6;
        if(committed||!careful||life.fireFrom!=null&&now<life.fireUntil)return true;
        life.fireFrom=origin;life.fireCause="ally_down";life.hits=2;life.fireNoticedAt=now+delay;life.lookBackUntil=life.fireNoticedAt+.35;
        life.fireUntil=life.fireNoticedAt+.8*(1.4+2.2*profile.caution()-.8*profile.aggression());life.shelter=new Shelter();life.shelterHits=0;life.dash=null;return true;
    }
    private boolean inView(Perception frame,Player p,int id) {
        for(var player:frame.players())if(player.id()==id) {
            double dx=player.eye().x()-p.eye().x(),dy=player.eye().y()-p.eye().y(),flat=Math.hypot(dx,dy),facing=flat>1e-6?(p.orientation().x()*dx+p.orientation().y()*dy)/flat:1;
            return player.alive()&&facing>=.3&&world.lineOfSight(p.eye(),player.eye());
        }
        return false;
    }
    private Reaction underFire(Perception frame,Player p,BotProfile profile,Life life,Player visible,BotModeDecision decision,double now) {
        if(visible!=null) {life.sightedId=visible.id();life.sightedAt=now;}
        double hitAt=p.lastDamageAt();
        if(hitAt>life.hitAt+1e-6) {
            life.hitAt=hitAt;var source=p.lastDamageSourcePosition();int attacker=p.lastDamageSourceId();boolean dueling=attacker==life.sightedId&&now-life.sightedAt<=2.5;
            if(source!=null&&attacker!=p.id()&&!dueling&&!UNDIRECTED.contains(p.lastDamageKind())&&now-hitAt>=0&&now-hitAt<=2&&!sameTeam(frame,p,attacker)) {
                life.fireCause="under_fire";
                if(now>=life.fireUntil) {
                    life.hits=0;life.fireNoticedAt=hitAt+noticeDelay(profile)*(1-.5*life.alert);life.lookBackUntil=life.fireNoticedAt+.35;life.shelter=new Shelter();life.shelterHits=0;life.dash=null;
                }
                life.hits++;life.fireFrom=source;boolean committed=decision!=null&&decision.objectivePriority()>=.9;
                life.fireUntil=Math.max(hitAt,life.fireNoticedAt)+(committed?.6:1)*(1.4+2.2*profile.caution()-.8*profile.aggression());
            }
        }
        var threat=life.fireFrom;if(threat==null)return null;
        if(now>=life.fireUntil) {
            life.fireFrom=null;life.glance=null;
            return (profile.aggression()>=.45||profile.skill()>=.6)&&visible==null?new Reaction("",ZERO,null,null,false,false,Priority.COMBAT,threat):null;
        }
        if(now<life.fireNoticedAt||visible!=null||flightRole(p,decision))return null;
        boolean eager=profile.skill()>=.35||profile.caution()>=.7;
        if(life.hits<(eager?1:2)) {life.glance=threat;life.glanceFrom=now;life.glanceUntil=life.watchUntil=now+.8;return null;}
        life.glance=null;var shelter=life.shelter;if(shelter.reached&&life.hits>life.shelterHits)shelter.clear();
        var line=profile.skill()>=.45?firingLine(p.position(),threat,3):List.of(threat);double side=mix(p.id(),p.lifeId(),life.hits)<.5?1:-1;
        var seek=seek(p,shelter,line,now,4.5+6.5*profile.skill(),2.5,side);boolean startled=now<life.lookBackUntil,airborne=!p.grounded();
        if(seek.state.equals("reached")) {if(life.shelterHits<=0)life.shelterHits=life.hits;return reaction(life.fireCause+"_hold",ZERO,null,threat,true,false);}
        if(seek.state.equals("run")) {life.shelterHits=0;return reaction(life.fireCause+"_cover",seek.heading,null,startled?threat:ahead(p,seek.heading),false,flat(shelter.spot,p.position())>3);}
        if(!airborne&&(life.dash==null||now>=life.dashUntil)) {
            life.dash=dashHeading(p,threat,life);life.dashes++;life.dashUntil=now+.7+.5*mix(p.id(),life.dashes,5);
            if(life.dashes>1) {startled=true;life.lookBackUntil=now+.35;}
        }
        if(life.dash==null)return reaction(life.fireCause+"_hold",ZERO,null,threat,!airborne,false);
        return reaction(life.fireCause+"_evade",life.dash,null,startled?threat:ahead(p,life.dash),false,true);
    }
    private record Seek(String state,Vector heading) {}
    private Seek seek(Player p,Shelter shelter,List<Vector> threats,double now,double reach,double minGap,double side) {
        if(shelter.spot==null&&p.grounded()&&now>=shelter.nextSearchAt) {
            shelter.nextSearchAt=now+.75;shelter.spot=findShelter(world,p,threats,reach,minGap,side,18);shelter.reached=false;shelter.best=Double.POSITIVE_INFINITY;shelter.progressAt=now;
        }
        if(shelter.spot==null)return new Seek("none",ZERO);double gap=flat(shelter.spot,p.position());
        if(gap<=1)shelter.reached=true;if(shelter.reached)return new Seek("reached",ZERO);
        if(gap+.3<shelter.best) {shelter.best=gap;shelter.progressAt=now;}
        if(now-shelter.progressAt>=1.5) {shelter.clear();return new Seek("none",ZERO);}
        return new Seek("run",unit(shelter.spot.x()-p.position().x(),shelter.spot.y()-p.position().y()));
    }
    private void countOdds(Perception frame,Player p,Life life,double now) {
        life.oddsAt=now+.45;var log=new ArrayList<Health>();for(var e:life.healthLog)if(now-e.at<=2)log.add(e);log.add(new Health(now,p.health()));life.healthLog=List.copyOf(log);
        var candidates=new ArrayList<Odds>();
        for(var player:frame.players()) {
            if(player.team()==p.team()||!player.alive()||!player.spawned()||player.spawnProtected())continue;
            double distance=distance(p.eye(),player.eye());if(distance>70)continue;
            double dx=player.eye().x()-p.eye().x(),dy=player.eye().y()-p.eye().y(),flat=Math.hypot(dx,dy),facing=flat>1e-6?(p.orientation().x()*dx+p.orientation().y()*dy)/flat:1;
            boolean marked=p.lastDamageSourceId()==player.id()&&now-p.lastDamageAt()>=0&&now-p.lastDamageAt()<=3;
            if(facing>=.2||marked||distance<=8)candidates.add(new Odds(distance,player.eye()));
        }
        candidates.sort(Comparator.comparingDouble(Odds::distance));var seen=new ArrayList<Odds>();
        for(var c:candidates.subList(0,Math.min(6,candidates.size())))if(world.lineOfSight(p.eye(),c.eye))seen.add(c);
        if(!seen.isEmpty()) {life.foes=seen.stream().map(Odds::eye).toList();life.foesAt=now;life.nearestFoe=seen.getFirst().distance;}
        else if(now-life.foesAt>1.5) {life.foes=List.of();life.nearestFoe=Double.POSITIVE_INFINITY;}
    }
    private Reaction outmatched(Perception frame,Player p,BotProfile profile,Life life,Player visible,BotModeDecision decision,double now) {
        if(!life.retreat.isEmpty())return retreat(frame,p,profile,life,visible,now);
        boolean pressed=p.lastDamageAt()>0&&now-p.lastDamageAt()>=0&&now-p.lastDamageAt()<=2.5&&!UNDIRECTED.contains(p.lastDamageKind());
        if(!pressed||now<life.retreatRestUntil) {life.outmatchedSince=0;if(!pressed)life.healthLog=List.of();return null;}
        if(now>=life.oddsAt)countOdds(frame,p,life,now);
        boolean outmatched=false;int odds=0;
        if(!life.foes.isEmpty()&&life.nearestFoe>7&&!holdsGround(p,decision)) {
            odds=life.foes.size()-(alliesNear(frame,p)+1);double lean=profile.caution()-profile.aggression();
            boolean outnumbered=odds>=(lean>.3?1:lean<-.4?3:2),hurt=p.health()<=22+26*profile.caution()-12*profile.aggression();
            int lost=life.healthLog.stream().mapToInt(Health::value).max().orElse(p.health())-p.health();boolean losing=lost>=20&&p.health()<=(odds>=1?2.5:1.5)*lost;
            outmatched=outnumbered||hurt||losing;
        }
        if(!outmatched) {if(now-life.outmatchedAt>1.5)life.outmatchedSince=0;return null;}
        life.outmatchedAt=now;if(life.outmatchedSince<=0)life.outmatchedSince=now;if(now-life.outmatchedSince<noticeDelay(profile))return null;
        var refuge=new Shelter();var seek=seek(p,refuge,life.foes,now,5+5.5*profile.skill(),0,homeSide(frame,p,life));life.rally=rallyPoint(frame,p,life,Double.POSITIVE_INFINITY);
        if(seek.state.equals("none")&&(odds<1||life.rally==null)) {life.retreatRestUntil=now+1.5;return null;}
        life.refuge=refuge;life.retreat=!seek.state.equals("none")?"cover":"fall_back";life.retreatUntil=now+(life.retreat.equals("cover")?4:12);life.retreatHitAt=p.lastDamageAt();life.deadGround=null;life.deadGroundAt=0;life.outmatchedSince=0;
        return retreat(frame,p,profile,life,visible,now);
    }
    private static int alliesNear(Perception frame,Player p) {return (int)frame.players().stream().filter(a->a.team()==p.team()&&a.alive()&&a.spawned()&&a.id()!=p.id()&&distance(a.position(),p.position())<=18).count();}
    private Reaction retreat(Perception frame,Player p,BotProfile profile,Life life,Player visible,double now) {
        if(life.foes.isEmpty())return endRetreat(life,profile,now);var foe=life.foes.getFirst();double side=homeSide(frame,p,life),hitAt=p.lastDamageAt();
        if(life.retreat.equals("regroup")&&hitAt>life.retreatHitAt+1e-6) {life.retreat="fall_back";life.refuge=new Shelter();}
        if(life.retreat.equals("fall_back")&&life.refuge.spot==null) {
            if(!seek(p,life.refuge,life.foes,now,10.5,0,side).state.equals("none")) {life.retreat="cover";life.retreatUntil=now+4;}
            else if(p.grounded()&&now>=life.deadGroundAt) {life.deadGroundAt=now+1.5;life.deadGround=findDeadGround(world,p,life.foes,side,14);}
        }
        if(life.retreat.equals("cover")) {
            var seek=seek(p,life.refuge,life.foes,now,10.5,0,side);
            if(seek.state.equals("run")&&now<life.retreatUntil)return reaction("disengage_cover",seek.heading,null,ahead(p,seek.heading),false,flat(life.refuge.spot,p.position())>3);
            if(seek.state.equals("reached"))settle(life,profile,hitAt,now);
            else {life.refuge.clear();life.retreat="fall_back";life.retreatUntil=now+12;}
        }
        if(life.retreat.equals("hold")) {
            if(visible!=null&&distance(visible.position(),p.position())<=14)return endRetreat(life,profile,now);
            boolean hitHere=hitAt>life.retreatHitAt+1e-6;
            if(!hitHere&&now<life.retreatUntil)return reaction("disengage_hold",ZERO,null,foe,true,false);
            boolean company=alliesNear(frame,p)>0;life.rally=rallyPoint(frame,p,life,60);
            if(!hitHere&&life.rally!=null&&!company&&wayExposed(p,life.rally,life.foes))life.rally=null;
            if(!hitHere&&(company||life.rally==null&&now>=life.retreatWait))return endRetreat(life,profile,now);
            if(!hitHere&&life.rally==null) {life.retreatUntil=now+1;return reaction("disengage_hold",ZERO,null,foe,true,false);}
            if(hitHere)life.rally=rallyPoint(frame,p,life,Double.POSITIVE_INFINITY);
            life.retreat=hitHere?"fall_back":"regroup";life.refuge=new Shelter();life.deadGround=null;life.retreatHitAt=hitAt;life.retreatUntil=now+12;
        }
        var rally=life.rally;
        if(rally==null||now>=life.retreatUntil||flat(rally,p.position())<=8||life.retreat.equals("regroup")&&alliesNear(frame,p)>0)return endRetreat(life,profile,now);
        var away=unit(p.position().x()-foe.x(),p.position().y()-foe.y());
        if(life.retreat.equals("fall_back")&&life.deadGround!=null) {
            if(flat(life.deadGround,p.position())<=2.5) {settle(life,profile,hitAt,now);return reaction("disengage_hold",ZERO,null,foe,true,false);}
            return reaction("fall_back",away,life.deadGround,null,false,true);
        }
        return reaction(life.retreat.equals("regroup")?"regroup":"fall_back",away,rally,null,false,true);
    }
    private boolean wayExposed(Player p,Vector rally,List<Vector> foes) {
        var heading=unit(rally.x()-p.position().x(),rally.y()-p.position().y());var surface=world.surface((int)Math.floor(p.position().x()+heading.x()*4),(int)Math.floor(p.position().y()+heading.y()*4),p.position().z(),2,false);
        return surface==null||exposed(world,surface.position(),foes);
    }
    private static void settle(Life life,BotProfile p,double hitAt,double now) {life.retreat="hold";life.retreatHitAt=hitAt;life.retreatUntil=now+2+3.5*p.caution();life.retreatWait=now+5+7*p.caution()-2.5*p.aggression();}
    private static Reaction endRetreat(Life life,BotProfile p,double now) {life.retreat="";life.refuge=new Shelter();life.retreatRestUntil=now+5+6*p.aggression();return null;}
    private static Vector rallyPoint(Perception frame,Player p,Life life,double reach) {
        var foe=life.foes.isEmpty()?null:life.foes.getFirst();
        var nearest=frame.players().stream().filter(a->a.team()==p.team()&&a.alive()&&a.spawned()&&a.id()!=p.id()&&distance(a.position(),p.position())>8&&(foe==null||distance(a.position(),foe)>12))
            .min(Comparator.comparingDouble(a->distance(a.position(),p.position()))).orElse(null);
        if(nearest!=null&&distance(nearest.position(),p.position())<=reach)return nearest.position();if(reach<Double.POSITIVE_INFINITY)return null;
        return frame.objectives().stream().filter(o->o.kind().equals("team_anchor")&&o.team()==p.team()).map(Objective::position).findFirst().orElse(null);
    }
    private static double homeSide(Perception frame,Player p,Life life) {
        var rally=rallyPoint(frame,p,life,Double.POSITIVE_INFINITY);if(rally==null||life.foes.isEmpty())return 1;var foe=life.foes.getFirst();
        double ax=p.position().x()-foe.x(),ay=p.position().y()-foe.y(),hx=rally.x()-p.position().x(),hy=rally.y()-p.position().y();return ax*hy-ay*hx>=0?1:-1;
    }
    private Vector dashHeading(Player p,Vector threat,Life life) {
        double away=Math.atan2(p.position().y()-threat.y(),p.position().x()-threat.x()),first=mix(p.id(),p.lifeId(),9)<.5?1:-1,side=life.dashes%2==0?first:-first;
        for(double turn:new double[]{.9,-.9,.35,-.35,1.5,-1.5}) {double angle=away+turn*side;var heading=new Vector(Math.cos(angle),Math.sin(angle),0);if(walkableHeading(world,p,heading,3))return heading;}
        return null;
    }
    private static boolean sameTeam(Perception frame,Player p,int id) {return frame.players().stream().anyMatch(a->a.id()==id&&a.team()==p.team());}
    private static boolean headFree(Intent i) {return i.action().kind()==ActionKind.NONE&&i.priority().value()<Priority.SURVIVAL.value()&&(i.look()==null||!i.look().visible())&&i.movement().affordance()==Affordance.WALK&&!i.movement().jump();}
}
