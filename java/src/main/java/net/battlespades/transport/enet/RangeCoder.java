// SPDX-License-Identifier: MIT
// Adapted from vendored ENet compress.c, Copyright (c) 2002-2020 Lee Salzman.
// This altered pure Java implementation retains the original algorithm; see THIRD_PARTY_NOTICES.
package net.battlespades.transport.enet;

import java.io.ByteArrayOutputStream;
import net.battlespades.protocol.ProtocolException;

/** ENet adaptive order-2 PPM range coder. Every invocation owns a fresh bounded model. */
public final class RangeCoder {
    private static final long MASK=0xffff_ffffL,TOP=1L<<24,BOTTOM=1L<<16;
    private RangeCoder() {}
    private static final class Symbol {
        int value,count,under,left,right,symbols,escapes,total,parent;
    }
    private record Coding(int symbol,int under,int count,int value) {}
    private static final class Model {
        final Symbol[] symbols=new Symbol[4096];
        int next,predicted,order;
        Model() { reset(); }
        int create(int value,int count) {
            if(next>=symbols.length) throw new ProtocolException("Range-coder model overflow");
            var s=new Symbol();s.value=value;s.count=count;s.under=count;symbols[next]=s;return next++;
        }
        void reset() { next=0;create(0,0);symbols[0].escapes=1;symbols[0].total=257;predicted=0;order=0; }
        void finishByte() {
            if(order>=2) predicted=symbols[predicted].parent;else order++;
            if(next>=4094) reset();
        }
        void setParent(int parentSlot,int value) { if(parentSlot<0) predicted=value;else symbols[parentSlot].parent=value; }
        int rescaleTree(int index) {
            int total=0;
            while(true) {
                Symbol s=symbols[index];s.count-=s.count>>1;s.under=s.count;
                if(s.left!=0) s.under+=rescaleTree(index+s.left);
                total+=s.under;if(s.right==0) break;index+=s.right;
            }
            return total;
        }
        void rescale(int context,int minimum) {
            Symbol c=symbols[context];c.total=c.symbols==0?0:rescaleTree(context+c.symbols);
            c.escapes-=c.escapes>>1;c.total+=c.escapes+256*minimum;
        }
        Coding encode(int context,int value,int update,int minimum) {
            Symbol c=symbols[context];int under=value*minimum,count=minimum,symbol;
            if(c.symbols==0) {symbol=create(value,update);c.symbols=symbol-context;}
            else {
                int index=context+c.symbols;
                while(true) {
                    Symbol node=symbols[index];
                    if(value<node.value) {
                        node.under+=update;
                        if(node.left!=0) {index+=node.left;continue;}
                        symbol=create(value,update);node.left=symbol-index;
                    } else if(value>node.value) {
                        under+=node.under;
                        if(node.right!=0) {index+=node.right;continue;}
                        symbol=create(value,update);node.right=symbol-index;
                    } else {
                        count+=node.count;under+=node.under-node.count;node.under+=update;node.count+=update;symbol=index;
                    }
                    break;
                }
            }
            return new Coding(symbol,under,count,value);
        }
        Coding decode(int context,int code,int update,int minimum) {
            Symbol c=symbols[context];int under=0,count=minimum,symbol,value;
            if(c.symbols==0) {
                if(minimum==0) throw new ProtocolException("Invalid range-coder context");
                value=code/minimum;under=code-code%minimum;
                if(value>255) throw new ProtocolException("Invalid range-coder value");
                symbol=create(value,update);c.symbols=symbol-context;
            } else {
                int index=context+c.symbols;
                while(true) {
                    Symbol node=symbols[index];int after=under+node.under+(node.value+1)*minimum,before=node.count+minimum;
                    if(code>=after) {
                        under+=node.under;
                        if(node.right!=0) {index+=node.right;continue;}
                        if(minimum==0) throw new ProtocolException("Missing range-coder right symbol");
                        value=node.value+1+(code-after)/minimum;under=code-(code-after)%minimum;
                        if(value>255) throw new ProtocolException("Invalid range-coder right value");
                        symbol=create(value,update);node.right=symbol-index;
                    } else if(code<after-before) {
                        node.under+=update;
                        if(node.left!=0) {index+=node.left;continue;}
                        if(minimum==0) throw new ProtocolException("Missing range-coder left symbol");
                        value=node.value-1-(after-before-code-1)/minimum;under=code-(after-before-code-1)%minimum;
                        if(value<0) throw new ProtocolException("Invalid range-coder left value");
                        symbol=create(value,update);node.left=symbol-index;
                    } else {
                        value=node.value;count+=node.count;under=after-before;node.under+=update;node.count+=update;symbol=index;
                    }
                    break;
                }
            }
            return new Coding(symbol,under,count,value);
        }
    }
    private static final class Encoder {
        long low,range=MASK;
        final ByteArrayOutputStream output=new ByteArrayOutputStream();
        void encode(int under,int count,int total) {
            range/=total;low=(low+under*range)&MASK;range=(range*count)&MASK;
            while(true) {
                if((low^((low+range)&MASK))>=TOP) {
                    if(range>=BOTTOM) break;
                    range=(-low)&(BOTTOM-1);
                }
                output.write((int)(low>>>24));range=(range<<8)&MASK;low=(low<<8)&MASK;
            }
        }
        byte[] finish() { while(low!=0) {output.write((int)(low>>>24));low=(low<<8)&MASK;}return output.toByteArray(); }
    }
    public static byte[] encode(byte[] input) {
        if(input.length==0) return new byte[0];
        var model=new Model();var encoder=new Encoder();
        for(byte raw:input) {
            int value=raw&255,parent=-1,context=model.predicted;boolean encoded=false;
            while(context!=0) {
                var c=model.symbols[context];var code=model.encode(context,value,2,0);
                model.setParent(parent,code.symbol);parent=code.symbol;
                int total=c.total;
                if(code.count>0) encoder.encode(c.escapes+code.under,code.count,total);
                else {
                    if(c.escapes>0 && c.escapes<total) encoder.encode(0,c.escapes,total);
                    c.escapes+=5;c.total+=5;
                }
                c.total+=2;
                if(code.count>251 || c.total>65280) model.rescale(context,0);
                if(code.count>0) {encoded=true;break;}
                context=c.parent;
            }
            if(!encoded) {
                var root=model.symbols[0];var code=model.encode(0,value,3,1);
                model.setParent(parent,code.symbol);
                encoder.encode(root.escapes+code.under,code.count,root.total);root.total+=3;
                if(code.count>250 || root.total>65280) model.rescale(0,1);
            }
            model.finishByte();
        }
        return encoder.finish();
    }
    private static final class Decoder {
        long low,code,range=MASK;
        final byte[] input;
        int position;
        Decoder(byte[] input) {
            this.input=input;
            for(int i=0;i<4;i++) {code=(code<<8)|read();}
        }
        private int read() { return position<input.length?input[position++]&255:0; }
        int readCode(int total) {
            if(total<=0 || total>65535) throw new ProtocolException("Invalid range-coder total");
            range/=total;
            if(range==0) throw new ProtocolException("Invalid range-coder range");
            long value=((code-low)&MASK)/range;
            if(value>=total) throw new ProtocolException("Range-coder code outside distribution");
            return (int)value;
        }
        void decode(int under,int count) {
            low=(low+under*range)&MASK;range=(range*count)&MASK;
            if(range==0) throw new ProtocolException("Empty range-coder interval");
            while(true) {
                if((low^((low+range)&MASK))>=TOP) {
                    if(range>=BOTTOM) break;
                    range=(-low)&(BOTTOM-1);
                }
                code=((code<<8)|read())&MASK;range=(range<<8)&MASK;low=(low<<8)&MASK;
            }
        }
    }
    public static byte[] decode(byte[] input,int limit) {
        if(limit<0) throw new IllegalArgumentException("Negative range output limit");
        if(input.length==0) return new byte[0];
        var model=new Model();var decoder=new Decoder(input);var output=new ByteArrayOutputStream();
        while(true) {
            int context=model.predicted,parent=-1,bottom=-1,value=0;
            while(context!=0) {
                Symbol c=model.symbols[context];
                if(c.escapes>0 && c.escapes<c.total) {
                    int code=decoder.readCode(c.total);
                    if(code<c.escapes) decoder.decode(0,c.escapes);
                    else {
                        var coding=model.decode(context,code-c.escapes,2,0);bottom=coding.symbol;value=coding.value;
                        decoder.decode(c.escapes+coding.under,coding.count);c.total+=2;
                        if(coding.count>251 || c.total>65280) model.rescale(context,0);
                        break;
                    }
                }
                context=c.parent;
            }
            if(bottom<0) {
                Symbol root=model.symbols[0];int code=decoder.readCode(root.total);
                if(code<root.escapes) {decoder.decode(0,root.escapes);break;}
                var coding=model.decode(0,code-root.escapes,3,1);bottom=coding.symbol;value=coding.value;
                decoder.decode(root.escapes+coding.under,coding.count);root.total+=3;
                if(coding.count>250 || root.total>65280) model.rescale(0,1);
            }
            int patch=model.predicted;
            while(patch!=context) {
                Symbol c=model.symbols[patch];var coding=model.encode(patch,value,2,0);
                model.setParent(parent,coding.symbol);parent=coding.symbol;
                if(coding.count<=0) {c.escapes+=5;c.total+=5;}
                c.total+=2;
                if(coding.count>251 || c.total>65280) model.rescale(patch,0);
                patch=c.parent;
            }
            model.setParent(parent,bottom);
            if(output.size()==limit) throw new ProtocolException("Range-coder output exceeds limit");
            output.write(value);model.finishByte();
        }
        return output.toByteArray();
    }
}
