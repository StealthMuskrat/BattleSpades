// SPDX-License-Identifier: AGPL-3.0-or-later AND PSF-2.0
// Adapted from CPython 3.12 Objects/setobject.c, copyright Python Software Foundation.
// Full license: META-INF/licenses/python-psf.txt. Integer entity IDs only; no Python runtime.
package net.battlespades.entities;

import java.util.*;

/** Original support-bucket iteration order matters when callbacks synchronously trigger other devices. */
public final class SourceEntityIds {
    private final Thread owner=Thread.currentThread();private int[] table=empty(8);private int fill,used;
    private static int[] empty(int size){var table=new int[size];Arrays.fill(table,-1);return table;}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Entity ID set outside owner");}
    private static void id(int value){if(value<0||value>65535)throw new IllegalArgumentException("Entity ID outside uint16");}
    private int lookup(int value){int mask=table.length-1,index=value&mask;long perturb=value;while(true){int probes=index+9<=mask?9:0;for(int offset=0;offset<=probes;offset++){int key=table[index+offset];if(key==-1||key==value)return index+offset;}perturb>>>=5;index=(index*5+1+(int)perturb)&mask;}}
    public boolean contains(int value){owned();id(value);return table[lookup(value)]==value;}
    public int size(){owned();return used;}
    public boolean add(int value){
        owned();id(value);int mask=table.length-1,index=value&mask,free=-1;long perturb=value;
        while(true){int probes=index+9<=mask?9:0;for(int offset=0;offset<=probes;offset++){int slot=index+offset,key=table[slot];if(key==-1){used++;if(free>=0)table[free]=value;else{table[slot]=value;fill++;if((long)fill*5>=mask*3L)resize(used>50000?used*2:used*4);}return true;}if(key==value)return false;if(key==-2)free=slot;}perturb>>>=5;index=(index*5+1+(int)perturb)&mask;}
    }
    private void resize(int minimum){int size=8;while(size<=minimum)size*=2;var previous=table;table=empty(size);fill=used;int mask=size-1;for(int value:previous)if(value>=0){int index=value&mask;long perturb=value;boolean stored=false;while(!stored){int probes=index+9<=mask?9:0;for(int offset=0;offset<=probes;offset++)if(table[index+offset]==-1){table[index+offset]=value;stored=true;break;}if(!stored){perturb>>>=5;index=(index*5+1+(int)perturb)&mask;}}}}
    public boolean discard(int value){owned();id(value);int slot=lookup(value);if(table[slot]!=value)return false;table[slot]=-2;used--;return true;}
    public List<Integer> values(){owned();var output=new ArrayList<Integer>(used);for(int value:table)if(value>=0)output.add(value);return List.copyOf(output);}
    public void clear(){owned();table=empty(8);fill=used=0;}
}
