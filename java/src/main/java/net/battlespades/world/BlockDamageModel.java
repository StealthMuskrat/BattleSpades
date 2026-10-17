// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.SourceRandom;

/** Original retail terrain footprints. Expansion consumes draws for air and out-of-map cells too. */
public final class BlockDamageModel {
    private BlockDamageModel() {}
    public record Cell(int x,int y,int z) {}
    public record Hit(Cell cell,double damage) {}
    public enum Kind { SINGLE,COLUMN,MACHETE,CUBE,RADIUS,NONE }
    public record Footprint(Kind kind,double parameter) {}
    private record Offset(int x,int y,int z,int squared) {}
    private static final Map<Integer,Footprint> FOOTPRINTS;
    private static final Map<Integer,List<Offset>> RADII;
    private static final double RADIUS_EXTRA;
    public static final int MAX_DAMAGEABLE_Z;
    static {
        var catalog=SourceCatalog.load("block-damage");var settings=catalog.getArray("settings").getTable(0);
        MAX_DAMAGEABLE_Z=Math.toIntExact(settings.getLong("max_damageable_z"));RADIUS_EXTRA=((Number)settings.get("radius_extra")).doubleValue();
        var entries=new LinkedHashMap<Integer,Footprint>();var radii=new HashMap<Integer,List<Offset>>();var rows=catalog.getArray("footprint");
        for(int i=0;i<rows.size();i++){
            var row=rows.getTable(i);var entry=new Footprint(Kind.valueOf(row.getString("kind").toUpperCase(Locale.ROOT)),((Number)row.get("parameter")).doubleValue());entries.put(Math.toIntExact(row.getLong("id")),entry);
            if(entry.kind==Kind.RADIUS)radii.computeIfAbsent((int)entry.parameter,radius->{var offsets=new ArrayList<Offset>();for(int z=-radius;z<=radius;z++)for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++){int squared=x*x+y*y+z*z;if(squared<radius*radius)offsets.add(new Offset(x,y,z,squared));}return List.copyOf(offsets);});
        }
        FOOTPRINTS=Map.copyOf(entries);RADII=Map.copyOf(radii);
    }
    public static Footprint kind(int type){return FOOTPRINTS.get(type);}
    public static boolean nativeType(int type){var entry=kind(type);return entry!=null&&entry.kind!=Kind.NONE;}
    public static double ceil4(double amount){double rounded=Math.ceil(amount*4)/4;return rounded==0?0:rounded;}
    public static double wireDamage(double amount){if(!Double.isFinite(amount))throw new IllegalArgumentException("Non-finite block damage");return Math.max(0,Math.min(255,Math.floor(amount*4+.5)))/4;}
    /** Position is a decoded protocol coordinate. */
    public static Cell center(Vector position){return new Cell(center(position.x()),center(position.y()),center(position.z()));}
    private static int center(double value){double coordinate=Math.floor(value+.5);if(!Double.isFinite(coordinate)||coordinate<Integer.MIN_VALUE||coordinate>Integer.MAX_VALUE)throw new IllegalArgumentException("Block coordinate outside integer range");return (int)coordinate;}
    public static List<Hit> footprint(int type,Vector position,double wireAmount,int seed){
        var entry=kind(type);if(entry==null||entry.kind==Kind.NONE)return List.of();var c=center(position);var out=new ArrayList<Hit>();
        switch(entry.kind){
            case SINGLE->out.add(new Hit(c,wireAmount));
            case COLUMN->{for(int z=-1;z<=1;z++)out.add(new Hit(new Cell(c.x,c.y,c.z+z),wireAmount));}
            case MACHETE->{for(int z=0;z<=1;z++)out.add(new Hit(new Cell(c.x,c.y,c.z+z),wireAmount));}
            case CUBE->{var random=new SourceRandom(seed&255);for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)out.add(new Hit(new Cell(c.x+x,c.y+y,c.z+z),ceil4(wireAmount+entry.parameter*random.random())));}
            case RADIUS->{var random=new SourceRandom(seed&255);int radius=(int)entry.parameter;double limit=radius*radius;for(var d:RADII.get(radius))out.add(new Hit(new Cell(c.x+d.x,c.y+d.y,c.z+d.z),ceil4(wireAmount*(1-d.squared/limit)+RADIUS_EXTRA*random.random())));}
            case NONE->throw new AssertionError("Filtered empty footprint");
        }
        return List.copyOf(out);
    }
    public static boolean damageable(Hit hit){var c=hit.cell;return c.x>=0&&c.x<512&&c.y>=0&&c.y<512&&c.z>=0&&c.z<=MAX_DAMAGEABLE_Z&&hit.damage>0;}
    public static long retailRound(double value){double magnitude=Math.floor(Math.abs(value)+.5);if(!Double.isFinite(magnitude)||magnitude>Long.MAX_VALUE)throw new IllegalArgumentException("Damage rounding range");return value>=0?(long)magnitude:-(long)magnitude;}
    public static int dim(int component,double damage){long value=component-((component*retailRound(damage))>>3);return (int)Math.max(0,value);}
    public static int dimRgb(int rgb,double damage){return ((dim((rgb>>>16)&255,damage)&255)<<16)|((dim((rgb>>>8)&255,damage)&255)<<8)|(dim(rgb&255,damage)&255);}
}
