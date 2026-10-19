// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.ugc;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.battlespades.world.MetadataDocument;

/** Genuine asset roots; case-insensitive installed names intersect the retail palette table. */
public record UgcAssets(Path root,Path maps,Path prefabs) {
    public UgcAssets{root=root.toAbsolutePath().normalize();maps=maps.toAbsolutePath().normalize();prefabs=prefabs.toAbsolutePath().normalize();}
    public record TerrainFiles(Path vxl,Path metadata){}
    public TerrainFiles files(UgcProject.Terrain terrain)throws IOException{return new TerrainFiles(child(maps,terrain.stem()+".vxl").orElseThrow(()->new IOException("Missing terrain VXL: "+terrain.stem())),child(maps,terrain.stem()+".txt").orElseThrow(()->new IOException("Missing terrain metadata: "+terrain.stem())));}
    public static UgcAssets discover(List<Path> roots)throws IOException{for(var root:new LinkedHashSet<>(roots)){var layout=new UgcAssets(root,root.resolve("ugc/maps"),root.resolve("ugc/kv6"));if(!Files.isDirectory(layout.maps)||!Files.isDirectory(layout.prefabs))continue;for(var t:UgcProject.TERRAINS)layout.files(t);return layout;}throw new IOException("Could not locate complete retail UGC assets in "+roots);}
    public List<String> compatible(UgcProject.Terrain terrain)throws IOException{var installed=new HashMap<String,String>();try(var paths=Files.list(prefabs)){for(var p:paths.filter(Files::isRegularFile).sorted().toList()){String n=p.getFileName().toString();if(n.toLowerCase(Locale.ROOT).endsWith(".kv6"))installed.putIfAbsent(n.substring(0,n.length()-4).toLowerCase(Locale.ROOT),n.substring(0,n.length()-4));}}var names=new LinkedHashSet<String>();for(var e:UgcCatalog.PREFABS.entrySet())if(e.getValue().contains(terrain.tag())){String n=installed.get(e.getKey().toLowerCase(Locale.ROOT));if(n!=null)names.add(n);}return names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();}
    private static Optional<Path> child(Path dir,String name)throws IOException{try(var children=Files.list(dir)){return children.filter(Files::isRegularFile).filter(p->p.getFileName().toString().equalsIgnoreCase(name)).sorted().findFirst();}}
    public record Presentation(String skybox,List<List<Integer>> colors){public Presentation{colors=List.copyOf(colors);}}
    public static Presentation presentation(String text){var values=MetadataDocument.sidecar(text,Set.of("skybox_texture","skybox_name","ground_colors"));if(values==null)return new Presentation("",List.of());Object raw=values.getOrDefault("skybox_name",values.getOrDefault("skybox_texture",""));String skybox=raw==null?"":raw.toString();var colors=new ArrayList<List<Integer>>();if(values.get("ground_colors")instanceof List<?> rows)for(var row:rows.stream().limit(32).toList()){if(!(row instanceof List<?> c)||c.size()!=4||c.stream().anyMatch(v->!(v instanceof Number)))continue;colors.add(c.stream().map(v->((Number)v).intValue()&255).toList());}return new Presentation(skybox,colors);}
}
