// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.ugc;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.battlespades.world.VxlMap;

/** Offline project commands use exactly the same model as the owned editor components. */
public final class UgcCommands {
    private UgcCommands(){}
    public static int run(String[] args,PrintStream out,PrintStream error){try{
        if(args.length==2&&args[0].equals("--ugc-assets")){var assets=UgcAssets.discover(List.of(Path.of(args[1])));out.println("Retail UGC assets: "+assets.root());for(var terrain:UgcProject.TERRAINS)out.println(terrain.key()+": "+assets.compatible(terrain).size()+" installed palette constructs");return 0;}
        if(args.length==6&&args[0].equals("--ugc-create")){var assets=UgcAssets.discover(List.of(Path.of(args[1])));var project=new UgcProject(args[3],"","",args[4],args[5]);var presentation=UgcAssets.presentation(Files.readString(assets.files(project.terrain()).metadata()));project.skybox(presentation.skybox());project.groundColors(presentation.colors());VxlMap.load(Files.readAllBytes(assets.files(project.terrain()).vxl()),"retail");var opened=UgcWorkspace.create(project,Path.of(args[2]),args[3],assets,false);out.println("Created UGC project: "+opened.files().sidecar());return report(opened,out);}
        if(args.length==3&&args[0].equals("--ugc-check"))return report(UgcWorkspace.open(Path.of(args[1]),args[2],null),out);
        error.println("UGC usage: --ugc-assets RETAIL_ROOT | --ugc-create RETAIL_ROOT DIRECTORY NAME TERRAIN MODE | --ugc-check DIRECTORY NAME");return 2;
    }catch(IOException|IllegalArgumentException exception){error.println("UGC project error: "+exception.getMessage());return 2;}}
    private static int report(UgcWorkspace.Opened opened,PrintStream out){VxlMap.load(opened.source(),"retail");var capacity=UgcCapacity.fromVxl(opened.source());var p=opened.project();out.printf("UGC project valid: %s; baseplate=%s; target=%s; objects=%d; solids=%d; chunks=%d; space=%s%n",p.title(),p.baseplate(),p.targetMode(),p.placements().size(),capacity.solids(),capacity.chunks(),capacity.hasSpace());out.println("Publishable modes: "+p.publishableModes());for(var row:p.validation().objectives())out.printf("%s: %d (%d..%d)%n",row.id(),row.value(),row.minimum(),row.maximum());return 0;}
}
