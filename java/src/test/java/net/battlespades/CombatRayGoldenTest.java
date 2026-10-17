// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.*;
import net.battlespades.physics.CollisionMap;
import net.battlespades.physics.PlayerPhysics.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatRayGoldenTest {
    @Test void originalManagerAccurateRayAndStepFallback()throws IOException {
        CollisionMap map=(x,y,z)->(x>=96&&x<109&&y>=96&&y<109&&z==62)||(x==104&&y>=98&&y<104&&z>=55&&z<62)||(x>=0&&x<512&&y>=0&&y<512&&z==239);
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/combat-ray.tsv.gz")))){
            int count=0;for(var line:new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().toList()){
                var r=line.split("\t");var a=Arrays.stream(r[1].split(",")).mapToDouble(CombatGeometryGoldenTest::number).toArray();var hit=HitTrace.terrainRay(map,new Vector(a[0],a[1],a[2]),new Vector(a[3],a[4],a[5]),a[6]);assertEquals(r[2],hit==null?"-":hit.x()+","+hit.y()+","+hit.z(),"original manager ray "+count++);
            }assertEquals(1600,count);
        }
    }
}
