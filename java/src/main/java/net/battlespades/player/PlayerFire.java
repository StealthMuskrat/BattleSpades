// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.function.Consumer;
import net.battlespades.combat.CombatCatalog;
import net.battlespades.combat.CombatCatalog.Profile;
import net.battlespades.protocol.*;

/** Original Player firing/reload wallet timing. Passing this gate does not authorize a tool, hit or terrain edit. */
public final class PlayerFire {
    public record Floor(int tool,long label) {}
    public record State(boolean raw,Integer beforeMachineGun,double lastShot,double nextShot,Floor autoReloadFloor,boolean donePending) {}
    private final Thread owner=Thread.currentThread();
    private final PlayerLife player;
    private boolean raw=true,donePending;
    private Integer beforeMachineGun;
    private double lastShot,nextShot;
    private Floor floor;
    private ActionClock.Link link;
    PlayerFire(PlayerLife player){this.player=Objects.requireNonNull(player);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Fire timing outside owner");}
    void resetLife(){owned();raw=true;beforeMachineGun=null;lastShot=nextShot=0;}
    public void link(ActionClock.Link value){owned();link=value;}
    public ActionClock.Link link(){owned();return link;}
    public State state(){owned();return new State(raw,beforeMachineGun,lastShot,nextShot,floor,donePending);}
    public boolean spade(){owned();return raw&&CombatCatalog.SPADES.contains(player.inventory().tool());}
    public boolean weapon(){owned();return raw?LifeCatalog.WEAPON_TOOLS.contains(player.inventory().tool()):LifeCatalog.WEAPONS.containsKey(player.inventory().weapon());}
    public Profile profile(){
        owned();int tool=player.inventory().tool();
        if(spade()){var profile=CombatCatalog.WEAPONS.get(tool);return profile!=null&&profile.melee()?profile:CombatCatalog.get(CombatCatalog.SPADE);}
        int weapon=raw&&LifeCatalog.WEAPONS.containsKey(tool)?tool:player.inventory().weapon();
        return CombatCatalog.get(LifeCatalog.WEAPONS.containsKey(weapon)?weapon:CombatCatalog.DEFAULT_WEAPON);
    }
    /** Trusted selected-tool transition; packet identity/loadout/mode/entity gates remain caller responsibilities. */
    public void tool(int value,Boolean explicitRaw){
        owned();boolean nextRaw=explicitRaw==null?CombatCatalog.RAW_TOOLS.contains(value):explicitRaw;
        var inventory=player.inventory();int previous=inventory.tool();
        if(value!=previous||raw!=nextRaw){inventory.reloadState(false,0);if(value==15&&previous!=15)beforeMachineGun=previous;}
        raw=nextRaw;inventory.tool(value,raw);
    }
    private void loadCycle(Profile profile){
        var inventory=player.inventory();var ammo=inventory.ammo();int needed=Math.max(0,profile.clip()-ammo.clip());if(profile.clipReload())needed=Math.min(1,needed);
        int loaded=Math.min(needed,ammo.reserve());inventory.ammo(ammo.clip()+loaded,ammo.reserve()-loaded);
    }
    private boolean reloadable(Profile profile){var ammo=player.inventory().ammo();return ammo.clip()<profile.clip()&&ammo.reserve()>0;}
    private void stop(){player.inventory().reloadState(false,0);floor=null;}
    public boolean advanceReload(double now){
        owned();var inventory=player.inventory();if(!inventory.reloading())return false;var profile=profile();
        while(inventory.reloading()&&now>=inventory.reloadEnd()){
            loadCycle(profile);
            if(profile.clipReload()&&profile.reload()>0&&reloadable(profile)){inventory.reloadState(true,inventory.reloadEnd()+profile.reload());continue;}
            stop();donePending=true;return true;
        }
        return false;
    }
    public void advanceAndAnnounce(double now,Consumer<GamePacket> broadcast){
        owned();if(player.inventory().reloading())advanceReload(now);
        if(donePending){donePending=false;broadcast.accept(new LifePackets.WeaponReload(player.id(),player.inventory().tool(),true));}
    }
    private static long frames(double interval){return Math.max(1,(long)Math.floor(interval*60+1e-6));}
    private boolean doneByLabel(Profile profile,Integer label){
        var inventory=player.inventory();if(floor==null||label==null||floor.tool!=inventory.tool()||(profile.clipReload()&&inventory.ammo().clip()>0)||inventory.ammo().reserve()<=0||!player.actionClock().labelPlausible(label))return false;
        return (long)label-floor.label>=frames(profile.reload())-1;
    }
    /** Source assault continuation empties a clip outside consumeShot but still dates auto reload. */
    public void noteAfterBurstRound(Integer label,double interval){owned();noteFloor(label,interval,player.bot());}
    private void noteFloor(Integer label,double interval,boolean bot){
        if(player.inventory().ammo().clip()>0||label==null||bot||!player.actionClock().labelPlausible(label)){floor=null;return;}
        floor=new Floor(player.inventory().tool(),label+frames(interval));
    }
    private boolean blocksShot(double now,boolean commit,Integer label){
        var inventory=player.inventory();if(!inventory.reloading())return false;var profile=profile();
        if(inventory.reloadEnd()-now<=CombatCatalog.RELOAD_GRACE+CombatCatalog.RELOAD_FRACTION*profile.reload()||doneByLabel(profile,label)){
            if(commit){loadCycle(profile);stop();}return false;
        }
        if(profile.clipReload()&&inventory.ammo().clip()>0){if(commit)stop();return false;}
        return true;
    }
    public boolean canFire(double now,Double overrideInterval,Integer label){
        boolean bot=player.bot();
        owned();if(!player.alive()||!player.spawned())return false;advanceReload(now);if(blocksShot(now,false,label))return false;
        double interval=overrideInterval==null?profile().interval():overrideInterval;
        if(!bot){if(!player.actionClock().peek("fire",label,interval,now,null,CombatCatalog.FIRE_GRACE,link))return false;}
        else if(now+CombatCatalog.FIRE_GRACE<nextShot)return false;
        if(spade())return true;if(!weapon())return false;
        var inventory=player.inventory();if(inventory.ammo().clip()>0)return true;
        var profile=profile();return inventory.reloading()&&inventory.ammo().reserve()>0&&(inventory.reloadEnd()-now<=CombatCatalog.RELOAD_GRACE+CombatCatalog.RELOAD_FRACTION*profile.reload()||doneByLabel(profile,label));
    }
    public boolean consumeShot(double now,Double overrideInterval,Integer label){
        boolean bot=player.bot();
        owned();if(!canFire(now,overrideInterval,label))return false;
        var inventory=player.inventory();if(weapon()){blocksShot(now,true,label);if(inventory.ammo().clip()<=0)return false;}
        double interval=overrideInterval==null?profile().interval():overrideInterval;
        if(!bot&&!player.actionClock().admit("fire",label,interval,now,null,CombatCatalog.FIRE_GRACE,link))return false;
        double previous=nextShot;lastShot=now;nextShot=previous<=0||now>=previous?now+interval:previous+interval;
        if(weapon()){var ammo=inventory.ammo();inventory.ammo(Math.max(0,ammo.clip()-1),ammo.reserve());noteFloor(label,interval,bot);}return true;
    }
    private boolean launcherReload(double now,boolean bot){
        int tool=player.inventory().tool();if(!raw||!LifeCatalog.LAUNCHERS.containsKey(tool))return false;
        Integer left=player.inventory().launcherClipLeft(tool,now,bot);if(left==null)return false;
        int clip=LifeCatalog.LAUNCHERS.get(tool).clip();var stock=player.inventory().oriented().get(tool);
        return left<clip&&(stock==null||stock>left);
    }
    public boolean startReload(double now){
        boolean bot=player.bot();
        owned();if(!player.alive()||!player.spawned()||!weapon())return false;
        if(raw&&!LifeCatalog.WEAPONS.containsKey(player.inventory().tool()))return launcherReload(now,bot);
        var profile=profile();if(player.inventory().reloading())advanceReload(now);
        if(player.inventory().reloading())return profile.clipReload();if(!reloadable(profile))return false;
        player.inventory().reloadState(true,now+profile.reload());return true;
    }
    public boolean finishReload(){owned();if(!player.inventory().reloading())return false;loadCycle(profile());stop();return true;}
}
