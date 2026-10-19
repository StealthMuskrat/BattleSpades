// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.physics.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;

/** Owner-confined Character lifetime. Input chronology, hit validation and corpse simulation are separate policies. */
public final class PlayerLife {
    private final Thread owner=Thread.currentThread();
    private final int id,language;
    private int team;
    private final String name,mode;
    private final CollisionMap world;
    private final GameRules rules;
    private final LifeInventory inventory=new LifeInventory();
    private final DamageHistory damageHistory=new DamageHistory();
    private final InputTimeline inputTimeline=new InputTimeline();
    private final OwnerAnchors ownerAnchors=new OwnerAnchors(inputTimeline);
    private final ActionClock actionClock=new ActionClock(inputTimeline);
    private final PlayerMotion motion=new PlayerMotion();
    private final MovementPacketRates packetRates=new MovementPacketRates();
    private final PlayerPositionReports positionReports=new PlayerPositionReports();
    public MovementPacketRates packetRates(){owned();return packetRates;}
    public PlayerPositionReports positionReports(){owned();return positionReports;}
    private final PlayerFlight flight=new PlayerFlight();
    private final net.battlespades.combat.ShotTally.Statistics combatStatistics=new net.battlespades.combat.ShotTally.Statistics();
    private final net.battlespades.combat.ShotValidation.Statistics shotStatistics=new net.battlespades.combat.ShotValidation.Statistics();
    private final PlayerFire fire=new PlayerFire(this);
    private final net.battlespades.combat.LagCompensation.PlayerTarget lagTarget=new net.battlespades.combat.LagCompensation.PlayerTarget(this);
    private ClassCatalog.Selection selection,pending;
    private PlayerPhysics body;
    private PlayerPhysics.Vector position=new PlayerPhysics.Vector(0,0,0),aim=new PlayerPhysics.Vector(1,0,0);
    private double sideX,sideY=1,spawnedAt,deathTime,walletScale;
    private long generation;
    private int worldUpdateAck;
    private List<Integer> chargeIds=List.of();
    private Integer radarEntityId;
    private Integer pickupId,pickupState;
    private boolean pickupBurdensome;
    private double hardcoreNextHeal;
    private Double lastLagRewindMillis;
    private int health=LifeCatalog.HEALTH,maxHealth=LifeCatalog.HEALTH,jetpack,parachute,color=0x707070,lastDamageSource=-1,lastDamageType=-1;
    private double lastDamageWall,lastCombatHit;
    private PlayerPhysics.Vector lastDamagePosition;
    private boolean alive,spawned,protectionCancelled,god,bot,diedReloading;
    private Double protectionCap;
    private Double balanceMovedAt;
    Double balanceMovedAt(){owned();return balanceMovedAt;}
    void balanceMovedAt(double value){owned();balanceMovedAt=value;}
    private LifePackets.KillAction lastDeath;
    public PlayerLife(int id,int team,int language,String name,String mode,CollisionMap world,GameRules rules,ClassCatalog.Selection selection){
        this(id,team,language,name,mode,world,rules,selection,rules.number("RULE_CHARACTER_BLOCK_WALLETS"));
    }
    public PlayerLife(int id,int team,int language,String name,String mode,CollisionMap world,GameRules rules,ClassCatalog.Selection selection,double walletScale){
        if(id<0||id>254||!(team==0||team==2||team==3))throw new IllegalArgumentException("Player identity");
        this.id=id;this.team=team;this.language=language;this.name=Objects.requireNonNull(name);this.mode=Objects.requireNonNull(mode);this.world=Objects.requireNonNull(world);this.rules=Objects.requireNonNull(rules);walletScale(walletScale);inventory.classWallet(0,1,true);apply(selection);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Life accessed outside owner");}
    void team(int value){owned();if(value!=0&&value!=2&&value!=3)throw new IllegalArgumentException("Team");team=value;}
    void color(int value){owned();color=value&0xffffff;}
    void clearPending(){owned();pending=null;}
    public int id(){owned();return id;}public int team(){owned();return team;}public int health(){owned();return health;}
    public boolean diedReloading(){owned();return diedReloading;}
    public String name(){owned();return name;}
    public Integer radarEntityId(){owned();return radarEntityId;}
    public Integer pickupId(){owned();return pickupId;}
    public Integer pickupState(){owned();return pickupState;}
    public boolean pickupBurdensome(){owned();return pickupBurdensome;}
    void carry(Integer id,boolean burdensome,Integer state){owned();pickupId=id;pickupBurdensome=burdensome;pickupState=state;if(body!=null)body.burdened(burdensome);}
    public void radarEntityId(Integer id){owned();radarEntityId=id;}
    public double hardcoreNextHeal(){owned();return hardcoreNextHeal;}
    public void hardcoreNextHeal(double wall){owned();hardcoreNextHeal=wall;}
    public List<Integer> chargeIds(){owned();return chargeIds;}
    public void chargeIds(List<Integer> value){owned();chargeIds=List.copyOf(value);}
    public void forgetCharge(int id){owned();chargeIds=chargeIds.stream().filter(value->value!=id).toList();}
    public int worldUpdateAck(){owned();return worldUpdateAck;}
    public void worldUpdateAck(int value){owned();worldUpdateAck=value;}
    public OptionalDouble lastLagRewindMillis(){owned();return lastLagRewindMillis==null?OptionalDouble.empty():OptionalDouble.of(lastLagRewindMillis);}
    public void lastLagRewindMillis(double value){owned();lastLagRewindMillis=value;}
    public boolean toolEnabled(int tool){owned();return rules.toolEnabled(tool);}
    public int maxHealth(){owned();return maxHealth;}public boolean god(){owned();return god;}
    public boolean bot(){owned();return bot;}public void bot(boolean value){owned();bot=value;}
    public double lastDamageWall(){owned();return lastDamageWall;}public double lastCombatHit(){owned();return lastCombatHit;}
    public int lastDamageSource(){owned();return lastDamageSource;}public int lastDamageType(){owned();return lastDamageType;}
    public PlayerPhysics.Vector lastDamagePosition(){owned();return lastDamagePosition;}public DamageHistory damageHistory(){owned();return damageHistory;}
    /** Trusted mode state, not a packet handler or a damage/heal event. */
    public void health(int value){owned();if(value<0)throw new IllegalArgumentException("Negative HP");health=value;}
    public void maxHealth(int value){owned();maxHealth=value;}public void god(boolean value){owned();god=value;}
    void damageStamp(double wall){owned();lastDamageWall=wall;}
    void combatStamp(PlayerLife source,int type,double now){owned();lastCombatHit=now;lastDamageSource=source.id();lastDamagePosition=source.position();lastDamageType=type;}
    public long generation(){owned();return generation;}public boolean alive(){owned();return alive;}public boolean spawned(){owned();return spawned;}
    public double deathTime(){owned();return deathTime;}public double spawnedAt(){owned();return spawnedAt;}
    public int jetpack(){owned();return jetpack;}public int parachute(){owned();return parachute;}public int color(){owned();return color;}
    public LifeInventory inventory(){owned();return inventory;}public ClassCatalog.Selection selection(){owned();return selection;}
    public Optional<ClassCatalog.Selection> pendingSelection(){owned();return Optional.ofNullable(pending);}
    public PlayerPhysics body(){owned();if(body==null)throw new IllegalStateException("No Character body");return body;}
    public PlayerPhysics.Vector position(){owned();return body==null?position:body.state().position();}
    public PlayerPhysics.Vector aim(){owned();return aim;}public LifePackets.KillAction lastDeath(){owned();return lastDeath;}
    public InputTimeline inputTimeline(){owned();return inputTimeline;}
    public ActionClock actionClock(){owned();return actionClock;}
    public PlayerMotion motion(){owned();return motion;}
    public PlayerFlight flight(){owned();return flight;}
    public net.battlespades.combat.ShotTally.Statistics combatStatistics(){owned();return combatStatistics;}
    public net.battlespades.combat.ShotValidation.Statistics shotStatistics(){owned();return shotStatistics;}
    public PlayerFire fire(){owned();return fire;}
    public net.battlespades.combat.LagCompensation.PlayerTarget lagTarget(){owned();return lagTarget;}
    public OwnerAnchors ownerAnchors(){owned();return ownerAnchors;}
    /** Call only after a self WorldUpdate carrying these exact values was queued to this owner. */
    public void recordOwnerAnchor(int stamp,PlayerPhysics.Vector position,PlayerPhysics.Vector velocity,Integer serverTick,Long suppliedSequence){
        owned();ownerAnchors.record(stamp,position,velocity,serverTick,suppliedSequence);flight.ownerRowQueued(this);
    }
    MovementProfile movementProfile(){return ClassCatalog.movement(selection.classId(),mode,rules);}
    double canopyWaterDamage(){return ClassCatalog.get(selection.classId()).movement().get(8);}
    double canopyClearance(){
        var at=position();double feet=at.z()+((motion.movement&32)!=0&&!body.state().wade()?1.35:2.25);
        int start=Math.max(0,(int)Math.floor(feet));double best=Double.POSITIVE_INFINITY;
        for(var corner:new double[][]{{0,0},{-.45,-.45},{.45,-.45},{-.45,.45},{.45,.45}}){
            int x=(int)Math.floor(at.x()+corner[0]),y=(int)Math.floor(at.y()+corner[1]),ground=239;
            if(x>=0&&x<512&&y>=0&&y<512&&start<240){
                int top=239;for(int z=0;z<240;z++)if(world.solid(x,y,z)){top=z;break;}
                ground=top;for(int z=start;z<240;z++)if(world.solid(x,y,z)){ground=z;break;}
            }
            best=Math.min(best,ground-feet);
        }
        return best;
    }
    String movementMode(){owned();return mode;}
    boolean waterFallDamage(){owned();return rules.enabled("RULE_ENABLE_FALL_ON_WATER_DAMAGE");}
    /** This API accepts a previously validated selection; packet-13 cooldown/interaction gates are not bypassed here. */
    public void stage(ClassCatalog.Selection value){owned();validate(value);pending=value;}
    private static void validate(ClassCatalog.Selection value){Objects.requireNonNull(value);if(!ClassCatalog.definitions().containsKey(value.classId()))throw new IllegalArgumentException("Class id");}
    public void walletScale(double value){owned();if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Wallet scale");walletScale=value;}
    public void apply(ClassCatalog.Selection value){owned();validate(value);selection=new ClassCatalog.Selection(value.classId(),value.loadout().stream().filter(rules::toolEnabled).toList(),rules.enabled("RULE_ENABLE_PREFABS")?value.prefabs():List.of(),value.ugcTools());inventory.classWallet(selection.classId(),walletScale,false);if(body!=null)body.profile(ClassCatalog.movement(selection.classId(),mode,rules));}
    public boolean applyPending(){owned();if(pending==null)return false;apply(pending);pending=null;return true;}
    public void orientation(double x,double y,double z){
        owned();if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;
        double length=Math.sqrt(x*x+y*y+z*z);if(length>1e-6)aim=new PlayerPhysics.Vector(x/length,y/length,z/length);
        orientBody();
    }
    private void orientBody(){
        double x=aim.x(),y=aim.y(),z=aim.z();
        if(Math.sqrt(x*x+y*y)<=0.001){double fx=sideY,fy=-sideX,length=Math.sqrt(fx*fx+fy*fy);if(length>1e-6){fx/=length;fy/=length;if(Math.abs(z)<=1e-6){x=fx;y=fy;z=0;}else{x=fx*0.001;y=fy*0.001;z=Math.copySign(Math.sqrt(1-0.001*0.001),z);}}}
        if(body!=null)body.orientation(new PlayerPhysics.Vector(x,y,z));double horizontal=Math.sqrt(x*x+y*y);if(horizontal<=0){sideX=0;sideY=1;}else{sideX=-y/horizontal;sideY=x/horizontal;}
    }
    /** Called after pending selection and synchronous mode preparation, with a verified spawn point. */
    public void spawn(PlayerPhysics.Vector at,double monotonic,double walletScale,int teamColor){
        owned();if(team==0)throw new IllegalStateException("Spectator cannot spawn");if(!Double.isFinite(monotonic))throw new IllegalArgumentException("Spawn clock");
        walletScale(walletScale);inventory.spawn(selection.classId(),walletScale);position=Objects.requireNonNull(at);generation++;health=maxHealth=LifeCatalog.HEALTH;damageHistory.clear();spawnedAt=monotonic;protectionCancelled=false;protectionCap=null;alive=true;spawned=true;lastDeath=null;color=teamColor&0xffffff;
        jetpack=selection.loadout().stream().filter(LifeCatalog.PACK_FUEL::containsKey).findFirst().orElse(0);parachute=selection.loadout().contains(72)?72:0;
        carry(null,false,null);hardcoreNextHeal=0;inputTimeline.resetLife();actionClock.reset(null);motion.resetLife();flight.resetLife();fire.resetLife();ownerAnchors.resetLife(at);body=new PlayerPhysics(world);body.position(at);body.profile(movementProfile());orientBody();body.flight(flight.mover(this));
        positionReports.spawned(at);
        // RoundLifecycle, rather than Player.spawn itself, clears death_time after a successful spawn.
    }
    public void clearDeathTime(){owned();deathTime=0;}
    public void camera(PlayerPhysics.Vector at,int teamColor){owned();if(alive||spawned)throw new IllegalStateException("Live camera replacement");position=Objects.requireNonNull(at);color=teamColor&0xffffff;}
    public void joinedDead(double wall,LifePackets.KillAction packet){owned();if(alive||spawned)throw new IllegalStateException("Already spawned");deathTime=wall;lastDeath=packet==null?null:packet.quiet();}
    /** Returns false for an already-dead body; transition deaths deliberately retain current HP. */
    public boolean die(double wall,LifePackets.KillAction packet){
        owned();if(!beginDeath(wall))return false;rememberDeath(packet);return true;
    }
    public void rememberDeath(LifePackets.KillAction packet){owned();if(alive)throw new IllegalStateException("Live death packet");lastDeath=packet.quiet();}
    public boolean beginDeath(double wall){
        owned();if(!alive)return false;alive=false;spawned=false;deathTime=wall;diedReloading=inventory.reloading();inventory.reloadState(false,0);motion.died();flight.died();
        body.alive(false);body.input(new PlayerPhysics.Input(false,false,false,false,false,false,false));body.flight(flight.mover(this));return true;
    }
    public void protectionCap(Double value){owned();protectionCap=value;}
    public double protectionRemaining(double now){owned();if(!alive||protectionCancelled)return 0;double duration=rules.number("RULE_SPAWN_PROTECTION_TIME");if(protectionCap!=null&&protectionCap<duration)duration=protectionCap;return duration<=0?0:Math.max(0,duration-(now-spawnedAt));}
    public boolean endProtection(double now){owned();if(protectionRemaining(now)<=0)return false;protectionCancelled=true;return true;}
    private static Vector3f wire(PlayerPhysics.Vector p){return new Vector3f((float)p.x(),(float)p.y(),(float)p.z());}
    public PlayerPackets.CreatePlayer create(){owned();return new PlayerPackets.CreatePlayer(id,0,selection.classId(),team,0,language,wire(position()),wire(aim),name,selection.loadout(),selection.prefabs());}
    public List<GamePacket> spawnOwnerPackets(){owned();return List.of(new LifePackets.SetHP(LifeCatalog.HEALTH,2,new Vector3f(0,0,0)),new SimplePackets.Restock(id,0));}
}
