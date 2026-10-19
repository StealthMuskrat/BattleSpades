// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.battlespades.world.BlockDamageModel.Cell;

/** Worker-only case-insensitive model cache and exact roll/pitch/yaw quarter-turn expansion. */
public final class PrefabModels {
    public record Block(Cell cell,int color){}
    private final List<Path> roots;private final Map<String,Optional<Kv6>> models=new HashMap<>();private Map<String,Path> files;
    public PrefabModels(List<Path> roots){this.roots=List.copyOf(roots);}
    public Optional<Kv6> get(String name)throws IOException{
        String key=name.toLowerCase(Locale.ROOT);var cached=models.get(key);if(cached!=null)return cached;
        if(files==null){files=new HashMap<>();for(var root:roots)if(Files.isDirectory(root))try(var entries=Files.list(root)){for(var file:entries.filter(Files::isRegularFile).toList()){String n=file.getFileName().toString();if(n.toLowerCase(Locale.ROOT).endsWith(".kv6"))files.putIfAbsent(n.substring(0,n.length()-4).toLowerCase(Locale.ROOT),file);}}}
        var file=files.get(key);var model=file==null?Optional.<Kv6>empty():Optional.of(Kv6.decode(Files.readAllBytes(file)));models.put(key,model);return model;
    }
    private static int rounded(double value){return (int)(value>=0?value+.5:value-.5);}
    public static List<Block> expand(Kv6 model,Cell anchor,int yaw,int pitch,int roll,Integer base){
        var blocks=new ArrayList<Block>(model.voxels().size());for(var v:model.voxels()){
            int x=rounded(v.x()-model.pivotX()),y=rounded(v.y()-model.pivotY()),z=rounded(v.z()-model.pivotZ());
            for(int i=0;i<(roll&3);i++){int old=x;x=-z;z=old;}for(int i=0;i<(pitch&3);i++){int old=y;y=z;z=-old;}for(int i=0;i<(yaw&3);i++){int old=x;x=y;y=-old;}
            int r=v.r(),g=v.g(),b=v.b();if(base!=null){r=(r+(base>>16&255))/2;g=(g+(base>>8&255))/2;b=(b+(base&255))/2;}
            blocks.add(new Block(new Cell(x+anchor.x(),y+anchor.y(),z+anchor.z()),r<<16|g<<8|b));
        }return List.copyOf(blocks);
    }
}
