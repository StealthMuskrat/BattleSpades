// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.ArrayList;
import java.util.List;

/** Protocol168 join metadata. Nested records group source fields without changing wire order. */
public record InitialInfo(Identity identity,Mode mode,MapInfo map,Features features,
        float blockWalletMultiplier,float blockHealthMultiplier,List<Integer> disabledTools,
        List<Integer> disabledClasses,List<Double> movementSpeedMultipliers,List<Integer> ugcPrefabSets,
        int enablePlayerScore,String serverName,List<Rgba> groundColors,List<Loadout> loadouts,
        int allowShootingHoldingIntel,int friendlyFire,List<Rule> customRules,int enableCorpseExplosion,int ugcMode)
        implements GamePacket {
    public InitialInfo withIdentity(Identity value){return new InitialInfo(value,mode,map,features,blockWalletMultiplier,blockHealthMultiplier,disabledTools,disabledClasses,movementSpeedMultipliers,ugcPrefabSets,enablePlayerScore,serverName,groundColors,loadouts,allowShootingHoldingIntel,friendlyFire,customRules,enableCorpseExplosion,ugcMode);}
    public record Identity(long steamId,int ip,int port,int queryPort) {
        public Identity { if(queryPort<0 || queryPort>65535) throw new IllegalArgumentException("Query port"); }
    }
    public record Mode(String name,String description,String infographic1,String infographic2,String infographic3,int key) {}
    public record MapInfo(String name,String filename,int checksum,int isUgc) {}
    public record Features(int classic,int minimap,int sameTeamCollision,int maxDrawDistance,
            int colourPicker,int colourPalette,int deathcam,int sniperBeam,int spectator,
            int exposedTeamsOnMinimap,int numericHp,String textureSkin,int beachZModifiable,
            int minimapHeightIcons,int fallOnWaterDamage) {}
    /** Ground colors are RGBA, unlike the BGR team/fog color fields. */
    public record Rgba(int red,int green,int blue,int alpha) {}
    public record Loadout(int classId,int slotId,List<Integer> tools) {
        public Loadout { tools=counted(tools,255); }
    }
    public record Rule(String name,String value) {}
    public InitialInfo {
        disabledTools=counted(disabledTools,255);disabledClasses=counted(disabledClasses,255);
        movementSpeedMultipliers=counted(movementSpeedMultipliers,255);ugcPrefabSets=counted(ugcPrefabSets,255);
        groundColors=counted(groundColors,255);loadouts=counted(loadouts,255);customRules=counted(customRules,255);
    }
    static <T> List<T> counted(List<T> list,int max) {
        if(list.size()>max) throw new IllegalArgumentException("Count exceeds wire limit: "+max);
        return List.copyOf(list);
    }
    static void writeBytes(Wire.Writer w,List<Integer> values) { w.u8(values.size());for(int i:values) w.u8(i); }
    static List<Integer> readBytes(Wire.Reader r) {
        int count=r.u8();var list=new ArrayList<Integer>(count);for(int i=0;i<count;i++) list.add(r.u8());return list;
    }
    @Override public int id() { return 114; }
    @Override public void write(Wire.Writer w) {
        w.u8(id()).u64(identity.steamId).i32(identity.ip).i32(identity.port)
                .string(mode.name).string(mode.description).string(mode.infographic1).string(mode.infographic2).string(mode.infographic3)
                .string(map.name).string(map.filename).i32(map.checksum).u8(mode.key).u8(map.isUgc).i16(identity.queryPort);
        w.u8(features.classic).u8(features.minimap).u8(features.sameTeamCollision).u8(features.maxDrawDistance)
                .u8(features.colourPicker).u8(features.colourPalette).u8(features.deathcam).u8(features.sniperBeam)
                .u8(features.spectator).u8(features.exposedTeamsOnMinimap).u8(features.numericHp).string(features.textureSkin)
                .u8(features.beachZModifiable).u8(features.minimapHeightIcons).u8(features.fallOnWaterDamage)
                .i16(Fixed.encodePosition(blockWalletMultiplier)).i16(Fixed.encodePosition(blockHealthMultiplier));
        writeBytes(w,disabledTools);writeBytes(w,disabledClasses);w.u8(movementSpeedMultipliers.size());
        for(double speed:movementSpeedMultipliers) w.i16(Fixed.encodePosition(speed));
        writeBytes(w,ugcPrefabSets);w.u8(enablePlayerScore).string(serverName).u8(groundColors.size());
        for(var c:groundColors) w.u8(c.red).u8(c.green).u8(c.blue).u8(c.alpha);
        w.u8(loadouts.size());for(var l:loadouts) {w.u8(l.classId).u8(l.slotId);writeBytes(w,l.tools);}
        w.u8(allowShootingHoldingIntel).u8(friendlyFire).u8(customRules.size());
        for(var rule:customRules) w.string(rule.name).string(rule.value);
        w.u8(enableCorpseExplosion).u8(ugcMode);
    }
    static InitialInfo read(Wire.Reader r) {
        long steam=r.u64();int ip=r.i32(),port=r.i32();
        String name=r.string(),description=r.string(),i1=r.string(),i2=r.string(),i3=r.string(),mapName=r.string(),filename=r.string();
        int crc=r.i32(),key=r.u8(),ugc=r.u8(),query=r.u16();
        var features=new Features(r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.u8(),r.string(),r.u8(),r.u8(),r.u8());
        float wallet=(float)Fixed.decodePosition(r.i16()),health=(float)Fixed.decodePosition(r.i16());
        var tools=readBytes(r);var classes=readBytes(r);int count=r.u8();var speeds=new ArrayList<Double>(count);
        for(int i=0;i<count;i++) speeds.add(Fixed.decodePosition(r.i16()));
        var sets=readBytes(r);int score=r.u8();String server=r.string();count=r.u8();var colors=new ArrayList<Rgba>(count);
        for(int i=0;i<count;i++) colors.add(new Rgba(r.u8(),r.u8(),r.u8(),r.u8()));
        count=r.u8();var loadouts=new ArrayList<Loadout>(count);
        for(int i=0;i<count;i++) loadouts.add(new Loadout(r.u8(),r.u8(),readBytes(r)));
        int shooting=r.u8(),ff=r.u8();count=r.u8();var rules=new ArrayList<Rule>(count);
        for(int i=0;i<count;i++) rules.add(new Rule(r.string(),r.string()));
        return new InitialInfo(new Identity(steam,ip,port,query),new Mode(name,description,i1,i2,i3,key),new MapInfo(mapName,filename,crc,ugc),
                features,wallet,health,tools,classes,speeds,sets,score,server,colors,loadouts,shooting,ff,rules,r.u8(),r.u8());
    }
}
