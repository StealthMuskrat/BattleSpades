// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import java.nio.file.*;
import java.util.*;
import net.battlespades.ugc.*;
import net.battlespades.world.Kv6;

/** Small valid retail assets exercising actual VXL/KV6 readers without installed game data. */
public final class CreatorFixtures {
    public static final String MODEL="ugc_prefab_primitive_block";
    private CreatorFixtures(){}
    public static UgcAssets assets(Path root)throws Exception{
        var maps=root.resolve("ugc/maps");var models=root.resolve("ugc/kv6");Files.createDirectories(maps);Files.createDirectories(models);
        for(var terrain:UgcProject.TERRAINS){StandardPorts.writeMap(maps.resolve(terrain.stem()+".vxl"));Files.writeString(maps.resolve(terrain.stem()+".txt"),"skybox_texture = 'User_Grassland.txt'\n");}
        Files.write(models.resolve(MODEL+".kv6"),model(3,3,70));return UgcAssets.discover(List.of(root));
    }
    public static byte[] model(int x,int y,int z){var voxels=new ArrayList<Kv6.Voxel>();var columns=new ArrayList<Integer>();var lengths=new ArrayList<Long>();for(int ix=0;ix<x;ix++){lengths.add((long)y*z);for(int iy=0;iy<y;iy++){columns.add(z);for(int iz=0;iz<z;iz++)voxels.add(new Kv6.Voxel(ix,iy,iz,0x12,0x34,0x56,255,63,0));}}return new Kv6(x,y,z,0,0,0,voxels,lengths,columns,new byte[0],false).encode();}
    public static UgcWorkspace.Opened project(Path root,UgcAssets assets)throws Exception{return UgcWorkspace.create(new UgcProject("Creator fixture","","","grassland","ctf"),root,"Creator",assets,false);}
}
