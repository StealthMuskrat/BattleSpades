// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.world.MetadataDocument;
import static net.battlespades.bot.BotFoundationsTest.*;
final class BotSourceFixture {
 private BotSourceFixture(){}
 static Map<String,Object> read(String name){try(var in=Objects.requireNonNull(BotSourceFixture.class.getResourceAsStream("/golden/"+name+".json"))){var r=map(MetadataDocument.json(new String(in.readAllBytes(),StandardCharsets.UTF_8)));if("zlib-json-v1".equals(r.get("encoding"))){try(var zip=new java.util.zip.InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(string(r.get("data")))))){r=map(MetadataDocument.json(new String(zip.readAllBytes(),StandardCharsets.UTF_8)));}}return map(expand(r,list(r.get("definitions")),new HashMap<>()));}catch(IOException e){throw new UncheckedIOException(e);}}
 static Object expand(Object value,List<?> definitions,Map<Integer,Object> cache){if(value instanceof Map<?,?> m){if(m.size()==1&&m.containsKey("$ref")){int key=integer(m.get("$ref"));if(cache.containsKey(key))return cache.get(key);var result=expand(definitions.get(key),definitions,cache);cache.put(key,result);return result;}var result=new LinkedHashMap<String,Object>();m.forEach((k,v)->{if(!k.equals("definitions"))result.put(k.toString(),expand(v,definitions,cache));});return Collections.unmodifiableMap(result);}if(value instanceof List<?> l)return l.stream().map(v->expand(v,definitions,cache)).toList();return value;}
 static Object observe(Object v){if(v instanceof Double d&&!Double.isFinite(d))return d>0?"infinity":"-infinity";if(v instanceof BotState.Cell c)return List.of(c.x(),c.y(),c.z());if(v instanceof Vector p)return List.of(p.x(),p.y(),p.z());if(v instanceof TeamTasks.Identity p)return List.of(p.id(),p.generation(),p.life());if(v instanceof Enum<?> e)return e.name().toLowerCase(Locale.ROOT);if(v instanceof Record r){var out=new LinkedHashMap<String,Object>();try{for(var c:r.getClass().getRecordComponents())out.put(c.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT),observe(c.getAccessor().invoke(r)));}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}return out;}if(v instanceof Map<?,?> m){var out=new LinkedHashMap<Object,Object>();m.forEach((k,x)->out.put(k,observe(x)));return out;}if(v instanceof Collection<?> c)return c.stream().map(BotSourceFixture::observe).toList();return v;}
 static void matches(Object expected,Object actual){equal(expected,observe(actual));}
}
