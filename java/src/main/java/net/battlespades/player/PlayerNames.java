// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Synchronous retail display-name allocation from server/player_names.py. */
public final class PlayerNames {
    public static final int MAX_BYTES=15;
    public static final String FALLBACK="Player";
    public record Existing(String name,boolean admin){}
    // Python's Unicode 15 str.isspace/re \s, including the four information separators.
    private static final String SPACE="[\\x{9}-\\x{d}\\x{1c}-\\x{20}\\x{85}\\x{a0}\\x{1680}\\x{2000}-\\x{200a}\\x{2028}\\x{2029}\\x{202f}\\x{205f}\\x{3000}]";
    private static final Pattern TAG=Pattern.compile("[\\[\\(\\{<|]"+SPACE+"*([^\\]\\)\\}>|]{1,16}?)"+SPACE+"*[\\]\\)\\}>|]");
    private static final Set<String> RESERVED=keys(NameUnicode.RESERVED_NAMES), TAGS=keys(NameUnicode.RESERVED_TAGS);
    private PlayerNames(){}
    private static String text(String value){return value==null?"":value;}
    public static String truncateUtf8(String value,int limit){
        var out=new StringBuilder();int size=0;
        for(int cp:text(value).codePoints().toArray()){
            String c=new String(Character.toChars(cp));int bytes=c.getBytes(StandardCharsets.UTF_8).length;
            if(size+bytes>Math.max(0,limit))break;out.append(c);size+=bytes;
        }
        return out.toString();
    }
    public static String safeBase(String requested){
        var out=new StringBuilder();boolean visible=false;
        for(int cp:NameUnicode.nfkc(text(requested)).codePoints().toArray()){
            if(NameUnicode.space(cp))out.append(' ');
            else if(!NameUnicode.invisible(cp)){out.appendCodePoint(cp);if(!NameUnicode.mark(cp))visible=true;}
        }
        String base=collapseSpaces(out.toString());
        if(!visible||base.isEmpty())base=FALLBACK;
        base=truncateUtf8(base,MAX_BYTES);return base.isEmpty()?FALLBACK:base;
    }
    public static String skeleton(String name){
        var out=new StringBuilder();
        for(int cp:folded(name).codePoints().toArray()){
            if(NameUnicode.invisible(cp)||NameUnicode.mark(cp)||NameUnicode.space(cp)||"_-.'`·".indexOf(cp)>=0)continue;
            out.append(confusable(cp));
        }
        return out.toString().replace("rn","m").replace("vv","w");
    }
    private static String folded(String name){return NameUnicode.nfkd(NameUnicode.casefold(NameUnicode.nfkc(text(name))));}
    private static String confusable(int cp){
        String mapped=NameUnicode.CONFUSABLES.getOrDefault(cp,new String(Character.toChars(cp)));
        return mapped.codePointCount(0,mapped.length())==1?NameUnicode.CONFUSABLES.getOrDefault(mapped.codePointAt(0),mapped):mapped;
    }
    private static String alnum(String name){
        var out=new StringBuilder();skeleton(name).codePoints().filter(NameUnicode::alnum).forEach(out::appendCodePoint);return out.toString();
    }
    private static Set<String> keys(Collection<String> values){
        var result=new HashSet<String>();for(String value:values)result.add(alnum(value));result.remove("");return Set.copyOf(result);
    }
    public static boolean reserved(String name,Collection<String> extra){
        var reserved=new HashSet<>(RESERVED);reserved.addAll(keys(extra));if(reserved.contains(alnum(name)))return true;
        var tags=new HashSet<>(TAGS);tags.addAll(reserved);var matcher=TAG.matcher(NameUnicode.nfkc(text(name)));
        while(matcher.find())if(tags.contains(alnum(matcher.group(1))))return true;return false;
    }
    private static String keepSeparators(String name){
        var out=new StringBuilder();for(int cp:folded(name).codePoints().toArray())if(!NameUnicode.invisible(cp)&&!NameUnicode.mark(cp))out.append(confusable(cp));
        return collapseSpaces(out.toString());
    }
    private static String collapseSpaces(String value){
        var out=new StringBuilder();boolean pending=false;
        for(int cp:value.codePoints().toArray()){
            if(NameUnicode.space(cp)){pending=out.length()>0;continue;}
            if(pending)out.append(' ');out.appendCodePoint(cp);pending=false;
        }
        return out.toString();
    }
    public static boolean posesAsBot(String name,String botLabel){
        String label=strip(text(botLabel)),key=alnum(label);if(key.isEmpty())return false;
        String normalized=NameUnicode.nfkc(text(name));
        if(TAG.matcher(label).matches()){
            var matcher=TAG.matcher(normalized);while(matcher.find())if(alnum(matcher.group(1)).equals(key))return true;return false;
        }
        return keepSeparators(normalized).startsWith(keepSeparators(label));
    }
    private static String strip(String value){
        int start=0,end=value.length();
        while(start<end&&NameUnicode.space(value.codePointAt(start)))start+=Character.charCount(value.codePointAt(start));
        while(end>start&&NameUnicode.space(value.codePointBefore(end)))end-=Character.charCount(value.codePointBefore(end));
        return value.substring(start,end);
    }
    private static String unique(String head,String body,Set<String> used){
        int available=MAX_BYTES-head.getBytes(StandardCharsets.UTF_8).length;
        String candidate=head+truncateUtf8(body,available);if(!used.contains(skeleton(candidate)))return candidate;
        for(int i=2;i<10000;i++){
            String suffix="~"+i;candidate=head+truncateUtf8(body,available-suffix.length())+suffix;
            if(!used.contains(skeleton(candidate)))return candidate;
        }
        String fallback=String.format(Locale.ROOT,"P%013d",used.size());return fallback.substring(Math.max(0,fallback.length()-MAX_BYTES));
    }
    public static String allocate(String requested,Collection<Existing> players,Collection<String> extraReserved,String botLabel){
        String base=safeBase(requested);
        if(reserved(base,extraReserved)||posesAsBot(base,botLabel))base=FALLBACK;
        else{
            String key=skeleton(base);for(var player:players)if(player.admin&&skeleton(player.name).equals(key)){base=FALLBACK;break;}
        }
        return unique("",base,used(players));
    }
    public static String allocateBot(String base,String prefix,Collection<Existing> players){
        return unique(truncateUtf8(text(prefix),MAX_BYTES-3),safeBase(base),used(players));
    }
    private static Set<String> used(Collection<Existing> players){
        var result=new HashSet<String>();for(var player:players)result.add(skeleton(player.name));return result;
    }
}
