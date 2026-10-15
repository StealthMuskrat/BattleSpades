// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.math.BigInteger;
import java.util.*;

/** Bounded data grammar. Legacy expressions never resolve names, call code or access attributes. */
public final class MetadataDocument {
    public static final int MAX_BYTES=8*1024*1024;
    private MetadataDocument(){}
    public static Object json(String text){return new Parser(text,false).document();}
    public static Map<String,Object> sidecar(String text,Set<String> legacyKeys){
        if(text.startsWith("\ufeff"))text=text.substring(1);
        try {Object value=json(text);return value instanceof Map<?,?> m?stringMap(m):null;}
        catch(IllegalArgumentException ignored){return legacy(text,legacyKeys);}
    }
    private static Map<String,Object> stringMap(Map<?,?> source){
        var result=new LinkedHashMap<String,Object>();source.forEach((k,v)->result.put(String.valueOf(k),v));return Collections.unmodifiableMap(result);
    }
    private static Map<String,Object> legacy(String text,Set<String> keys){
        if(text.length()>MAX_BYTES)throw new IllegalArgumentException("Metadata document too large");
        var result=new LinkedHashMap<String,Object>();
        int start=0,depth=0;char quote=0;boolean triple=false,escape=false,comment=false;
        for(int i=0;i<=text.length();i++){
            char c=i<text.length()?text.charAt(i):'\n';
            if(comment){if(c!='\n')continue;comment=false;}
            if(quote!=0){
                if(escape){escape=false;continue;}if(c=='\\'){escape=true;continue;}
                if(c==quote){if(!triple)quote=0;else if(i+2<text.length()&&text.charAt(i+1)==c&&text.charAt(i+2)==c){quote=0;i+=2;}}
                continue;
            }
            if(c=='#'){comment=true;continue;}
            if(c=='\''||c=='"'){quote=c;triple=i+2<text.length()&&text.charAt(i+1)==c&&text.charAt(i+2)==c;if(triple)i+=2;continue;}
            if(c=='('||c=='['||c=='{')depth++;
            if(c==')'||c==']'||c=='}'){if(--depth<0)return null;}
            if((c=='\n'||c==';')&&depth==0){
                String statement=text.substring(start,i);if(start>0&&text.charAt(start-1)==';')statement=statement.stripLeading();start=i+1;
                if(statement.isBlank()||Character.isWhitespace(statement.charAt(0)))continue;
                int equal=statement.indexOf('=');if(equal<0)continue;
                String key=statement.substring(0,equal).strip();if(!keys.contains(key))continue;
                try {result.put(key,new Parser(statement.substring(equal+1),true).document());}
                catch(IllegalArgumentException ignored){/* The source ignores non-inert assignment values. */}
            }
        }
        return quote!=0||depth!=0||result.isEmpty()?null:Collections.unmodifiableMap(result);
    }
    private static final class Parser {
        final String text;final boolean python;int index,nodes;
        Parser(String text,boolean python){if(text.length()>MAX_BYTES)throw bad();this.text=text;this.python=python;}
        IllegalArgumentException bad(){return new IllegalArgumentException("Invalid or unsupported metadata literal at "+index);}
        void space(){
            while(index<text.length()){
                char c=text.charAt(index);if(Character.isWhitespace(c)){index++;continue;}
                if(python&&c=='#'){while(index<text.length()&&text.charAt(index)!='\n')index++;continue;}break;
            }
        }
        boolean take(char c){space();if(index<text.length()&&text.charAt(index)==c){index++;return true;}return false;}
        void need(char c){if(!take(c))throw bad();}
        Object document(){Object value=value(0);if(python&&take(',')){var tuple=new ArrayList<Object>();tuple.add(value);space();while(index<text.length()){tuple.add(value(1));bound(tuple.size());if(!take(','))break;space();}value=Collections.unmodifiableList(tuple);}space();if(index!=text.length())throw bad();return value;}
        Object value(int depth){
            Object v=atom(depth);
            if(python)while(true){space();if(index==text.length()||(text.charAt(index)!='+'&&text.charAt(index)!='-'))break;char op=text.charAt(index++);Object right=atom(depth+1);if(!(v instanceof Number a)||!(right instanceof Number b))throw bad();v=arithmetic(a,b,op);}
            return v;
        }
        Object atom(int depth){
            if(depth>(python?16:64)||++nodes>1_000_000)throw bad();space();if(index==text.length())throw bad();char c=text.charAt(index);
            if(c=='"'||python&&c=='\''){String result=string();if(python){space();while(index<text.length()&&(text.charAt(index)=='\''||text.charAt(index)=='"')){result+=string();space();}}return result;}
            if(c=='['){index++;return sequence(']',depth);}
            if(python&&c=='('){index++;if(take(')'))return List.of();Object first=value(depth+1);if(take(')'))return first;need(',');var values=new ArrayList<Object>();values.add(first);while(!take(')')){values.add(value(depth+1));bound(values.size());if(!take(',')){need(')');break;}}return Collections.unmodifiableList(values);}
            if(c=='{'){
                index++;var map=new LinkedHashMap<Object,Object>();if(take('}'))return Collections.unmodifiableMap(map);
                Object key=value(depth+1);
                if(python&&!take(':')){var values=new LinkedHashSet<Object>();values.add(key);while(!take('}')){need(',');if(take('}'))break;values.add(value(depth+1));bound(values.size());}return Collections.unmodifiableSet(values);}
                if(!python){if(!(key instanceof String))throw bad();need(':');}
                map.put(key,value(depth+1));
                while(!take('}')){need(',');if(python&&take('}'))break;key=value(depth+1);if(!python&&!(key instanceof String))throw bad();need(':');map.put(key,value(depth+1));bound(map.size());}return Collections.unmodifiableMap(map);
            }
            if(python&&(c=='+'||c=='-')){index++;Object v=atom(depth+1);if(!(v instanceof Number n))throw bad();if(c=='+')return n;if(n instanceof BigInteger b)return b.negate();if(n instanceof Double)return -n.doubleValue();return -n.longValue();}
            for(String name:python?List.of("True","False","None"):List.of("true","false","null","NaN","Infinity","-Infinity")){
                if(text.startsWith(name,index)){index+=name.length();return switch(name){case "True","true"->true;case "False","false"->false;case "None","null"->null;case "NaN"->Double.NaN;case "Infinity"->Double.POSITIVE_INFINITY;default->Double.NEGATIVE_INFINITY;};}
            }
            int begin=index;
            if(!python&&c=='-')index++;
            while(index<text.length()){
                char n=text.charAt(index);if(Character.isLetterOrDigit(n)||n=='.'||python&&n=='_'||(n=='+'||n=='-')&&index>begin&&(text.charAt(index-1)=='e'||text.charAt(index-1)=='E'))index++;else break;
            }
            if(index==begin)throw bad();String number=text.substring(begin,index);
            try {
                if(!python&&!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw bad();
                number=number.replace("_","");
                if(python&&number.matches("0[xX][0-9a-fA-F]+"))return integer(number.substring(2),16);
                if(python&&number.matches("0[bB][01]+"))return integer(number.substring(2),2);
                if(python&&number.matches("0[oO][0-7]+"))return integer(number.substring(2),8);
                if(python&&number.matches("0[0-9]+")&&!number.matches("0+"))throw bad();
                return number.contains(".")||number.contains("e")||number.contains("E")?Double.valueOf(number):integer(number,10);
            }catch(NumberFormatException e){throw bad();}
        }
        Object sequence(char end,int depth){var out=new ArrayList<Object>();if(take(end))return Collections.unmodifiableList(out);while(true){out.add(value(depth+1));bound(out.size());if(take(end))break;need(',');if(python&&take(end))break;}return Collections.unmodifiableList(out);}
        void bound(int n){if(n>(python?4096:1_000_000))throw bad();}
        Object integer(String value,int radix){if(value.length()>4300)throw bad();BigInteger n=new BigInteger(value,radix);return n.bitLength()<64?n.longValue():n;}
        Object arithmetic(Number a,Number b,char op){if(a instanceof Double||b instanceof Double)return op=='+'?a.doubleValue()+b.doubleValue():a.doubleValue()-b.doubleValue();BigInteger left=new BigInteger(a.toString()),right=new BigInteger(b.toString());return op=='+'?left.add(right):left.subtract(right);}
        String string(){
            char quote=text.charAt(index++);boolean triple=python&&index+1<text.length()&&text.charAt(index)==quote&&text.charAt(index+1)==quote;if(triple)index+=2;
            var out=new StringBuilder();
            while(index<text.length()){
                char c=text.charAt(index++);
                if(c==quote){if(!triple)return out.toString();if(index+1<text.length()&&text.charAt(index)==quote&&text.charAt(index+1)==quote){index+=2;return out.toString();}}
                if(c=='\\'){
                    if(index==text.length())throw bad();char escaped=text.charAt(index++);
                    switch(escaped){
                        case '"','\\'->out.append(escaped);
                        case '\''->{if(!python)throw bad();out.append(escaped);}
                        case '/'->{if(python)out.append('\\');out.append('/');}
                        case 'n'->out.append('\n');case 'r'->out.append('\r');case 't'->out.append('\t');case 'b'->out.append('\b');case 'f'->out.append('\f');
                        case 'a'->{if(!python)throw bad();out.append('\7');}case 'v'->{if(!python)throw bad();out.append('\13');}
                        case '\n'->{if(!python)throw bad();}
                        case 'u','U','x'->{if(!python&&escaped!='u')throw bad();int digits=escaped=='U'?8:escaped=='x'?2:4;if(index+digits>text.length())throw bad();try{int cp=Integer.parseInt(text.substring(index,index+digits),16);if(!Character.isValidCodePoint(cp))throw bad();out.appendCodePoint(cp);index+=digits;}catch(NumberFormatException e){throw bad();}}
                        default->{if(!python)throw bad();if(escaped>='0'&&escaped<='7'){int octal=escaped-'0',n=1;while(n<3&&index<text.length()&&text.charAt(index)>='0'&&text.charAt(index)<='7'){octal=octal*8+text.charAt(index++)-'0';n++;}out.append((char)octal);}else out.append('\\').append(escaped);}
                    }
                }else {if(!python&&c<32||python&&!triple&&(c=='\n'||c=='\r'))throw bad();out.append(c);}
            }
            throw bad();
        }
    }
}
