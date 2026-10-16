// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.physics.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;

/** Owner-confined Character lifetime. Input chronology, hit validation and corpse simulation are separate policies. */
public final class PlayerLife {
    private final Thread owner=Thread.currentThread();
    private final int id,team,language;
    private final String name,mode;
    private final CollisionMap world;
    private final GameRules rules;
    private final LifeInventory inventory=new LifeInventory();
    private final DamageHistory damageHistory=new DamageHistory();
    private final InputTimeline inputTimeline=new InputTimeline();
    private final ActionClock actionClock=new ActionClock(inputTimeline);
    private ClassCatalog.Selection selection,pending;
    private PlayerPhysics body;
    private PlayerPhysics.Vector position=new PlayerPhysics.Vector(0,0,0),aim=new PlayerPhysics.Vector(1,0,0);
    private double sideX,sideY=1,spawnedAt,deathTime,walletScale;
    private long generation;
    private int health=LifeCatalog.HEALTH,maxHealth=LifeCatalog.HEALTH,jetpack,parachute,color=0x707070,lastDamageSource=-1,lastDamageType=-1;
    private double lastDamageWall,lastCombatHit;
    private PlayerPhysics.Vector lastDamagePosition;
    private boolean alive,spawned,protectionCancelled,god;
    private Double protectionCap;
    private LifePackets.KillAction lastDeath;
    public PlayerLife(int id,int team,int language,String name,String mode,CollisionMap world,GameRules rules,ClassCatalog.Selection selection){
        this(id,team,language,name,mode,world,rules,selection,rules.number("RULE_CHARACTER_BLOCK_WALLETS"));
    }
    public PlayerLife(int id,int team,int language,String name,String mode,CollisionMap world,GameRules rules,ClassCatalog.Selection selection,double walletScale){
        if(id<0||id>254||!(team==0||team==2||team==3))throw new IllegalArgumentException("Player identity");
        this.id=id;this.team=team;this.language=language;this.name=Objects.requireNonNull(name);this.mode=Objects.requireNonNull(mode);this.world=Objects.requireNonNull(world);this.rules=Objects.requireNonNull(rules);walletScale(walletScale);inventory.classWallet(0,1,true);apply(selection);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Life accessed outside owner");}
    public int id(){owned();return id;}public int team(){owned();return team;}public int health(){owned();return health;}
    public int maxHealth(){owned();return maxHealth;}public boolean god(){owned();return god;}
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
        inputTimeline.resetLife();actionClock.reset(null);body=new PlayerPhysics(world);body.position(at);body.profile(ClassCatalog.movement(selection.classId(),mode,rules));orientBody();body.flight(new PlayerPhysics.Flight(jetpack==0?0:jetpack-65,false,false,false,parachute!=0,false,0.1f,0.05f,false));
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
        owned();if(!alive)return false;alive=false;spawned=false;deathTime=wall;inventory.reloadState(false,0);
        body.alive(false);body.input(new PlayerPhysics.Input(false,false,false,false,false,false,false));body.flight(new PlayerPhysics.Flight(jetpack==0?0:jetpack-65,false,false,false,parachute!=0,false,0.1f,0.05f,false));return true;
    }
    public void protectionCap(Double value){owned();protectionCap=value;}
    public double protectionRemaining(double now){owned();if(!alive||protectionCancelled)return 0;double duration=rules.number("RULE_SPAWN_PROTECTION_TIME");if(protectionCap!=null&&protectionCap<duration)duration=protectionCap;return duration<=0?0:Math.max(0,duration-(now-spawnedAt));}
    public boolean endProtection(double now){owned();if(protectionRemaining(now)<=0)return false;protectionCancelled=true;return true;}
    private static Vector3f wire(PlayerPhysics.Vector p){return new Vector3f((float)p.x(),(float)p.y(),(float)p.z());}
    public PlayerPackets.CreatePlayer create(){owned();return new PlayerPackets.CreatePlayer(id,0,selection.classId(),team,0,language,wire(position()),wire(aim),name,selection.loadout(),selection.prefabs());}
    public List<GamePacket> spawnOwnerPackets(){owned();return List.of(new LifePackets.SetHP(LifeCatalog.HEALTH,2,new Vector3f(0,0,0)),new SimplePackets.Restock(id,0));}
}
