// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.bot.*;
import net.battlespades.bot.BotMessages.Action;
import net.battlespades.combat.*;
import net.battlespades.entities.*;
import net.battlespades.physics.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.world.BlockDamageModel.Cell;
import net.battlespades.world.MutableWorld;

/** Concrete gateway over the active match's ordinary gameplay authorities. */
public final class HostedBotActions implements BotActionGateway.Services {
    private final StandardMatchDomain match;
    private final BotActionGateway gateway;
    private static final Map<Integer,Double> SPEEDS=Map.ofEntries(Map.entry(11,50.),Map.entry(31,35.),Map.entry(32,50.),Map.entry(33,40.),Map.entry(12,75.),Map.entry(13,150.),Map.entry(14,40.),Map.entry(29,50.),Map.entry(54,40.),Map.entry(55,75.),Map.entry(57,50.),Map.entry(58,75.));
    public HostedBotActions(StandardMatchDomain match){this.match=Objects.requireNonNull(match);gateway=new BotActionGateway(this);}
    public BotActionGateway gateway(){return gateway;}
    @Override public PlayerLife occupant(int id){return match.lives().players().stream().filter(p->p.id()==id).findFirst().orElse(null);}
    @Override public boolean select(PlayerLife p,int tool){if(!match.inputs().authorized(p,tool))return false;p.fire().tool(tool,true);return p.inventory().tool()==tool;}
    @Override public boolean fire(PlayerLife p){
        int tool=p.inventory().tool();if(!BotCombatProfiles.FIREARMS.contains(tool)&&!p.fire().spade())return false;
        var direction=BotActionSafety.normalized(p.aim());if(direction==null)return false;
        int loop=match.frameLoop(),seed=(int)(((long)loop*1103515245+(long)p.id()*12345)&0x7fffffff);
        return match.combatRuntime().shots().shot(p,new ShootPacket(loop,p.id(),0,wire(p.position()),wire(direction),0,0,false,(p.motion().snapshot().actions()&2)!=0,seed));
    }
    @Override public boolean reload(PlayerLife p){return match.lives().reload(p);}
    @Override public boolean blockIntervalReady(PlayerLife p){return match.construction().blockIntervalReady(p);}
    @Override public void palette(PlayerLife p,String argument){
        if(argument==null||!argument.startsWith("rgb:"))return;
        try{int color=new java.math.BigInteger(argument.substring(4),16).intValue()&0xffffff;if(color!=p.color())match.lives().packet(p.id(),new SimplePackets.SetColor(p.id(),color));}catch(NumberFormatException ignored){/* Optional palette argument. */}
    }
    private static Cell cell(Vector at){return new Cell((int)at.x(),(int)at.y(),(int)at.z());}
    @Override public boolean build(PlayerLife p,Vector at){
        var c=cell(at);boolean repair=match.demolition().map(d->d.base(p.team()).destroyed().contains(new MutableWorld.Cell(c.x(),c.y(),c.z()))).orElse(false);
        Long reservation=null;if(!repair){reservation=match.construction().safety().reserve(p.id(),p.team(),List.of(c)).token();if(reservation==null)return false;}
        boolean accepted=false;try{accepted=match.construction().single(p,c,match.frameLoop());return accepted;}finally{if(!accepted)match.construction().safety().release(reservation);}
    }
    @Override public boolean buildLine(PlayerLife p,Vector start,Vector end){
        var a=cell(start);var b=cell(end);
        long count=1+Math.abs((long)a.x()-b.x())+Math.abs((long)a.y()-b.y())+Math.abs((long)a.z()-b.z());if(count>64)return false;
        var cells=WorldQueries.cubeLine(new WorldQueries.Cube(a.x(),a.y(),a.z()),new WorldQueries.Cube(b.x(),b.y(),b.z())).stream().map(c->new Cell(c.x(),c.y(),c.z())).toList();
        var token=match.construction().safety().reserve(p.id(),p.team(),cells).token();if(token==null)return false;
        boolean accepted=false;try{accepted=match.construction().line(p,a,b,match.frameLoop());return accepted;}finally{if(!accepted)match.construction().safety().release(token);}
    }
    @Override public boolean paint(PlayerLife p,Vector at){return match.construction().paint(p,cell(at),p.color(),match.frameLoop());}
    @Override public boolean prefab(PlayerLife p,int tool,String name,Vector at,int yaw){return match.prefabs().placeBot(p,name,at,yaw);}
    @Override public boolean deploy(PlayerLife p,int tool,Vector at,int face,double yaw){
        var actions=match.entities().actions();
        boolean accepted=switch(tool){case 15->actions.machineGun(p,at,yaw);case 16->actions.turret(p,at,yaw);case 20,56->actions.place(p,tool,at,4);default->actions.place(p,tool,at,face);};
        if(accepted){match.inputs().breakDisguise(p);p.endProtection(match.monotonic());}return accepted;
    }
    private BotActionSafety.Actor actor(PlayerLife p){return new BotActionSafety.Actor(p.id(),p.team(),p.alive(),p.spawned(),p.position(),p.position());}
    @Override public boolean explosiveSafe(PlayerLife p,int tool,Vector at){
        double radius=Math.max(tool==21?5:tool==20?3:tool==59?8:0,Optional.ofNullable(ProjectileSpec.TOOLS.get(tool)).map(ProjectileSpec::radius).orElse(0.));
        var explosives=match.entities().registry().all().stream().filter(e->e.behavior instanceof DeployableRuntime.Explosive).map(e->new BotActionSafety.Explosive(e.type,e.alive,new Vector(e.x,e.y,e.z),((DeployableRuntime.Explosive)e.behavior).blast().radius())).toList();
        return BotActionSafety.deploySafe(actor(p),at,radius,match.lives().players().stream().map(this::actor).toList(),explosives);
    }
    @Override public boolean detonate(PlayerLife p){return match.entities().actions().detonate(p);}
    @Override public boolean disguise(PlayerLife p,boolean communityOnly){return !communityOnly&&match.entities().actions().authorized(p,64)&&p.disguise().activate(p,null,match.monotonic());}
    // StandardMatchDomain excludes community/editor modes at its factory boundary.
    @Override public boolean modeCommand(PlayerLife p,String command,Vector at){return false;}
    @Override public boolean oriented(PlayerLife p,Action action){
        int tool=action.toolId();var spec=ProjectileSpec.TOOLS.get(tool);var speed=SPEEDS.get(tool);var direction=BotActionSafety.normalized(p.aim());if(spec==null||speed==null||direction==null)return false;
        var target=tool==12||tool==13?action.endPosition():null;
        if(!BotActionSafety.launchSafe(actor(p),direction,spec.radius(),target,spec.name().equals("snowball")?1:3,match.lives().players().stream().map(this::actor).toList(),(o,d,length)->WorldQueries.ray(CollisionMap.of(match.world()),o,d,length,false).isPresent())||!select(p,tool))return false;
        var eye=p.position();var velocity=p.body().state().velocity();
        var origin=new Vector(eye.x()+direction.x()*.6,eye.y()+direction.y()*.6,eye.z()+direction.z()*.6);
        var launch=new Vector(direction.x()*speed+velocity.x()*.35,direction.y()*speed+velocity.y()*.35,direction.z()*speed+velocity.z()*.35);
        double fuse=spec.motion()==ProjectileSpec.Motion.BOUNCE?2.5:0;
        return match.entities().projectiles().use(p,tool,wire(origin),wire(launch),fuse,null);
    }
    private static Vector3f wire(Vector v){return new Vector3f((float)v.x(),(float)v.y(),(float)v.z());}
    @Override public void rejected(PlayerLife p,Action action,RuntimeException failure){match.failure("bot action "+p.id()+" "+action.kind(),failure);}
}
