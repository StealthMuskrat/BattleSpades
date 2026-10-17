// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.*;
import net.battlespades.physics.CollisionMap;
import net.battlespades.world.BlockDamageModel.Cell;

/** Original bounded 18-neighbor collapse search, including native down-first support probes. */
public final class TerrainCollapse {
    private TerrainCollapse() {}
    private record Offset(int x,int y,int z,int order) {}
    private static final List<Offset> NEIGHBORS,PROBE;
    public static final long WORK_BUDGET=180_000_000L;
    private static final int SAFE=1<<31;
    static {
        var neighbors=new ArrayList<Offset>();for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){int sum=Math.abs(x)+Math.abs(y)+Math.abs(z);if(sum>=1&&sum<=2)neighbors.add(new Offset(x,y,z,neighbors.size()));}
        NEIGHBORS=List.copyOf(neighbors);neighbors.sort(Comparator.comparingInt(Offset::z).thenComparingInt(d->d.x==0&&d.y==0?1:0).thenComparingInt(Offset::order));PROBE=List.copyOf(neighbors);
    }
    private static final class Cells {
        private int[] data=new int[64];private int size;
        void add(int key){if(size==data.length)data=Arrays.copyOf(data,data.length*2);data[size++]=key;}
        int pop(){return data[--size];}void clear(){size=0;}
    }
    private static boolean bounds(int x,int y,int z){return x>=0&&x<512&&y>=0&&y<512&&z>=0&&z<240;}
    private static int key(int x,int y,int z){return x|(y<<9)|(z<<18);}
    private static int walk(CollisionMap map,IntColors seen,Cells stack,Cells component,Cells discovered,int start,int generation,List<Offset> neighbors,long budget){
        component.clear();discovered.clear();stack.clear();seen.put(start,generation);discovered.add(start);stack.add(start);long work=0;
        while(stack.size>0){int cell=stack.pop(),x=cell&511,y=(cell>>>9)&511,z=cell>>>18;if(z>238)return 1;component.add(cell);
            for(var d:neighbors){if(++work>budget)return 2;int nx=x+d.x,ny=y+d.y,nz=z+d.z;if(!bounds(nx,ny,nz))continue;int next=key(nx,ny,nz),value=seen.get(next,0);if((value&SAFE)!=0)return 1;if(value==generation)continue;if(map.solid(nx,ny,nz)){seen.put(next,generation);discovered.add(next);stack.add(next);}}
        }
        return 0;
    }
    public static List<List<Cell>> find(MutableWorld world,List<Cell> removed){return find(CollisionMap.of(world),removed,WORK_BUDGET);}
    public static List<List<Cell>> find(CollisionMap map,List<Cell> removed,long workBudget){
        Objects.requireNonNull(map);Objects.requireNonNull(removed);var seen=new IntColors();var stack=new Cells();var component=new Cells();var discovered=new Cells();var chunks=new ArrayList<List<Cell>>();int generation=0;
        for(var cell:removed)for(var d:NEIGHBORS){int x=cell.x()+d.x,y=cell.y()+d.y,z=cell.z()+d.z;if(!bounds(x,y,z)||!map.solid(x,y,z))continue;int start=key(x,y,z);if(seen.contains(start))continue;
            int result=walk(map,seen,stack,component,discovered,start,++generation,PROBE,workBudget);if(result==0)result=walk(map,seen,stack,component,discovered,start,++generation,NEIGHBORS,workBudget);
            if(result==0){var chunk=new ArrayList<Cell>(component.size);for(int i=0;i<component.size;i++){int c=component.data[i];chunk.add(new Cell(c&511,(c>>>9)&511,c>>>18));}chunks.add(List.copyOf(chunk));}
            else for(int i=0;i<discovered.size;i++)seen.put(discovered.data[i],generation|SAFE);
        }
        return List.copyOf(chunks);
    }
}
