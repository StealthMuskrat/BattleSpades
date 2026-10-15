// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.player.NameUnicode;
import net.battlespades.player.SpawnGeometry;
import net.battlespades.player.SpawnGeometry.*;
import net.battlespades.protocol.InitialInfo;
import net.battlespades.protocol.StateData;
import net.battlespades.scene.*;
import org.tomlj.TomlTable;

/** Inert map-owned data, translated before a world/scene is made visible. This does not implement mode objectives. */
public record MapMetadata(Path source,boolean officialMap,String vxlFormat,String activeMode,Map<String,Object> unofficial,
                          ScenePackets.Environment environment,SpawnGeometry.Layout spawnLayout,
                          Map<Integer,List<Position>> spawnPoints,List<Zone> neutralBases,Zone occupationBase,
                          List<Position> occupationBombs,List<Zone> diamondBases,List<BigInteger> diamondCapacities,
                          Map<Integer,BigInteger> baseMinDestruction,List<Entity> entities,List<Ambient> ambientSounds,
                          Map<Integer,Integer> staticLightColors,List<Achievement> achievementRegions,
                          Map<String,String> layoutSources,String displayName,BigInteger capLimit,Double timeLimit,
                          List<String> retailModes,List<String> retailPlaylistModes,BigInteger retailMaxPlayers,List<String> diagnostics) {
    public record Entity(int type,String kind,Position position,String item,Integer color){}
    public record Ambient(String name,List<StateData.Vector> points,double volume,double attenuation){public Ambient{points=List.copyOf(points);}}
    public record Achievement(String apiName,BigInteger kind,List<Double> bounds,Integer team,BigInteger kills,BigInteger weapon){public Achievement{bounds=List.copyOf(bounds);}}
    public MapMetadata {
        unofficial=freezeMap(unofficial);spawnPoints=freezeLists(spawnPoints);neutralBases=List.copyOf(neutralBases);occupationBombs=List.copyOf(occupationBombs);diamondBases=List.copyOf(diamondBases);diamondCapacities=List.copyOf(diamondCapacities);baseMinDestruction=Map.copyOf(baseMinDestruction);entities=List.copyOf(entities);ambientSounds=List.copyOf(ambientSounds);staticLightColors=Map.copyOf(staticLightColors);achievementRegions=List.copyOf(achievementRegions);layoutSources=Map.copyOf(layoutSources);retailModes=List.copyOf(retailModes);retailPlaylistModes=List.copyOf(retailPlaylistModes);diagnostics=List.copyOf(diagnostics);
    }
    private record Stock(String skybox,double gravity,String ambient){}
    private record Sky(Integer fog,String ambient){}
    private record Ugc(int team,List<Integer> extents){}
    private static final Map<String,Stock> STOCK=new HashMap<>();
    private static final Map<String,Sky> SKY=new HashMap<>();
    private static final Map<String,Map<Integer,Region>> REGIONS=new HashMap<>();
    private static final Map<String,Ugc> UGC=new HashMap<>();
    private static final Map<String,String> PLAYLISTS=new HashMap<>();
    private static final Set<String> LEGACY_KEYS,AMBIENT_ASSETS;
    static {
        var catalog=SourceCatalog.load("map-metadata");var settings=catalog.getArray("settings").getTable(0);
        LEGACY_KEYS=Set.copyOf(SourceCatalog.strings(settings.getArray("legacy_keys")));AMBIENT_ASSETS=Set.copyOf(SourceCatalog.strings(settings.getArray("ambient_assets")));
        for(var r:tables(catalog,"stock"))STOCK.put(r.getString("name"),new Stock(r.getString("skybox"),((Number)r.get("gravity")).doubleValue(),r.getString("ambient")));
        for(var r:tables(catalog,"sky")){var c=SourceCatalog.integers(r.getArray("fog"));SKY.put(r.getString("name"),new Sky(c.get(0)<<16|c.get(1)<<8|c.get(2),r.getString("ambient")));}
        for(var r:tables(catalog,"region")){var b=SourceCatalog.integers(r.getArray("bounds"));REGIONS.computeIfAbsent(r.getString("name"),k->new HashMap<>()).put(Math.toIntExact(r.getLong("team")),new Region(b.get(0),b.get(1),b.get(2),b.get(3)));}
        for(var r:tables(catalog,"zone"))UGC.put(r.getString("item"),new Ugc(Math.toIntExact(r.getLong("team")),SourceCatalog.integers(r.getArray("extents"))));
        for(var r:tables(catalog,"playlist"))PLAYLISTS.put(r.getString("mode"),r.getString("name"));
    }
    private static List<TomlTable> tables(TomlTable t,String key){var a=t.getArray(key);var out=new ArrayList<TomlTable>();for(int i=0;i<a.size();i++)out.add(a.getTable(i));return out;}
    public static String canonicalMode(String mode){String value=mode==null?"":NameUnicode.strip(mode).toLowerCase(Locale.ROOT);var code=ModeCatalog.get(value).code();if(code.startsWith("hc_"))code=code.substring(3);return code.equals("nor")&&!value.equals("nor")?value:code;}
    private static boolean applies(Object value,String active){String code=truth(value)?NameUnicode.strip(str(value)).toLowerCase(Locale.ROOT):"nor";return Set.of("","nor","all","any").contains(code)||canonicalMode(code).equals(active);}
    public static List<Path> sidecars(Path map){String name=map.getFileName().toString();int dot=name.lastIndexOf('.');String stem=dot>0?name.substring(0,dot):name;return List.of(map.resolveSibling(stem+".json"),map.resolveSibling(stem+".ugc"),map.resolveSibling(stem+".txt"),map.resolveSibling(name+".json"));}
    public static MapMetadata load(Path map,String activeMode){return load(map,activeMode,0);}
    public static MapMetadata load(Path map,String activeMode,int sourceCrc){
        String mode=canonicalMode(activeMode),filename=map.getFileName().toString();int dot=filename.lastIndexOf('.');String stem=dot>0?filename.substring(0,dot):filename,key=NameUnicode.casefold(stem);
        var b=new Builder();var payload=new LinkedHashMap<String,Object>();var sources=new HashMap<String,Path>();
        for(Path candidate:sidecars(map)){
            if(!Files.isRegularFile(candidate))continue;
            var data=read(candidate,b.diagnostics,true);if(data==null)continue;if(b.source==null)b.source=candidate;
            data.forEach((k,v)->{if(!payload.containsKey(k)){payload.put(k,v);sources.put(k,candidate);}});
        }
        Stock stock=STOCK.get(key);boolean official=stock!=null;String skybox=null;
        for(String field:List.of("skybox_name","skybox_texture","skybox"))if(payload.containsKey(field)){skybox=validSky(payload.get(field));break;}
        if(skybox==null&&stock!=null)skybox=stock.skybox();if(skybox==null&&SKY.containsKey(stem+".txt"))skybox=stem+".txt";
        Integer fog=rgb(payload.get("fog_color"));Sky sky=SKY.get(skybox);
        if(fog==null&&sky!=null)fog=sky.fog();else if(sky!=null&&suffix(sources.get("skybox_name")).equals(".ugc")&&suffix(sources.get("fog_color")).equals(".txt"))fog=sky.fog();
        String format=str(payload.getOrDefault("vxl_format",official?"retail":"auto")).toLowerCase(Locale.ROOT);if(!Set.of("auto","retail","classic64").contains(format))throw new IllegalArgumentException("vxl_format must be auto, retail or classic64");
        double gravity=ScenePackets.canonicalGravity(number(payload.getOrDefault("gravity",stock==null?1.0:stock.gravity()),1));
        Double intensity=optionalNumber(payload.get("ambient_light_intensity"));if(intensity!=null&&!(intensity>=0&&intensity<=4))intensity=null;
        var colors=new ArrayList<InitialInfo.Rgba>();var palette=list(payload.get("ground_colors"));for(Object row:palette.subList(0,Math.min(32,palette.size()))){var rgba=components(row,4,true);if(rgba!=null&&list(row).size()==4)colors.add(new InitialInfo.Rgba(rgba[0],rgba[1],rgba[2],rgba[3]));}
        var env=new ScenePackets.Environment(stem,sourceCrc,gravity,fog,rgb(payload.get("light_color")),vector(payload.get("light_direction")),rgb(payload.get("back_light_color")),vector(payload.get("back_light_direction")),rgb(payload.get("ambient_light_color")),intensity,colors,skybox);
        for(int i=0;i<2;i++){Integer c=rgb(payload.get("static_light_color"+i));if(c!=null)b.staticColors.put(i,c);}
        parseAmbients(b,payload.get("ambient_sounds"));if(b.ambient.isEmpty()){String name=stock==null?"":stock.ambient();if(name.isEmpty()&&sky!=null)name=sky.ambient();b.ambient.add(new Ambient(name.isEmpty()?"amb_rural":name,List.of(),1,0));}
        if(nonFinite(payload))b.diagnostics.add("Non-finite metadata values use safe defaults; non-finite placements are omitted");
        legacy(b,payload);applyMode(b,payload,mode);ugc(b,payload.get("ugc_entities"),mode);
        String display=payload.get("name") instanceof String s?truncate(NameUnicode.strip(s),64):null;
        BigInteger cap=integer(payload.get("cap_limit"));if(cap!=null)cap=cap.max(BigInteger.ONE);
        Double time=optionalNumber(payload.get("time_limit"));if(time!=null&&(!Double.isFinite(time)||time<=0))time=null;
        Map<String,Object> catalogue=retailCatalogue(map.getParent()==null?Path.of(""):map.getParent()).getOrDefault(key,Map.of());
        String retailCode=mode.equals("cctf")?"ctf":mode;if(strings(catalogue.get("invalid_modes")).contains(retailCode))b.diagnostics.add("Retail catalogue lists "+stem+" as invalid for mode "+mode);
        var regions=REGIONS.getOrDefault(key,SpawnGeometry.Layout.defaults().regions());
        return new MapMetadata(b.source,official,format,mode,map(payload.get("unofficial")),env,new SpawnGeometry.Layout(b.spawns,b.bases,regions),b.points,b.neutral,b.occupation,b.bombs,b.diamonds,b.capacities,b.minimum,b.entities,b.ambient,b.staticColors,b.achievements,b.layoutSources,display,cap,time,strings(catalogue.get("valid_modes")),strings(catalogue.get("playlist_modes")),integer(catalogue.get("max_players")),b.diagnostics);
    }
    private static String suffix(Path p){if(p==null)return "";String s=p.getFileName().toString();int dot=s.lastIndexOf('.');return dot<0?"":s.substring(dot).toLowerCase(Locale.ROOT);}
    private static Map<String,Object> read(Path path,List<String> diagnostics,boolean legacy){
        try {
            if(Files.size(path)>MetadataDocument.MAX_BYTES)throw new IOException("metadata exceeds 8 MiB");
            byte[] bytes;try(var input=Files.newInputStream(path)){bytes=input.readNBytes(MetadataDocument.MAX_BYTES+1);}if(bytes.length>MetadataDocument.MAX_BYTES)throw new IOException("metadata exceeds 8 MiB");
            String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            Map<String,Object> data=legacy?MetadataDocument.sidecar(text,LEGACY_KEYS):mapOrNull(MetadataDocument.json(text));
            if(data==null)diagnostics.add("Ignoring invalid map metadata "+path);return data;
        }catch(IOException|IllegalArgumentException e){diagnostics.add("Ignoring unreadable map metadata "+path+": "+e.getMessage());return null;}
    }
    public static Map<String,Map<String,Object>> retailCatalogue(Path mapsDir){
        Path path=mapsDir.resolve("retail_map_info.json");if(!Files.isRegularFile(path))return Map.of();var doc=read(path,new ArrayList<>(),false);if(doc==null)return Map.of();var rows=map(doc.get("maps"));var result=new LinkedHashMap<String,Map<String,Object>>();rows.forEach((name,value)->{var entry=mapOrNull(value);if(entry!=null)result.put(NameUnicode.casefold(name),freezeMap(entry));});return Collections.unmodifiableMap(result);
    }
    public static List<String> retailModePool(Path mapsDir,String requested){
        String mode=canonicalMode(requested),playlist=PLAYLISTS.get(mode);if(playlist==null)return List.of();
        var doc=read(mapsDir.resolve("retail_map_info.json"),new ArrayList<>(),false);if(doc==null)return List.of();var spec=mapOrNull(map(doc.get("playlists")).get(playlist));var rows=mapOrNull(doc.get("maps"));if(spec==null||rows==null)return List.of();
        var names=new HashMap<String,String>();rows.forEach((k,v)->{if(v instanceof Map<?,?>)names.put(NameUnicode.casefold(k),k);});var result=new ArrayList<String>();
        var modes=strings(spec.get("modes"));boolean classic=truth(spec.get("classic")),mafia=truth(spec.getOrDefault("mafia",Set.of("tc","vip").contains(mode)));
        for(String value:strings(spec.get("maps"))){String name=names.get(NameUnicode.casefold(value));if(name==null)continue;var entry=map(rows.get(name));if(modes.stream().anyMatch(strings(entry.get("invalid_modes"))::contains)||truth(entry.get("classic"))!=classic||truth(entry.get("mafia"))!=mafia||!truth(entry.get("release")))continue;if(!result.contains(name))result.add(name);}
        return List.copyOf(result);
    }
    private static final class Builder {
        Path source;Zone occupation;
        final Map<Integer,List<Zone>> spawns=new HashMap<>(),bases=new HashMap<>();final Map<Integer,List<Position>> points=new HashMap<>();
        final Map<Integer,BigInteger> minimum=new HashMap<>();final Map<Integer,Integer> staticColors=new HashMap<>();final Map<String,String> layoutSources=new HashMap<>();
        final List<Zone> neutral=new ArrayList<>(),diamonds=new ArrayList<>();final List<BigInteger> capacities=new ArrayList<>();final List<Position> bombs=new ArrayList<>();final List<Entity> entities=new ArrayList<>();final List<Ambient> ambient=new ArrayList<>();final List<Achievement> achievements=new ArrayList<>();final List<String> diagnostics=new ArrayList<>();
        Builder(){for(int team:List.of(2,3)){spawns.put(team,new ArrayList<>());bases.put(team,new ArrayList<>());points.put(team,new ArrayList<>());minimum.put(team,BigInteger.ZERO);}}
    }
    private static void legacy(Builder b,Map<String,Object> p){
        for(int team:List.of(2,3)){
            String prefix=team==2?"team_one":"team_two";
            b.spawns.get(team).addAll(spawnAreas(p.get(prefix+"_spawn_area"),team,prefix+"_spawn_area"));
            for(Object row:list(p.get(prefix+"_spawn"))){Position v=point(row);if(v!=null)b.points.get(team).add(v);}
            Zone base=centered("base",team,p.get(prefix+"_base_point"),p.get(prefix+"_base_w_h_d"),prefix+"_base_point");if(base!=null)b.bases.get(team).add(base);
            b.minimum.put(team,integerOr(p.get(prefix+"_min_destruction"),BigInteger.ZERO).max(BigInteger.ZERO));
            if(!b.spawns.get(team).isEmpty())b.layoutSources.put("spawn"+team,prefix+"_spawn_area");if(base!=null)b.layoutSources.put("base"+team,prefix+"_base_point");
        }
        b.neutral.addAll(parallel(p,"mh_base_points","mh_base_w_h_d",false));if(!b.neutral.isEmpty())b.layoutSources.put("neutral","mh_base_points");
        b.occupation=centered("base",3,p.get("occupation_base_point"),p.get("occupation_base_w_h_d"),"occupation_base_point");if(b.occupation!=null)b.layoutSources.put("occupation","occupation_base_point");
        for(Object row:list(p.get("occupation_bomb_points"))){Position v=point(row);if(v!=null)b.bombs.add(v);}
        var points=list(p.get("diamond_base_points"));var sizes=list(p.get("diamond_base_w_h_d"));
        for(int i=0;i<Math.min(points.size(),sizes.size());i++){
            int team=smallInteger(at(p.get("diamond_base_teams"),i),1);if(team!=1&&team!=2&&team!=3)team=1;
            Zone zone=centered("base",team,points.get(i),sizes.get(i),"diamond_base_points["+i+"]");if(zone==null)continue;
            b.diamonds.add(zone);b.capacities.add(integerOr(at(p.get("diamond_base_capacity"),i),BigInteger.ONE).max(BigInteger.ONE));
        }
        if(!b.diamonds.isEmpty())b.layoutSources.put("diamond","diamond_base_points");
        String[] drops={"ammo","health","block"};for(int i=0;i<drops.length;i++)for(Object row:list(p.get(drops[i]+"_crate_drop_points"))){Position v=point(row);if(v!=null)b.entities.add(new Entity(i+3,drops[i],v,"legacy_"+drops[i]+"_drop",null));}
        var ids=list(p.get("ac_ids"));var kinds=list(p.get("ac_types"));var centres=list(p.get("ac_centres"));var dimensions=list(p.get("ac_w_h_d"));
        for(int i=0;i<Math.min(Math.min(ids.size(),kinds.size()),Math.min(centres.size(),dimensions.size()));i++){
            if(!(ids.get(i) instanceof String id))continue;BigInteger kind=integer(kinds.get(i));Zone z=centered("base",1,centres.get(i),dimensions.get(i),id);if(kind==null||z==null)continue;
            int team=smallInteger(at(p.get("ac_teams"),i),0);Integer owner=team==1?Integer.valueOf(2):team==2?Integer.valueOf(3):null;b.achievements.add(new Achievement(id,kind,List.of(z.x()+z.x0(),z.x()+z.x1(),z.y()+z.y0(),z.y()+z.y1(),z.z()+z.z0(),z.z()+z.z1()),owner,integerOr(at(p.get("ac_kills"),i),BigInteger.ZERO).max(BigInteger.ZERO),integerOr(at(p.get("ac_weapons"),i),BigInteger.ZERO)));
        }
    }
    private static List<Zone> spawnAreas(Object rows,int team,String item){var out=new ArrayList<Zone>();for(Object row:list(rows)){var parts=list(row);if(parts.size()<2)continue;Zone z=centered("spawn",team,parts.get(0),parts.get(1),item);if(z!=null)out.add(new Zone(z.kind(),team,z.x(),z.y(),z.z(),z.x0(),z.x1(),z.y0(),z.y1(),z.z0(),Math.max(z.z1(),239-z.z()),item));}return out;}
    private static Zone centered(String kind,int team,Object center,Object dimensions,String item){Position p=point(center),d=point(dimensions);if(p==null||d==null||d.x()<=0||d.y()<=0||d.z()<=0)return null;return new Zone(kind,team,p.x(),p.y(),p.z(),-d.x()/2,d.x()/2,-d.y()/2,d.y()/2,-d.z()/2,d.z()/2,item);}
    private static List<Zone> parallel(Map<String,Object> p,String pointsKey,String sizesKey,boolean teams){var points=list(p.get(pointsKey));var sizes=list(p.get(sizesKey));var out=new ArrayList<Zone>();for(int i=0;i<Math.min(points.size(),sizes.size());i++){Zone z=centered("base",teams?(i==0?2:3):1,points.get(i),sizes.get(i),pointsKey+"["+i+"]");if(z!=null)out.add(z);}return out;}
    private static void applyMode(Builder b,Map<String,Object> p,String mode){
        switch(mode){
            case "ctf","cctf"->{var bases=parallel(p,"ctf_base_points","ctf_base_w_h_d",true);if(bases.size()>=2)for(int i=0;i<2;i++){b.bases.put(i+2,new ArrayList<>(List.of(bases.get(i))));b.layoutSources.put("base"+(i+2),"ctf_base_points");}}
            case "tc"->{var bases=parallel(p,"tc_base_points","tc_base_w_h_d",false);if(!bases.isEmpty()){b.neutral.clear();b.neutral.addAll(bases);b.layoutSources.put("neutral","tc_base_points");}}
            case "oc"->{for(int team:List.of(2,3)){String key=team==2?"oc_team_one_spawn_area":"oc_team_two_spawn_area";var zones=spawnAreas(p.get(key),team,key);if(!zones.isEmpty()){b.spawns.put(team,zones);b.layoutSources.put("spawn"+team,key);}}}
            case "zom"->{var survivors=spawnAreas(p.get("survivor_spawn_area"),3,"survivor_spawn_area");if(!survivors.isEmpty()){b.spawns.put(3,survivors);b.layoutSources.put("spawn3","survivor_spawn_area");}var zombies=spawnAreas(p.get("zombie_spawn_area"),2,"zombie_spawn_area");if(!zombies.isEmpty()){String key=b.spawns.get(2).isEmpty()?"zombie_spawn_area":"zombie_spawn_area+team_one_spawn_area";zombies.addAll(b.spawns.get(2));b.spawns.put(2,zombies);b.layoutSources.put("spawn2",key);}}
            default->{}
        }
    }
    private static void ugc(Builder b,Object rows,String mode){
        for(Object row:list(rows)){
            var r=mapOrNull(row);if(r==null||!applies(r.get("mode"),mode))continue;String item=str(r.getOrDefault("item","")).toLowerCase(Locale.ROOT);Position p=point(r.get("position"));if(p==null)continue;
            int drop=switch(item){case "ugc_ammo_drop"->3;case "ugc_health_drop"->4;case "ugc_block_drop"->5;default->0;};
            if(drop>0){b.entities.add(new Entity(drop,drop==3?"ammo":drop==4?"health":"block",p,item,null));continue;}
            if(item.equals("ugc_bomb_drop")){b.bombs.add(p);continue;}
            if(Set.of("flare_block","flareblock","glowblock","static_flare").contains(item)){Integer c=rgb(r.get("color"));if(c!=null)b.entities.add(new Entity(13,"static_flare",p,item,c));continue;}
            Ugc info=UGC.get(item);if(info==null)continue;String kind=item.contains("_spawn")?"spawn":item.contains("_base")?"base":"";if(kind.isEmpty())continue;var e=info.extents();var zone=new Zone(kind,info.team(),p.x(),p.y(),p.z(),e.get(0),e.get(1),e.get(2),e.get(3),e.get(4),e.get(5),item);
            if(info.team()==2||info.team()==3)(kind.equals("spawn")?b.spawns:b.bases).get(info.team()).add(zone);
            else if(info.team()==1&&kind.equals("base")){b.neutral.add(zone);if(mode.equals("dia")){b.diamonds.add(zone);b.capacities.add(BigInteger.ONE);}}
            if(mode.equals("oc")&&kind.equals("base")&&info.team()==3&&b.occupation==null)b.occupation=zone;
        }
    }
    private static void parseAmbients(Builder b,Object rows){
        var list=list(rows);for(Object row:list.subList(0,Math.min(32,list.size()))){var r=list(row);if(r.isEmpty()||!(r.get(0) instanceof String text))continue;String name=NameUnicode.strip(text);if(!name.matches("[A-Za-z0-9][A-Za-z0-9_\\-]{0,62}")||!AMBIENT_ASSETS.contains(name))continue;
            var points=new ArrayList<StateData.Vector>();var raw=list(at(r,1));for(Object point:raw.subList(0,Math.min(255,raw.size()))){StateData.Vector v=vector(point);if(v!=null)points.add(new StateData.Vector((int)Math.rint(v.x()),(int)Math.rint(v.y()),(int)Math.rint(v.z())));}
            double volume=r.size()>2?number(r.get(2),Double.NaN):1,attenuation=r.size()>3?number(r.get(3),Double.NaN):points.isEmpty()?0:1;if(!(volume>=0&&volume<=4&&attenuation>=0&&attenuation<=16))continue;b.ambient.add(new Ambient(name,points,volume,attenuation));
        }
    }
    private static String validSky(Object value){return value instanceof String s?SceneConfig.validSkybox(s):null;}
    private static StateData.Vector vector(Object value){Position p=point(value);if(p==null||Math.abs(p.x())>32767||Math.abs(p.y())>32767||Math.abs(p.z())>32767)return null;return new StateData.Vector(p.x(),p.y(),p.z());}
    private static Position point(Object value){var v=list(value);if(v.size()<3)return null;Double x=optionalNumber(v.get(0)),y=optionalNumber(v.get(1)),z=optionalNumber(v.get(2));return x==null||y==null||z==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)?null:new Position(x,y,z);}
    private static int[] components(Object value,int count,boolean bytes){var v=list(value);if(v.size()<count)return null;int[] out=new int[count];for(int i=0;i<count;i++){BigInteger n=integer(v.get(i));if(n==null||n.bitLength()>31||bytes&&(n.signum()<0||n.intValue()>255))return null;out[i]=n.intValue();}return out;}
    private static Integer rgb(Object value){if(value instanceof Number&&!(value instanceof Double)){BigInteger n=integer(value);return n!=null&&n.signum()>=0&&n.compareTo(BigInteger.valueOf(0xffffff))<=0?n.intValue():null;}var c=components(value,3,true);return c==null?null:c[0]<<16|c[1]<<8|c[2];}
    private static BigInteger integer(Object value){try{if(value instanceof BigInteger b)return b;if(value instanceof Boolean b)return b?BigInteger.ONE:BigInteger.ZERO;if(value instanceof Number n){double d=n.doubleValue();if(!Double.isFinite(d))return null;return value instanceof Float||value instanceof Double?new java.math.BigDecimal(d).toBigInteger():BigInteger.valueOf(n.longValue());}if(value instanceof String s){String text=digits(NameUnicode.strip(s));if(text.length()<=4300&&text.matches("[+-]?[0-9]+(?:_[0-9]+)*"))return new BigInteger(text.replace("_",""));}return null;}catch(NumberFormatException e){return null;}}
    private static BigInteger integerOr(Object value,BigInteger fallback){BigInteger n=integer(value);return n==null?fallback:n;}
    private static int smallInteger(Object value,int fallback){BigInteger n=integer(value);return n==null||n.bitLength()>31?fallback:n.intValue();}
    private static String digits(String text){var out=new StringBuilder();text.codePoints().forEach(cp->{int d=Character.digit(cp,10);if(d>=0)out.append((char)('0'+d));else out.appendCodePoint(cp);});return out.toString();}
    private static Double optionalNumber(Object value){
        if(value==null)return null;if(value instanceof Boolean b)return b?1.0:0.0;if(value instanceof Number n)return n.doubleValue();
        if(!(value instanceof String s))return null;String text=digits(NameUnicode.strip(s)).toLowerCase(Locale.ROOT);
        for(int i=0;i<text.length();i++)if(text.charAt(i)=='_'&&(i==0||i+1==text.length()||!Character.isDigit(text.charAt(i-1))||!Character.isDigit(text.charAt(i+1))))return null;
        text=text.replace("_","");
        if(text.matches("[+-]?(inf(inity)?|nan)"))return text.endsWith("nan")?Double.NaN:text.startsWith("-")?Double.NEGATIVE_INFINITY:Double.POSITIVE_INFINITY;
        if(!text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:e[+-]?[0-9]+)?"))return null;
        try{return Double.valueOf(text);}catch(NumberFormatException e){return null;}
    }
    private static double number(Object value,double fallback){Double n=optionalNumber(value);return n==null?fallback:n;}
    private static List<?> list(Object value){return value instanceof List<?> l?l:List.of();}
    private static Object at(Object rows,int i){var l=list(rows);return i>=l.size()?null:l.get(i);}
    private static String str(Object value){return value==null?"None":value instanceof Boolean b?b?"True":"False":String.valueOf(value);}
    private static boolean nonFinite(Object value){if(value instanceof Number n)return !Double.isFinite(n.doubleValue());if(value instanceof Map<?,?> m)return m.values().stream().anyMatch(MapMetadata::nonFinite);if(value instanceof Collection<?> c)return c.stream().anyMatch(MapMetadata::nonFinite);return false;}
    private static boolean truth(Object value){if(value==null)return false;if(value instanceof Boolean b)return b;if(value instanceof Number n)return n.doubleValue()!=0;if(value instanceof String s)return !s.isEmpty();if(value instanceof Collection<?> c)return !c.isEmpty();if(value instanceof Map<?,?> m)return !m.isEmpty();return true;}
    private static List<String> strings(Object value){return list(value).stream().map(MapMetadata::str).toList();}
    private static Map<String,Object> mapOrNull(Object value){if(!(value instanceof Map<?,?> m))return null;var result=new LinkedHashMap<String,Object>();m.forEach((k,v)->result.put(str(k),v));return result;}
    private static Map<String,Object> map(Object value){var result=mapOrNull(value);return result==null?Map.of():result;}
    private static String truncate(String value,int count){return value.codePointCount(0,value.length())<=count?value:value.substring(0,value.offsetByCodePoints(0,count));}
    private static Object freeze(Object v){if(v instanceof Map<?,?> m){var out=new LinkedHashMap<String,Object>();m.forEach((k,value)->out.put(str(k),freeze(value)));return Collections.unmodifiableMap(out);}if(v instanceof List<?> l)return Collections.unmodifiableList(l.stream().map(MapMetadata::freeze).toList());return v;}
    private static Map<String,Object> freezeMap(Map<String,Object> source){var out=new LinkedHashMap<String,Object>();source.forEach((k,v)->out.put(k,freeze(v)));return Collections.unmodifiableMap(out);}
    private static <T> Map<Integer,List<T>> freezeLists(Map<Integer,List<T>> source){var out=new HashMap<Integer,List<T>>();source.forEach((k,v)->out.put(k,List.copyOf(v)));return Map.copyOf(out);}
}
