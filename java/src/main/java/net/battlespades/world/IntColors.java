// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.Arrays;

/** Primitive voxel-index map, mirroring the native color table without boxed entries. */
final class IntColors {
    private int[] keys = new int[4096];
    private int[] values = new int[4096];
    private int size;
    IntColors() { Arrays.fill(keys,-1); }
    private static int hash(int key) {
        key ^= key >>> 16;key *= 0x7feb352d;key ^= key >>> 15;key *= 0x846ca68b;key ^= key >>> 16;return key;
    }
    private int slot(int key) {
        int i=hash(key)&(keys.length-1);
        while(keys[i]!=-1 && keys[i]!=key) i=(i+1)&(keys.length-1);
        return i;
    }
    boolean contains(int key) { return keys[slot(key)]==key; }
    int get(int key,int fallback) { int i=slot(key);return keys[i]==key?values[i]:fallback; }
    void remove(int key) {
        int i=slot(key);if(keys[i]!=key)return;
        keys[i]=-1;size--;
        // Reinsert the following probe cluster so lookups cannot stop at the hole.
        for(i=(i+1)&(keys.length-1);keys[i]!=-1;i=(i+1)&(keys.length-1)) {
            int nextKey=keys[i],value=values[i];keys[i]=-1;size--;put(nextKey,value);
        }
    }
    void put(int key,int value) {
        if((size+1L)*5 > keys.length*3L) {
            int[] oldKeys=keys,oldValues=values;
            keys=new int[keys.length*2];values=new int[keys.length];Arrays.fill(keys,-1);size=0;
            for(int i=0;i<oldKeys.length;i++) if(oldKeys[i]!=-1) put(oldKeys[i],oldValues[i]);
        }
        int i=slot(key);if(keys[i]==-1) { keys[i]=key;size++; } values[i]=value;
    }
}
