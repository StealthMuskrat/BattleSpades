// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.scene;

import java.util.*;
import net.battlespades.player.ClassCatalog;
import net.battlespades.protocol.*;

/** Common builders from server/builders. Active mode amendments remain an explicit caller hook. */
public final class ScenePackets {
    public record Environment(String filename,int sourceCrc,double gravity,Integer fogColor,Integer lightColor,
                              StateData.Vector lightDirection,Integer backColor,StateData.Vector backDirection,
                              Integer ambientColor,Double ambientIntensity,List<InitialInfo.Rgba> groundColors,String skybox){
        public Environment {groundColors=List.copyOf(groundColors);gravity=canonicalGravity(gravity);}
        public static Environment defaults(String filename,int crc){return new Environment(filename,crc,1,null,null,null,null,null,null,null,List.of(),null);}
    }
    public record Team(String name,int color,int score){}
    public record State(Team team1,Team team2,Integer activeScoreLimit,Integer fogOverride){
        public static State initial(SceneConfig c){return new State(new Team(c.team1().name(),c.team1().color(),0),new Team(c.team2().name(),c.team2().color(),0),null,null);}
    }
    public record ServerIdentity(long registeredSteamId,long publicIp,boolean queryActive,int queryPort){}
    private ScenePackets(){}
    /** map_metadata.canonical_gravity: authority consumes the same 1/64 value as StateData. */
    public static double canonicalGravity(double value){if(!Double.isFinite(value)||value<=0||value>8)value=1;return Math.floor(value*64+0.5)/64;}
    private static int flag(boolean b){return b?1:0;}
    public static InitialInfo initial(SceneConfig c,Environment e){
        return initial(c,e,new ServerIdentity(0,0,false,c.port()));
    }
    public static InitialInfo initial(SceneConfig c,Environment e,ServerIdentity identity){
        return finishInitial(c,baseInitial(c,e,identity));
    }
    static InitialInfo baseInitial(SceneConfig c,Environment e,ServerIdentity identity){
        var r=c.rules();var m=c.mode();var allowed=new HashSet<>(m.classes());var disabled=new TreeSet<Integer>();
        if(!allowed.isEmpty()){disabled.addAll(ClassCatalog.definitions().keySet());disabled.removeAll(allowed);disabled.addAll(r.disabledClasses());}
        var speeds=new ArrayList<Double>();for(int id=0;id<18;id++)speeds.add(ClassCatalog.speedScale(id,m.code(),r));
        String filename=e.filename()==null||e.filename().isEmpty()?c.mapName():e.filename();if(filename==null||filename.isEmpty())filename="MayanJungle";
        var colors=e.groundColors().isEmpty()?List.of(new InitialInfo.Rgba(59,58,55,238),new InitialInfo.Rgba(40,54,64,239)):e.groundColors();
        return new InitialInfo(new InitialInfo.Identity(identity.registeredSteamId()!=0?identity.registeredSteamId():c.steamId(),(int)identity.publicIp(),c.port(),identity.queryActive()?identity.queryPort():c.port()),new InitialInfo.Mode(m.title(),m.description(),m.info().get(0),m.info().get(1),m.info().get(2),m.id()),new InitialInfo.MapInfo(displayName(filename),filename,e.sourceCrc(),0),
            new InitialInfo.Features(flag(m.classic()),flag(r.enabled("RULE_ENABLE_MINI_MAP")&&!m.classic()),flag(c.sameTeamCollision()),192,flag(r.enabled("RULE_ENABLE_COLOUR_PICKER")),flag(r.enabled("RULE_ENABLE_COLOUR_PICKER")),flag(r.enabled("RULE_ENABLE_DEATH_CAM")),flag(r.enabled("RULE_ENABLE_SNIPER_BEAM")),flag(r.enabled("RULE_ENABLE_SPECTATORS")),0,1,m.mafia()?"mafia":"",1,0,flag(r.enabled("RULE_ENABLE_FALL_ON_WATER_DAMAGE"))),
            (float)r.number("RULE_CHARACTER_BLOCK_WALLETS"),(float)r.number("RULE_BLOCK_HEALTH"),r.disabledTools(),List.copyOf(disabled),speeds,List.of(),1,c.name(),colors,List.of(),flag(r.enabled("RULE_CTF_ENABLE_SHOOT_WITH_INTEL")),flag(c.friendlyFire()),List.of(),flag(r.enabled("RULE_ENABLE_CORPSE_EXPLOSION")),m.id());
    }
    public static StateData state(SceneConfig c,Environment e,State state,int playerId){
        return finishState(c,baseState(c,e,state,playerId));
    }
    static StateData baseState(SceneConfig c,Environment e,State state,int playerId){
        var m=c.mode();int score=state.activeScoreLimit()!=null?state.activeScoreLimit():c.scoreLimit()!=0?c.scoreLimit():m.scoreLimit();
        var classes=m.classes();boolean lockedClass=m.code().equals("vip");
        var lighting=new StateData.Lighting(e.lightColor()!=null?e.lightColor():0xb4c0dc,e.lightDirection()!=null?e.lightDirection():new StateData.Vector(0.203125,0.796875,0),e.backColor()!=null?e.backColor():0x404040,e.backDirection()!=null?e.backDirection():new StateData.Vector(-0.078125,-0.578125,0.296875),e.ambientColor()!=null?e.ambientColor():0x343840,(float)(e.ambientIntensity()!=null?e.ambientIntensity():0.203125));
        return new StateData(Math.max(0,playerId),state.fogOverride()!=null?state.fogOverride():e.fogColor()!=null?e.fogColor():c.fogColor(),(float)e.gravity(),lighting,1,score,m.id(),6,team(state.team1(),lockedClass,classes),team(state.team2(),lockedClass,classes),false,false,List.of(),List.of(),List.of(new StateData.Vector(0,0,0)),List.of(new StateData.Vector(0,0,0)),0);
    }
    private static StateData.Team team(Team t,boolean lockedClass,List<Integer> classes){return new StateData.Team(t.name(),t.color(),t.score(),false,false,true,true,false,lockedClass,false,classes);}
    /** Source appends the gravestone extension after the active mode's amendment. */
    public static InitialInfo finishInitial(SceneConfig config,InitialInfo p){
        var rules=new ArrayList<>(p.customRules().stream().filter(r->!r.name().equals("RULE_ENABLE_GRAVESTONES")).toList());
        rules.add(new InitialInfo.Rule("RULE_ENABLE_GRAVESTONES",config.rules().enabled("RULE_ENABLE_GRAVESTONES")?"1":"0"));
        return new InitialInfo(p.identity(),p.mode(),p.map(),p.features(),p.blockWalletMultiplier(),p.blockHealthMultiplier(),p.disabledTools(),p.disabledClasses(),p.movementSpeedMultipliers(),p.ugcPrefabSets(),p.enablePlayerScore(),p.serverName(),p.groundColors(),p.loadouts(),p.allowShootingHoldingIntel(),p.friendlyFire(),rules,p.enableCorpseExplosion(),p.ugcMode());
    }
    /** Source filters rule-disabled classes after the active mode's team-menu amendment. */
    public static StateData finishState(SceneConfig config,StateData p){
        return new StateData(p.playerId(),p.fogColor(),p.gravity(),p.lighting(),p.timeScale(),p.scoreLimit(),p.modeType(),p.teamHeadcountType(),filterTeam(config,p.team1()),filterTeam(config,p.team2()),p.lockTeamSwap(),p.lockSpectatorSwap(),p.prefabs(),p.entities(),p.cameraPoints(),p.cameraRotations(),p.hasMapEnded());
    }
    private static StateData.Team filterTeam(SceneConfig config,StateData.Team t){return new StateData.Team(t.name(),t.color(),t.score(),t.locked(),t.canSeeOther(),t.showScore(),t.showMaxScore(),t.infiniteBlocks(),t.lockedClass(),t.lockedScore(),t.classes().stream().filter(config.rules()::classEnabled).toList());}
    public static String skybox(SceneConfig c,Environment e){String name=SceneConfig.validSkybox(e.skybox());return name!=null?name:SceneConfig.normalizeSkybox(c.skybox());}
    public static String displayName(String filename){
        return switch(filename){case "ArcticBase"->"Arctic Base";case "CastleWars"->"Castle Wars";case "CityOfChicago"->"City of Chicago";case "20thCenturyTown"->"WW1";default->filename.replaceAll("(?<=[a-z0-9])(?=[A-Z])"," ");};
    }
}
