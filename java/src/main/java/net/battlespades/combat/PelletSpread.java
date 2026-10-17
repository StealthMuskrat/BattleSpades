// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;

/** Original seeded pellet expansion and frame-dated retail bloom; call only after shot admission. */
public final class PelletSpread {
    public record State(int tool,double spread,double lastAt,Integer label) {}
    private final Thread owner=Thread.currentThread();
    private final Map<Integer,State> states=new LinkedHashMap<>();
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Pellet state outside owner");}
    public void forgetPlayer(int id){owned();states.remove(id);}
    public Map<Integer,State> snapshot(){owned();return Collections.unmodifiableMap(new LinkedHashMap<>(states));}
    private static Double labelGap(PlayerLife p,Integer previous,Integer label){
        if(previous==null||p.bot()||!p.actionClock().labelPlausible(label))return null;
        long gap=(long)label-previous;return gap<0||gap>600?null:gap/60.0;
    }
    public List<Vector> expand(PlayerLife p,Vector direction,CombatCatalog.Profile profile,Integer label,int seed,double now){
        owned();var state=states.get(p.id());int tool=p.inventory().tool();double accuracy=profile.spread(),spread=0;
        var curve=CombatCatalog.ACCURACY.get(tool);
        if(curve!=null){
            if(state==null||state.tool!=tool)spread=curve.minimum();
            else{
                double elapsed=Math.max(0,now-state.lastAt);var gap=labelGap(p,state.label,label);if(gap!=null)elapsed=gap;
                spread=Math.max(curve.minimum(),state.spread-elapsed*curve.reduction());
            }
            double ratio=curve.maximum()>curve.minimum()?Math.min(1,Math.max(0,(spread-curve.minimum())/(curve.maximum()-curve.minimum()))):0;
            accuracy=profile.accuracyMinimum()+ratio*(profile.accuracyMaximum()-profile.accuracyMinimum());
            spread=Math.min(curve.maximum(),spread+curve.increase());
        }
        states.put(p.id(),new State(tool,spread,now,label));
        boolean zoom=p.inputTimeline().zoomForAction(label,(p.motion().snapshot().actions()&4)!=0);
        if(zoom&&CombatCatalog.ZOOM.containsKey(tool))accuracy=CombatCatalog.ZOOM.get(tool);
        double scale=zoom?2:4,center=zoom?1:2;var rng=new SourceRandom(seed&255);var pellets=new ArrayList<Vector>();
        for(int index=0;index<profile.pellets();index++){
            double x=direction.x()+(rng.random()*scale-center)*accuracy,y=direction.y()+(rng.random()*scale-center)*accuracy,z=direction.z()+(rng.random()*scale-center)*accuracy;
            double norm=Math.sqrt(x*x+y*y+z*z);if(norm>1e-6)pellets.add(new Vector(x/norm,y/norm,z/norm));
        }
        return List.copyOf(pellets);
    }
}
