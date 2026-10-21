// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.Affordance;

/** Per-connection director fences, action convergence lease and bounded exactly-once task ledger. */
public final class BotIntentState {
    private final Thread owner=Thread.currentThread();
    private final int id,generation;private Intent intent;private long lastFrame=-1,lastActionFrame=-1;
    private Action pending;private Vector pendingLook;private boolean pendingVisible,pendingSecondary,pendingZoom;
    private double pendingDeadline;private Priority pendingPriority=Priority.ROUTINE;private long pendingLife=-1;
    private int lockPlayer=-1,lockGeneration;private double lockConfirmed,lockStarted;
    private long ledgerLife=-1,highWater;private final LinkedHashMap<Long,Boolean> ledger=new LinkedHashMap<>();
    private Feedback feedback=new Feedback("",true,null,-1,0,0,"",true,0);
    public record Feedback(String kind,boolean accepted,Vector position,long frame,double at,long request,String reason,boolean taskAccepted,double taskAt){}
    public BotIntentState(int id,int generation){this.id=id;this.generation=generation;}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Bot intent state outside owner");}
    public Intent intent(){owned();return intent;}public Action pending(){owned();return pending;}
    public Vector pendingLook(){owned();return pendingLook;}public boolean pendingVisible(){owned();return pendingVisible;}
    public boolean pendingSecondary(){owned();return pendingSecondary;}public boolean pendingZoom(){owned();return pendingZoom;}
    public double pendingDeadline(){owned();return pendingDeadline;}public long pendingLife(){owned();return pendingLife;}
    public Feedback feedback(){owned();return feedback;}public long lastActionFrame(){owned();return lastActionFrame;}
    public double lockStarted(){owned();return lockStarted;}
    public int lockPlayer(){owned();return lockPlayer;}
    public boolean locked(double now){owned();return lockPlayer>=0&&now-lockConfirmed<=.45;}
    /** A carried objective can revoke weapon use between two perception samples. */
    public boolean suppressWeapons(){owned();boolean weapon=pending!=null&&(pending.kind()==ActionKind.FIRE||pending.kind()==ActionKind.MELEE);if(weapon)clearPending();boolean proposed=intent!=null&&(intent.action().kind()==ActionKind.FIRE||intent.action().kind()==ActionKind.MELEE);if(intent!=null&&(proposed||intent.action().kind()==ActionKind.NONE&&intent.movement().affordance()==Affordance.BREACH&&(intent.look()!=null||intent.toolId()>=0||intent.secondaryFire()||intent.zoom())))intent=new Intent(intent.botId(),intent.botGeneration(),intent.frameId(),intent.mapEpoch(),intent.modeEpoch(),intent.topologyVersion(),intent.createdAt(),intent.expiresAt(),intent.movement(),null,-1,Action.none(),intent.priority(),false,false,intent.debugGoal(),intent.debugPath(),intent.debugRole(),intent.lifeId(),intent.debugNavigation());lockPlayer=-1;return weapon||proposed;}
    public boolean lock(int target,int targetGeneration,double now){owned();return target>=0&&target==lockPlayer&&targetGeneration==lockGeneration&&now-lockConfirmed<=.45;}
    /** Older topology remains usable; current collision and action authorities revalidate it. */
    public boolean accept(Intent next,long life,long map,long mode,long topology,double now){
        owned();Objects.requireNonNull(next);
        if(next.botId()!=id||next.botGeneration()!=generation||next.lifeId()>=0&&next.lifeId()!=life||next.mapEpoch()!=map||next.modeEpoch()!=mode||next.topologyVersion()>topology||!Double.isFinite(next.expiresAt())||next.expiresAt()<=now||next.frameId()<=lastFrame)return false;
        if(pending!=null&&(next.priority().value()>pendingPriority.value()||intent!=null&&intent.movement().affordance()==Affordance.BREACH&&next.movement().affordance()!=Affordance.BREACH))clearPending();
        intent=next;lastFrame=next.frameId();var look=next.look();if(look!=null&&look.visible()&&look.targetPlayerId()>=0){if(look.targetPlayerId()!=lockPlayer)lockStarted=now;lockPlayer=look.targetPlayerId();lockGeneration=look.targetGeneration();lockConfirmed=now;}
        return true;
    }
    /** Returns a proposal once per frame; precise attacks retain a convergence lease. */
    public Action takeAction(){
        owned();if(intent==null||intent.action().kind()==ActionKind.NONE||lastActionFrame==intent.frameId())return null;
        lastActionFrame=intent.frameId();var action=intent.action();
        if(action.kind()==ActionKind.FIRE||action.kind()==ActionKind.MELEE||action.kind()==ActionKind.ORIENTED){
            pending=action;pendingLife=intent.lifeId();pendingLook=intent.look()==null?null:intent.look().target();pendingVisible=intent.look()!=null&&intent.look().visible();pendingDeadline=intent.expiresAt()+.15;pendingPriority=intent.priority();pendingSecondary=intent.secondaryFire();pendingZoom=intent.zoom();return null;
        }
        return action;
    }
    public void expire(long life,boolean alive,double now){owned();if(intent!=null&&intent.lifeId()>=0&&intent.lifeId()!=life){intent=null;clearPending();}if(!alive||pending!=null&&(now>pendingDeadline||pendingLife>=0&&pendingLife!=life))clearPending();}
    public void clearPending(){owned();pending=null;pendingLook=null;pendingVisible=pendingSecondary=pendingZoom=false;pendingDeadline=0;pendingLife=-1;pendingPriority=Priority.ROUTINE;}
    public void clear(){owned();intent=null;clearPending();lockPlayer=-1;lockGeneration=0;lockConfirmed=lockStarted=0;lastActionFrame=lastFrame=-1;}
    public boolean commit(long life,Action action,BooleanSupplier execute){
        owned();long request=action.requestId();if(request<=0)return execute.getAsBoolean();
        if(ledgerLife!=life){ledger.clear();ledgerLife=life;highWater=0;}
        var previous=ledger.get(request);if(previous!=null)return previous;if(request<=highWater)return false;
        boolean accepted=execute.getAsBoolean();highWater=request;ledger.put(request,accepted);if(ledger.size()>64)ledger.remove(ledger.keySet().iterator().next());return accepted;
    }
    public void result(Action action,boolean accepted,double now,boolean noStock){
        owned();long request=feedback.request();String reason=feedback.reason();boolean taskAccepted=feedback.taskAccepted();double taskAt=feedback.taskAt();
        if(action.requestId()>0){request=action.requestId();reason=accepted?"accepted":action.kind()==ActionKind.DEPLOY&&noStock?"no_stock":"service_rejected";taskAccepted=accepted;taskAt=now;}
        feedback=new Feedback(action.kind().code(),accepted,action.position(),lastActionFrame,now,request,reason,taskAccepted,taskAt);
    }
    public static double facingThreshold(BotProfile profile,Action action,double distance){
        if(action.position()!=null)return Math.cos(Math.atan2(.45,Math.max(distance,.75))+.03);
        if(action.kind()==ActionKind.MELEE)return distance<=1.2?0:distance<=2.5?.72*((distance-1.2)/1.3):.72;
        double error=Math.atan2(.5,Math.max(distance,2))*(.9+.9*(1-profile.skill()))+profile.aimNoise()*1.2+.01;
        return Math.cos(Math.min(error,Math.PI/2));
    }
}
