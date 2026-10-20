// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.*;

/** Deterministic JSON data encoder shared by inert metadata and durable service records. */
public final class JsonData {
    private JsonData(){}
    public static String encode(Object value){var out=new StringBuilder();write(out,value);return out.append('\n').toString();}
    private static void write(StringBuilder out,Object value){
        if(value==null){out.append("null");return;}
        if(value instanceof String s){out.append('"');for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){case '"'->out.append("\\\"");case '\\'->out.append("\\\\");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}}}out.append('"');return;}
        if(value instanceof Number n){if(n instanceof Double d&&!Double.isFinite(d)||n instanceof Float f&&!Float.isFinite(f))throw new IllegalArgumentException("Nonfinite JSON number");out.append(n);return;}
        if(value instanceof Boolean){out.append(value);return;}
        if(value instanceof Map<?,?> m){out.append('{');boolean first=true;for(var e:m.entrySet()){if(!first)out.append(',');first=false;write(out,e.getKey().toString());out.append(':');write(out,e.getValue());}out.append('}');return;}
        if(value instanceof Iterable<?> rows){out.append('[');boolean first=true;for(var row:rows){if(!first)out.append(',');first=false;write(out,row);}out.append(']');return;}
        throw new IllegalArgumentException("Unsupported JSON value");
    }
}
