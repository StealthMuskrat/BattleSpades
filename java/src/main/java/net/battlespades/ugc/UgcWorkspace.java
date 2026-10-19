// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.ugc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.battlespades.world.MutableWorld;

/** VXL/TXT/UGC project files and serialized atomic checkpoints. Never reads live world from a worker. */
public final class UgcWorkspace {
    public record Fileset(Path vxl,Path metadata,Path sidecar,Path preview){}
    public record Opened(UgcProject project,Fileset files,byte[] source){public Opened{source=source.clone();}@Override public byte[] source(){return source.clone();}}
    public record Snapshot(byte[] vxl,String sidecar){public Snapshot{vxl=vxl.clone();Objects.requireNonNull(sidecar);}@Override public byte[] vxl(){return vxl.clone();}}
    private final Fileset files;
    public UgcWorkspace(Fileset files){this.files=Objects.requireNonNull(files);}
    public Fileset files(){return files;}
    public static Fileset paths(Path directory,String name){String slug=UgcProject.slug(name);var dir=directory.toAbsolutePath().normalize();return new Fileset(dir.resolve(slug+".vxl"),dir.resolve(slug+".txt"),dir.resolve(slug+".ugc"),dir.resolve(slug+".png"));}
    public static Path publishDirectory(Path root){var path=root.toAbsolutePath().normalize();if(path.getFileName()==null||!path.getFileName().toString().equalsIgnoreCase("hosted_ugc"))throw new IllegalArgumentException("Publish root must be the hosted_ugc directory");return path.resolve("maps");}
    public static Opened create(UgcProject project,Path directory,String name,UgcAssets assets,boolean overwrite)throws IOException{var files=paths(directory,name);if(!overwrite&&(Files.exists(files.vxl)||Files.exists(files.sidecar)))throw new FileAlreadyExistsException("UGC project already exists: "+files.sidecar);var source=assets.files(project.terrain());byte[] raw=Files.readAllBytes(source.vxl());byte[] metadata=Files.readAllBytes(source.metadata());Files.createDirectories(files.sidecar.getParent());atomic(files.vxl,raw);atomic(files.metadata,metadata);atomic(files.sidecar,project.json().getBytes(StandardCharsets.UTF_8));return new Opened(project,files,raw);}
    public static Opened open(Path directory,String name,String target)throws IOException{var files=paths(directory,name);String text=readText(files.sidecar);var project=UgcProject.parse(text,UgcProject.slug(name),target);byte[] source=Files.readAllBytes(files.vxl);return new Opened(project,files,source);}
    private static String readText(Path p)throws IOException{if(Files.size(p)>8*1024*1024)throw new IOException("UGC sidecar too large");return Files.readString(p,StandardCharsets.UTF_8);}
    /** Owner-side VXL serialization occurs only on explicit save/shutdown, never a periodic tick. */
    public static Snapshot capture(MutableWorld world,UgcProject project){var out=new ByteArrayOutputStream();for(int y=0;y<512;y++)for(int x=0;x<512;x++){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("UGC save cancelled");out.writeBytes(world.serializeColumn(x,y));if(out.size()>256*1024*1024)throw new IllegalArgumentException("UGC VXL exceeds save limit");}return new Snapshot(out.toByteArray(),project.json());}
    public synchronized void save(Snapshot snapshot)throws IOException{atomic(files.vxl,snapshot.vxl);atomic(files.sidecar,snapshot.sidecar.getBytes(StandardCharsets.UTF_8));}
    public synchronized void checkpoint(String sidecar)throws IOException{atomic(files.sidecar,sidecar.getBytes(StandardCharsets.UTF_8));}
    public synchronized boolean preview(byte[] data)throws IOException{if(!validPreview(data))return false;atomic(files.preview,data);return true;}
    public synchronized Optional<byte[]> preview()throws IOException{if(!Files.isRegularFile(files.preview)||Files.size(files.preview)>8*1024*1024)return Optional.empty();byte[] data=Files.readAllBytes(files.preview);return validPreview(data)?Optional.of(data):Optional.empty();}
    public static boolean validPreview(byte[] data){byte[] signature={(byte)137,80,78,71,13,10,26,10};return data.length>=8&&data.length<=8*1024*1024&&Arrays.equals(signature,Arrays.copyOf(data,8));}
    private static void atomic(Path path,byte[] payload)throws IOException{Files.createDirectories(path.toAbsolutePath().getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");try{Files.write(temp,payload);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}}
}
