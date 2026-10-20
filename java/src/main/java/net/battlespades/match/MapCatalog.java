// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.battlespades.player.*;
import net.battlespades.scene.ModeCatalog;
import net.battlespades.world.MapMetadata;

/** Immutable startup/worker snapshot. Ranking never globs maps, reads metadata or inflates navigation. */
public final class MapCatalog {
    private final List<String> available;
    private final Map<String,Long> areas;
    private final Map<String,Map<String,Object>> retail;
    private final Map<String,List<String>> playlists;
    private final boolean explicitRotation;
    private MapCatalog(List<String> available,Map<String,Long> areas,Map<String,Map<String,Object>> retail,Map<String,List<String>> playlists,boolean explicitRotation){this.available=List.copyOf(available);this.areas=Collections.unmodifiableMap(new HashMap<>(areas));this.retail=retail;this.playlists=Map.copyOf(playlists);this.explicitRotation=explicitRotation;}
    public static MapCatalog load(Path root,List<String> rotation,boolean shuffle,Map<String,?> overrides,SourceRandom random)throws IOException{
        var discovered=new ArrayList<String>();if(Files.isDirectory(root))try(var paths=Files.newDirectoryStream(root,"*.vxl")){for(var path:paths)if(Files.isRegularFile(path)){var name=path.getFileName().toString();discovered.add(name.substring(0,name.length()-4));}}
        discovered.sort(Comparator.comparing(NameUnicode::casefold,(a,b)->Arrays.compare(a.codePoints().toArray(),b.codePoints().toArray())));var names=new HashMap<String,String>();discovered.forEach(name->names.put(NameUnicode.casefold(name),name));var available=new ArrayList<String>();
        if(rotation.isEmpty())available.addAll(discovered);else for(var raw:rotation){var name=names.get(NameUnicode.casefold(raw));if(name!=null)available.add(name);}
        if(shuffle&&available.size()>1)Objects.requireNonNull(random).shuffle(available);
        var sizes=new HashMap<String,Long>();overrides.forEach((name,value)->{Long area=null;if(value instanceof String text)area=switch(NameUnicode.strip(text).toLowerCase(Locale.ROOT)){case "small"->50000L;case "medium"->130000L;case "large"->230000L;default->null;};else if(value instanceof Number n&&Double.isFinite(n.doubleValue()))area=n.longValue();else if(value instanceof Boolean b)area=b?1L:0L;sizes.put(NameUnicode.casefold(name),area);});
        var areas=new HashMap<String,Long>();for(var name:discovered){String key=NameUnicode.casefold(name);if(sizes.containsKey(key))areas.put(key,sizes.get(key));else{var area=NavigationAreas.read(root.resolve(name+".botnav"));areas.put(key,area==null?null:area.longValue());}}
        var retail=MapMetadata.retailCatalogue(root);var pools=new HashMap<String,List<String>>();for(var code:ModeCatalog.definitions().keySet()){String canonical=MapMetadata.canonicalMode(code);pools.computeIfAbsent(canonical,ignored->MapMetadata.retailModePool(root,canonical));}
        return new MapCatalog(available,areas,retail,pools,!rotation.isEmpty());
    }
    /** Owner-side ordering after worker IO, retaining the gameplay random stream and all prepared metadata. */
    public MapCatalog shuffled(SourceRandom random){var names=new ArrayList<>(available);if(names.size()>1)random.shuffle(names);return new MapCatalog(names,areas,retail,playlists,explicitRotation);}
    public List<String> available(){return available;}
    public Long area(String name){return areas.get(NameUnicode.casefold(name));}
    public Long cap(String name){Object value=retail.getOrDefault(NameUnicode.casefold(name),Map.of()).get("max_players");try{if(value instanceof Boolean b)return b?1L:0L;if(value instanceof Number n)return Double.isFinite(n.doubleValue())?n.longValue():null;return value==null?null:Long.valueOf(value.toString());}catch(NumberFormatException invalid){return null;}}
    public List<String> forMode(String mode,List<String> stockMaps){
        String code=MapMetadata.canonicalMode(mode),retailCode=code.equals("cctf")?"ctf":code;List<String> filtered;
        if(explicitRotation)filtered=available.stream().filter(name->{var invalid=retail.getOrDefault(NameUnicode.casefold(name),Map.of()).get("invalid_modes");return !(invalid instanceof List<?> list&&list.contains(retailCode));}).toList();
        else{var playlist=stockMaps.isEmpty()?playlists.getOrDefault(code,List.of()):stockMaps;if(playlist.isEmpty())return available;var wanted=new HashSet<String>();playlist.forEach(name->wanted.add(NameUnicode.casefold(name)));filtered=available.stream().filter(name->wanted.contains(NameUnicode.casefold(name))).toList();}
        return filtered.isEmpty()?available:filtered;
    }
}
