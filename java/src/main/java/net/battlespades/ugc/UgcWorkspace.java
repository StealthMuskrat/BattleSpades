// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.ugc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.battlespades.world.MutableWorld;
import net.battlespades.world.MetadataDocument;
import net.battlespades.entities.MapEntity;

/** VXL/TXT/UGC project files and serialized atomic checkpoints. Never reads live world from a worker. */
public final class UgcWorkspace {
    public record Fileset(Path vxl,Path metadata,Path sidecar,Path preview){}
    public record Opened(UgcProject project,Fileset files,byte[] source,String originalSidecar){public Opened(UgcProject project,Fileset files,byte[] source){this(project,files,source,project.json());}public Opened{source=source.clone();}@Override public byte[] source(){return source.clone();}}
    public record Snapshot(byte[] vxl,String sidecar,String sceneMetadata){public Snapshot(byte[] vxl,String sidecar){this(vxl,sidecar,null);}public Snapshot{vxl=vxl.clone();Objects.requireNonNull(sidecar);}@Override public byte[] vxl(){return vxl.clone();}}
    private final Fileset files;
    public UgcWorkspace(Fileset files){this.files=Objects.requireNonNull(files);}
    public Fileset files(){return files;}
    public Path sceneMetadata(){String name=files.vxl.getFileName().toString();return files.vxl.resolveSibling(name.substring(0,name.length()-4)+".json");}
    /** One creator process owns the project until its final checkpoint is durable. */
    public record Lease(java.nio.channels.FileChannel channel,java.nio.channels.FileLock fileLock)implements AutoCloseable{
        @Override public void close()throws IOException{try{fileLock.release();}finally{channel.close();}}
    }
    public Lease lock()throws IOException{
        var path=files.sidecar.resolveSibling(files.sidecar.getFileName()+".lock");Files.createDirectories(path.toAbsolutePath().getParent());var channel=java.nio.channels.FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try{var lease=channel.tryLock();if(lease==null)throw new IOException("UGC project is already open: "+files.sidecar);return new Lease(channel,lease);}catch(IOException|RuntimeException error){channel.close();if(error instanceof java.nio.channels.OverlappingFileLockException)throw new IOException("UGC project is already open: "+files.sidecar,error);throw error;}
    }
    /** A separate standard map-metadata layer preserves native editor-item validation in the .ugc file. */
    public Snapshot capture(MutableWorld world,UgcProject project,List<MapEntity> entities)throws IOException{
        var snapshot=capture(world,project);var lights=new ArrayList<Map<String,Object>>();
        for(var e:entities)if(e.alive&&e.type==13&&e.color!=null&&(e.kind.equals("flare_block")||e.kind.equals("map_flare")))lights.add(Map.of("position",List.of((int)e.x,(int)e.y,(int)e.z),"mode","nor","item","flare_block","color",List.of((int)e.color.x(),(int)e.color.y(),(int)e.color.z())));
        return new Snapshot(snapshot.vxl,snapshot.sidecar,overlay(snapshot.sidecar,lights));
    }
    public void verifySceneMetadata()throws IOException{existingMetadata();}
    private Map<String,Object> existingMetadata()throws IOException{
        var path=sceneMetadata();if(!Files.exists(path))return new LinkedHashMap<>();if(Files.size(path)>MetadataDocument.MAX_BYTES)throw new IOException("UGC scene metadata too large");Object parsed=MetadataDocument.json(Files.readString(path));if(!(parsed instanceof Map<?,?> map))throw new IOException("Invalid UGC scene metadata");var result=new LinkedHashMap<String,Object>();map.forEach((k,v)->result.put(String.valueOf(k),v));return result;
    }
    private String overlay(String sidecar,List<? extends Map<?,?>> lights)throws IOException{
        var metadata=existingMetadata();var project=(Map<?,?>)MetadataDocument.json(sidecar);var rows=new ArrayList<Object>((List<?>)project.get("ugc_entities"));rows.addAll(lights);metadata.put("ugc_entities",rows);
        for(String key:List.of("skybox_name","skybox_texture","skybox","ground_colors","fog_color"))metadata.remove(key);
        return ProjectJson.encode(metadata);
    }

    public static Fileset paths(Path directory,String name){String slug=UgcProject.slug(name);var dir=directory.toAbsolutePath().normalize();return new Fileset(dir.resolve(slug+".vxl"),dir.resolve(slug+".txt"),dir.resolve(slug+".ugc"),dir.resolve(slug+".png"));}
    public static Path publishDirectory(Path root){var path=root.toAbsolutePath().normalize();if(path.getFileName()==null||!path.getFileName().toString().equalsIgnoreCase("hosted_ugc"))throw new IllegalArgumentException("Publish root must be the hosted_ugc directory");return path.resolve("maps");}
    @SuppressWarnings("try")
    public static Opened create(UgcProject project,Path directory,String name,UgcAssets assets,boolean overwrite)throws IOException{var workspace=new UgcWorkspace(paths(directory,name));try(var lease=workspace.lock()){return createLocked(project,directory,name,assets,overwrite);}}
    private static Opened createLocked(UgcProject project,Path directory,String name,UgcAssets assets,boolean overwrite)throws IOException{var files=paths(directory,name);if(!overwrite&&(Files.exists(files.vxl)||Files.exists(files.sidecar)||Files.exists(new UgcWorkspace(files).sceneMetadata())))throw new FileAlreadyExistsException("UGC project already exists: "+files.sidecar);var source=assets.files(project.terrain());byte[] raw=Files.readAllBytes(source.vxl());byte[] metadata=Files.readAllBytes(source.metadata());Files.createDirectories(files.sidecar.getParent());atomic(files.vxl,raw);atomic(files.metadata,metadata);atomic(files.sidecar,project.json().getBytes(StandardCharsets.UTF_8));if(overwrite){Files.deleteIfExists(new UgcWorkspace(files).sceneMetadata());Files.deleteIfExists(files.preview);}return new Opened(project,files,raw);}
    public static Opened open(Path directory,String name,String target)throws IOException{var files=paths(directory,name);if(!Files.isRegularFile(files.metadata))throw new NoSuchFileException(files.metadata.toString());String text=readText(files.sidecar);var project=UgcProject.parse(text,UgcProject.slug(name),target);if(Files.size(files.vxl)>256L*1024*1024)throw new IOException("UGC VXL too large");byte[] source=Files.readAllBytes(files.vxl);return new Opened(project,files,source,text);}
    private static String readText(Path p)throws IOException{if(Files.size(p)>8*1024*1024)throw new IOException("UGC sidecar too large");return Files.readString(p,StandardCharsets.UTF_8);}
    /** Owner-side VXL serialization occurs only on explicit save/shutdown, never a periodic tick. */
    public static Snapshot capture(MutableWorld world,UgcProject project){var out=new ByteArrayOutputStream();for(int y=0;y<512;y++)for(int x=0;x<512;x++){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("UGC save cancelled");out.writeBytes(world.serializeColumn(x,y));if(out.size()>256*1024*1024)throw new IllegalArgumentException("UGC VXL exceeds save limit");}return new Snapshot(out.toByteArray(),project.json());}
    public synchronized void save(Snapshot snapshot)throws IOException{atomic(files.vxl,snapshot.vxl);if(snapshot.sceneMetadata!=null)atomic(sceneMetadata(),snapshot.sceneMetadata.getBytes(StandardCharsets.UTF_8));atomic(files.sidecar,snapshot.sidecar.getBytes(StandardCharsets.UTF_8));}
    public synchronized void checkpoint(String sidecar)throws IOException{
        if(Files.exists(sceneMetadata())){var metadata=existingMetadata();var lights=new ArrayList<Map<?,?>>();Object rows=metadata.get("ugc_entities");if(rows instanceof List<?> list)for(Object row:list)if(row instanceof Map<?,?> map&&"flare_block".equals(map.get("item")))lights.add(map);atomic(sceneMetadata(),overlay(sidecar,lights).getBytes(StandardCharsets.UTF_8));}
        atomic(files.sidecar,sidecar.getBytes(StandardCharsets.UTF_8));
    }
    public synchronized boolean preview(byte[] data)throws IOException{if(!validPreview(data))return false;atomic(files.preview,data);return true;}
    public synchronized Optional<byte[]> preview()throws IOException{if(!Files.isRegularFile(files.preview)||Files.size(files.preview)>8*1024*1024)return Optional.empty();byte[] data=Files.readAllBytes(files.preview);return validPreview(data)?Optional.of(data):Optional.empty();}
    public static boolean validPreview(byte[] data){byte[] signature={(byte)137,80,78,71,13,10,26,10};return data.length>=8&&data.length<=8*1024*1024&&Arrays.equals(signature,Arrays.copyOf(data,8));}
    private static void atomic(Path path,byte[] payload)throws IOException{Files.createDirectories(path.toAbsolutePath().getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");try{Files.write(temp,payload);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}}
}
