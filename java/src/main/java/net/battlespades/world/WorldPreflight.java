// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.battlespades.player.NameUnicode;

/** Parse an isolated immutable map candidate on a supplied worker, never against the active world. */
public final class WorldPreflight {
    private final Path maps;
    private final Executor worker;
    public WorldPreflight(Path maps,Executor worker){this.maps=Objects.requireNonNull(maps).toAbsolutePath().normalize();this.worker=Objects.requireNonNull(worker);}
    public static String normalize(String name){
        String value=NameUnicode.strip(Objects.requireNonNull(name));if(value.isEmpty()||value.equals(".")||value.equals("..")||value.indexOf('/')>=0)return "";
        // Source pathlib uses POSIX path semantics: backslash is an ordinary filename character.
        if(value.indexOf(0)>=0)return "";
        int dot=value.lastIndexOf('.');return dot>0&&value.substring(dot).equalsIgnoreCase(".vxl")?value.substring(0,dot):value;
    }
    public CompletionStage<MapAssembly> load(String name,String mode){
        var result=new CompletableFuture<MapAssembly>();String normalized=normalize(name);if(normalized.isEmpty()){result.completeExceptionally(new IllegalArgumentException("Invalid or empty map name"));return result.minimalCompletionStage();}
        try{worker.execute(()->{try{
            Path root=maps.toRealPath();Path target=root.resolve(normalized.toLowerCase(Locale.ROOT).endsWith(".vxl")?normalized:normalized+".vxl").normalize();
            if(!Files.isRegularFile(target))throw new IllegalArgumentException("Map not found: "+normalized);
            target=target.toRealPath();if(!target.startsWith(root))throw new IllegalArgumentException("Map path must stay inside the configured maps directory");
            result.complete(MapAssembly.load(target,mode));
        }catch(IOException|RuntimeException error){result.completeExceptionally(error);}catch(Error fatal){result.completeExceptionally(fatal);}});}catch(RuntimeException rejected){result.completeExceptionally(rejected);}
        return result.minimalCompletionStage();
    }
}
