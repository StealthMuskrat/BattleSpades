// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;

/** Python-2-compatible string/tuple literals for the stock map-vote HUD. */
public final class VoteText {
    private VoteText(){}
    private static String literal(String value){
        char quote=value.indexOf('\'')>=0&&value.indexOf('"')<0?'"':'\'';var out=new StringBuilder();if(value.codePoints().anyMatch(cp->cp>127))out.append('u');out.append(quote);
        value.codePoints().forEach(cp->{if(cp==quote||cp=='\\')out.append('\\').appendCodePoint(cp);else if(cp=='\n')out.append("\\n");else if(cp=='\r')out.append("\\r");else if(cp=='\t')out.append("\\t");else if(cp<32||cp>=127){int digits=cp<=255?2:cp<=65535?4:8;out.append(digits==2?"\\x":digits==4?"\\u":"\\U");String hex=Integer.toHexString(cp);out.append("0".repeat(digits-hex.length())).append(hex);}else out.appendCodePoint(cp);});return out.append(quote).toString();
    }
    public static String localised(String identifier,List<String> arguments){
        if(!identifier.matches("[A-Z0-9_]+")||identifier.replace("_","").isEmpty())throw new IllegalArgumentException("Localization identifier");
        var args=arguments.stream().map(VoteText::literal).toList();return "("+literal(identifier)+", ("+String.join(", ",args)+(args.size()==1?",":"")+"))";
    }
    public static String dynamic(String value){
        Objects.requireNonNull(value);if(value.isEmpty()||value.codePointCount(0,value.length())>128||value.indexOf(0)>=0||value.indexOf('\r')>=0||value.indexOf('\n')>=0)throw new IllegalArgumentException("Invalid retail dynamic text");
        return "("+literal(value.replace("{","{{").replace("}","}}"))+", ())";
    }
    public static String vote(String identifier,List<String> arguments,List<Integer> localised){
        localised(identifier,List.of());for(int index:localised)if(index<0||index>=arguments.size())throw new IllegalArgumentException("Localized argument index");
        for(var argument:arguments)if(argument.indexOf(0)>=0||argument.codePointCount(0,argument.length())>128)throw new IllegalArgumentException("Vote argument bound");
        String args="("+String.join(", ",arguments.stream().map(VoteText::literal).toList())+(arguments.size()==1?",":"")+")";
        String indices="("+String.join(", ",localised.stream().map(Object::toString).toList())+(localised.size()==1?",":"")+")";
        return "("+(localised.isEmpty()?literal(identifier):"("+literal(identifier)+", "+indices+")")+", "+args+")";
    }
}
