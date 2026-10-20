// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.nio.*;
/** Unkeyed BLAKE2s with 16-byte output for source .botnav identities (RFC 7693 sections 2 and 3). */
final class Blake2s128 {
    private static final int[] IV={0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19};
    private static final int[][] SIGMA={{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},{14,10,4,8,9,15,13,6,1,12,0,2,11,7,5,3},{11,8,12,0,5,2,15,13,10,14,3,6,7,1,9,4},{7,9,3,1,13,12,11,14,2,6,5,10,4,0,15,8},{9,0,5,7,2,4,10,15,14,1,11,12,6,8,3,13},{2,12,6,10,0,11,8,3,4,13,7,5,15,14,1,9},{12,5,1,15,14,13,4,10,0,7,6,3,9,2,8,11},{13,11,7,14,12,1,3,9,5,0,15,4,8,6,2,10},{6,15,14,9,11,3,0,8,12,2,13,7,1,4,10,5},{10,2,8,4,7,6,1,5,15,11,9,14,3,12,13,0}};
    private Blake2s128(){}
    static byte[] digest(byte[] source){int[] h=IV.clone();h[0]^=0x01010010;int position=0;do{int size=Math.min(64,source.length-position);int[] m=new int[16];for(int i=0;i<size;i++)m[i>>>2]|=(source[position+i]&255)<<((i&3)*8);position+=size;int[] v=new int[16];System.arraycopy(h,0,v,0,8);System.arraycopy(IV,0,v,8,8);v[12]^=position;if(position==source.length)v[14]=~v[14];for(var s:SIGMA){g(v,0,4,8,12,m[s[0]],m[s[1]]);g(v,1,5,9,13,m[s[2]],m[s[3]]);g(v,2,6,10,14,m[s[4]],m[s[5]]);g(v,3,7,11,15,m[s[6]],m[s[7]]);g(v,0,5,10,15,m[s[8]],m[s[9]]);g(v,1,6,11,12,m[s[10]],m[s[11]]);g(v,2,7,8,13,m[s[12]],m[s[13]]);g(v,3,4,9,14,m[s[14]],m[s[15]]);}for(int i=0;i<8;i++)h[i]^=v[i]^v[i+8];}while(position<source.length);var result=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<4;i++)result.putInt(h[i]);return result.array();}
    private static void g(int[] v,int a,int b,int c,int d,int x,int y){v[a]+=v[b]+x;v[d]=Integer.rotateRight(v[d]^v[a],16);v[c]+=v[d];v[b]=Integer.rotateRight(v[b]^v[c],12);v[a]+=v[b]+y;v[d]=Integer.rotateRight(v[d]^v[a],8);v[c]+=v[d];v[b]=Integer.rotateRight(v[b]^v[c],7);}
}
