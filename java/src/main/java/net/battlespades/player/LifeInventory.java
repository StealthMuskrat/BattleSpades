// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;

/** Owned life wallets. Inventory grants are explicit; authorization/geometry and firing remain action policies. */
public final class LifeInventory {
    public record Ammo(int clip,int reserve){}
    public record LauncherUse(int left,double time){}
    private final Thread owner=Thread.currentThread();
    private final Map<Integer,Ammo> weapons=new LinkedHashMap<>();
    private final Map<Integer,Integer> oriented=new TreeMap<>(),deployable=new TreeMap<>();
    private final Map<Integer,Double> deployableDue=new TreeMap<>();
    private final Map<Integer,LauncherUse> launchers=new TreeMap<>();
    private int weapon=LifeCatalog.DEFAULT_WEAPON,tool=weapon,clip,reserve,blocks,maxBlocks,turrets,disguise;
    private boolean reloading;private double reloadEnd;
    public LifeInventory(){resetGuns();resetOriented();resetDeployables();turrets=LifeCatalog.TURRET_INITIAL;}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Inventory accessed outside owner");}
    public int weapon(){owned();return weapon;}public int tool(){owned();return tool;}public Ammo ammo(){owned();return new Ammo(clip,reserve);}
    public Map<Integer,Ammo> weapons(){owned();return Map.copyOf(weapons);}public Map<Integer,Integer> oriented(){owned();return Map.copyOf(oriented);}
    public Map<Integer,Integer> deployables(){owned();return Map.copyOf(deployable);}public Map<Integer,Double> deployableDue(){owned();return Map.copyOf(deployableDue);}
    public int blocks(){owned();return blocks;}public int maxBlocks(){owned();return maxBlocks;}public int turrets(){owned();return turrets;}public int disguise(){owned();return disguise;}
    public boolean reloading(){owned();return reloading;}public double reloadEnd(){owned();return reloadEnd;}
    public void ammo(int magazine,int remaining){owned();if(magazine<0||remaining<0)throw new IllegalArgumentException("Negative ammo");clip=magazine;reserve=remaining;}
    public void reloadState(boolean active,double end){owned();if(!Double.isFinite(end))throw new IllegalArgumentException("Reload clock");reloading=active;reloadEnd=end;}
    public void tool(int value){
        owned();if(value!=tool){reloading=false;reloadEnd=0;}tool=value;
        if(LifeCatalog.WEAPONS.containsKey(value)&&value!=weapon){weapons.put(weapon,new Ammo(clip,reserve));weapon=value;var ammo=weapons.get(value);clip=ammo.clip();reserve=ammo.reserve();}
        if(!LifeCatalog.WEAPON_TOOLS.contains(value)){reloading=false;reloadEnd=0;}
    }
    public void classWallet(int id,double multiplier,boolean reset){
        owned();if(!Double.isFinite(multiplier)||multiplier<0)throw new IllegalArgumentException("Wallet scale");var profile=Objects.requireNonNull(LifeCatalog.BLOCKS.get(id),"Unknown class wallet");
        double maximum=Math.rint(profile.maximum()*multiplier),initial=Math.rint(profile.start()*multiplier);if(maximum>Integer.MAX_VALUE||initial>Integer.MAX_VALUE)throw new IllegalArgumentException("Wallet overflow");
        maxBlocks=Math.max(0,(int)maximum);blocks=reset?Math.min(maxBlocks,Math.max(0,(int)initial)):Math.min(blocks,maxBlocks);
    }
    public void spawn(int classId,double walletScale){owned();classWallet(classId,walletScale,true);resetGuns();resetOriented();resetDeployables();turrets=LifeCatalog.TURRET_INITIAL;reloading=false;reloadEnd=0;tool=weapon;}
    private void resetGuns(){weapons.clear();LifeCatalog.WEAPONS.forEach((id,p)->weapons.put(id,new Ammo(p.clip(),p.initial())));var p=LifeCatalog.WEAPONS.getOrDefault(weapon,LifeCatalog.WEAPONS.values().iterator().next());clip=p.clip();reserve=p.initial();}
    private void resetOriented(){oriented.clear();LifeCatalog.ORIENTED.forEach((id,p)->oriented.put(id,p.magazineInitial()+p.reserveInitial()));launchers.clear();disguise=LifeCatalog.DISGUISE_INITIAL;}
    private void resetDeployables(){deployable.clear();LifeCatalog.DEPLOYABLES.forEach((id,p)->deployable.put(id,p.initial()));deployableDue.clear();}
    public void oriented(int toolId,int count){owned();if(!LifeCatalog.ORIENTED.containsKey(toolId)||count<0)throw new IllegalArgumentException("Oriented wallet");oriented.put(toolId,count);}
    public void launcherUse(int toolId,int left,double now){owned();if(!Double.isFinite(now)||left<0)throw new IllegalArgumentException("Launcher state");launchers.put(toolId,new LauncherUse(left,now));}
    private Integer clipLeft(int id,double now,boolean bot){
        var p=LifeCatalog.LAUNCHERS.get(id);if(bot||p==null||p.clip()<=0||p.reload()<=0)return null;var state=launchers.get(id);if(state==null)return p.clip();
        double gap=now-state.time(),interval=Math.max(0,p.interval()),reloadGap=Math.max(interval,interval+p.reload()-(0.25+0.1*p.reload()));
        if(gap<reloadGap)return state.left();if(p.cycles()){int cycles=1+(int)Math.floor((gap-reloadGap)/Math.max(p.reload(),1e-6));return Math.min(p.clip(),state.left()+cycles);}return p.clip();
    }
    public void restock(int type,double now,boolean bot){
        owned();int disguiseBefore=disguise;
        if(type==3){
            weapons.replaceAll((id,a)->{var p=LifeCatalog.WEAPONS.get(id);return new Ammo(a.clip(),Math.min(a.reserve()+(p.restock()<0?p.reserve():p.restock()),p.reserve()));});
            var p=LifeCatalog.WEAPONS.get(weapon);reserve=Math.min(reserve+(p.restock()<0?p.reserve():p.restock()),p.reserve());weapons.put(weapon,new Ammo(clip,reserve));
            LifeCatalog.ORIENTED.forEach((id,stock)->{int total=Math.max(0,oriented.getOrDefault(id,0));if(stock.reserveMax()<=0){oriented.put(id,Math.min(total+stock.restock(),stock.magazineMax()));return;}Integer left=clipLeft(id,now,bot);int magazine=Math.min(total,left==null?stock.magazineMax():left);oriented.put(id,magazine+Math.min(total-magazine+stock.restock(),stock.reserveMax()));});
        }else {resetGuns();resetOriented();}
        turrets=Math.min(LifeCatalog.TURRET_MAX,turrets+LifeCatalog.TURRET_RESTOCK);
        if(type!=0){LifeCatalog.DEPLOYABLES.forEach((id,p)->deployable.put(id,Math.min(p.maximum(),Math.max(0,deployable.getOrDefault(id,0))+p.restock())));disguise=Math.min(LifeCatalog.DISGUISE_MAX,disguiseBefore+LifeCatalog.DISGUISE_RESTOCK);}
    }
    public boolean removeBlock(){owned();if(blocks<=0)return false;blocks--;return true;}
    public void addBlocks(int count){owned();if(count<0)throw new IllegalArgumentException("Negative block grant");blocks=(int)Math.min(maxBlocks,(long)blocks+count);}
    public boolean deployableReady(int toolId,double now){owned();return LifeCatalog.CADENCE.containsKey(toolId)&&Double.isFinite(now)&&now+0.05>=deployableDue.getOrDefault(toolId,0.0)&&rawDeployableStock(toolId)>0;}
    public int rawDeployableStock(int toolId){owned();var rule=LifeCatalog.DEPLOYABLES.get(toolId);if(rule!=null)return Math.min(rule.maximum(),Math.max(0,deployable.getOrDefault(toolId,0)));if(toolId==16)return Math.min(LifeCatalog.TURRET_MAX,Math.max(0,turrets));if(toolId==22)return Math.max(0,disguise);return toolId==15?1:0;}
    public void commitDeployable(int toolId,double now){owned();if(!deployableReady(toolId,now))throw new IllegalStateException("Deployable preflight not ready");if(LifeCatalog.DEPLOYABLES.containsKey(toolId))deployable.put(toolId,deployable.get(toolId)-1);deployableDue.put(toolId,Math.max(now,deployableDue.getOrDefault(toolId,0.0))+LifeCatalog.CADENCE.get(toolId));}
}
