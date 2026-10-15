// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.ArrayList;
import java.util.List;

/** Initial world/team state; entity and prefab counts are signed 16-bit, not bytes. */
public record StateData(int playerId,int fogColor,float gravity,Lighting lighting,float timeScale,
        int scoreLimit,int modeType,int teamHeadcountType,Team team1,Team team2,
        boolean lockTeamSwap,boolean lockSpectatorSwap,List<String> prefabs,List<EntityRecord> entities,
        List<Vector> cameraPoints,List<Vector> cameraRotations,int hasMapEnded) implements GamePacket {
    /** Source tuples retain Python double precision before fixed-point encoding. Wire order is ZYX. */
    public record Vector(double x,double y,double z) {
        void write(Wire.Writer w) { w.i16(Fixed.encodePosition(z)).i16(Fixed.encodePosition(y)).i16(Fixed.encodePosition(x)); }
        static Vector read(Wire.Reader r) {
            double z=Fixed.decodePosition(r.i16()),y=Fixed.decodePosition(r.i16()),x=Fixed.decodePosition(r.i16());return new Vector(x,y,z);
        }
    }
    public record Lighting(int color,Vector direction,int backColor,Vector backDirection,int ambientColor,float ambientIntensity) {}
    public record Team(String name,int color,int score,boolean locked,boolean canSeeOther,boolean showScore,
            boolean showMaxScore,boolean infiniteBlocks,boolean lockedClass,boolean lockedScore,List<Integer> classes) {
        public Team { classes=InitialInfo.counted(classes,255); }
        void write(Wire.Writer w) {
            int flags=(locked?1:0)|(canSeeOther?2:0)|(showScore?4:0)|(showMaxScore?8:0)|(infiniteBlocks?16:0)|(lockedClass?32:0)|(lockedScore?64:0);
            w.string(name).color(color).i32(score).u8(flags);InitialInfo.writeBytes(w,classes);
        }
        static Team read(Wire.Reader r) {
            String name=r.string();int color=r.color(),score=r.i32(),flags=r.u8();
            return new Team(name,color,score,(flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0,(flags&16)!=0,(flags&32)!=0,(flags&64)!=0,InitialInfo.readBytes(r));
        }
    }
    public StateData {
        prefabs=InitialInfo.counted(prefabs,32767);entities=InitialInfo.counted(entities,32767);
        cameraPoints=InitialInfo.counted(cameraPoints,255);cameraRotations=InitialInfo.counted(cameraRotations,255);
    }
    @Override public int id() { return 45; }
    @Override public void write(Wire.Writer w) {
        w.u8(id()).u8(playerId).color(fogColor).i16(Fixed.encodePosition(gravity)).color(lighting.color);
        lighting.direction.write(w);w.color(lighting.backColor);lighting.backDirection.write(w);
        w.color(lighting.ambientColor).i16(Fixed.encodePosition(lighting.ambientIntensity)).i16(Fixed.encodePosition(timeScale))
                .u8(scoreLimit).u8(modeType).u8(teamHeadcountType);
        team1.write(w);team2.write(w);w.u8((lockTeamSwap?1:0)|(lockSpectatorSwap?2:0)).i16(prefabs.size());
        for(String prefab:prefabs) w.string(prefab);
        w.i16(entities.size());for(var entity:entities) entity.write(w);
        w.u8(cameraPoints.size());for(var point:cameraPoints) point.write(w);
        w.u8(cameraRotations.size());for(var rotation:cameraRotations) rotation.write(w);
        w.u8(hasMapEnded);
    }
    static StateData read(Wire.Reader r) {
        int player=r.u8(),fog=r.color();float gravity=(float)Fixed.decodePosition(r.i16());
        var light=new Lighting(r.color(),Vector.read(r),r.color(),Vector.read(r),r.color(),(float)Fixed.decodePosition(r.i16()));
        float time=(float)Fixed.decodePosition(r.i16());int limit=r.u8(),mode=r.u8(),headcount=r.u8();
        var t1=Team.read(r);var t2=Team.read(r);int flags=r.u8(),count=r.i16();
        if(count<0 || count>r.remaining()) throw new ProtocolException("Invalid prefab count");
        var prefabs=new ArrayList<String>(count);for(int i=0;i<count;i++) prefabs.add(r.string());
        count=r.i16();if(count<0 || (long)count*33>r.remaining()) throw new ProtocolException("Invalid entity count");
        var entities=new ArrayList<EntityRecord>(count);for(int i=0;i<count;i++) entities.add(EntityRecord.read(r));
        count=r.u8();var points=new ArrayList<Vector>(count);for(int i=0;i<count;i++) points.add(Vector.read(r));
        count=r.u8();var rotations=new ArrayList<Vector>(count);for(int i=0;i<count;i++) rotations.add(Vector.read(r));
        return new StateData(player,fog,gravity,light,time,limit,mode,headcount,t1,t2,(flags&1)!=0,(flags&2)!=0,prefabs,entities,points,rotations,r.u8());
    }
}
