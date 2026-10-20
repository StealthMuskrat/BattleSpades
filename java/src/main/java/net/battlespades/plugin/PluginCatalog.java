// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.plugin;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import org.tomlj.TomlTable;
/** Worker-side trusted top-level JAR discovery. Providers instantiate only on the owner. */
public final class PluginCatalog implements AutoCloseable {
    public record Settings(boolean enabled,Path directory,Set<String> allow,Set<String> deny,double tickBudgetMillis){
        public Settings{Objects.requireNonNull(directory);allow=normalize(allow);deny=normalize(deny);if(!Double.isFinite(tickBudgetMillis))throw new IllegalArgumentException("Plugin budget must be finite");tickBudgetMillis=Math.max(.1,tickBudgetMillis);}
        public static Settings from(TomlTable t,Path root){Object budget=t.get("network.plugin_event_budget_ms");if(budget!=null&&!(budget instanceof Number))throw new IllegalArgumentException("Plugin budget must be numeric");return new Settings(t.getBoolean("plugins.enabled",()->true),root.resolve(t.getString("plugins.path",()->"plugins")),names(t,"plugins.allowlist"),names(t,"plugins.denylist"),budget==null?2:((Number)budget).doubleValue());}
        private static Set<String> names(TomlTable t,String key){var array=t.getArray(key);var names=new LinkedHashSet<String>();if(array!=null)for(int i=0;i<array.size();i++)names.add(String.valueOf(array.get(i)));return names;}
        private static Set<String> normalize(Collection<String> values){var result=new HashSet<String>();for(String v:values)if(!net.battlespades.player.NameUnicode.strip(v).isEmpty())result.add(net.battlespades.player.NameUnicode.casefold(net.battlespades.player.NameUnicode.strip(v)));return Set.copyOf(result);}
        public boolean permits(String stem,String name){var ids=new HashSet<>(List.of(net.battlespades.player.NameUnicode.casefold(stem),net.battlespades.player.NameUnicode.casefold(net.battlespades.player.NameUnicode.strip(name))));return Collections.disjoint(ids,deny)&&(allow.isEmpty()||!Collections.disjoint(ids,allow));}
    }
    public record Candidate(String stem,Supplier<RuntimePlugin> factory){}
    private final List<Candidate> candidates;private final List<URLClassLoader> loaders;private boolean closed;
    private PluginCatalog(List<Candidate> candidates,List<URLClassLoader> loaders){this.candidates=List.copyOf(candidates);this.loaders=List.copyOf(loaders);}
    public List<Candidate> candidates(){if(closed)throw new IllegalStateException("Plugin catalog closed");return candidates;}
    public static PluginCatalog prepare(Settings settings,BiConsumer<String,Throwable> failure)throws IOException{
        var candidates=new ArrayList<Candidate>();var loaders=new ArrayList<URLClassLoader>();if(!settings.enabled()||!Files.isDirectory(settings.directory()))return new PluginCatalog(candidates,loaders);Path root=settings.directory().toRealPath();
        try(var files=Files.list(root)){for(Path path:files.filter(p->p.getFileName().toString().endsWith(".jar")).sorted(Comparator.comparing(p->p.getFileName().toString())).toList()){
            String filename=path.getFileName().toString(),stem=filename.substring(0,filename.length()-4);if(stem.startsWith("_")||stem.equals("base_plugin")||settings.deny().contains(net.battlespades.player.NameUnicode.casefold(stem)))continue;Path resolved;try{resolved=path.toRealPath();}catch(IOException invalid){failure.accept("plugin path:"+filename,invalid);continue;}if(!resolved.startsWith(root)){failure.accept("plugin outside root:"+filename,new IOException("Plugin escapes configured directory"));continue;}URLClassLoader loader=null;
            try{try(var jar=new java.util.jar.JarFile(resolved.toFile())){if(jar.getJarEntry("META-INF/services/"+RuntimePlugin.class.getName())==null)continue;}loader=new URLClassLoader(new URL[]{resolved.toUri().toURL()},RuntimePlugin.class.getClassLoader());var providers=ServiceLoader.load(RuntimePlugin.class,loader).stream().sorted(Comparator.comparing(p->p.type().getName())).toList();var found=new ArrayList<Candidate>();for(var provider:providers){var source=provider.type().getProtectionDomain().getCodeSource();if(source==null||!source.getLocation().toURI().equals(resolved.toUri()))continue;found.add(new Candidate(stem,provider::get));}candidates.addAll(found);loaders.add(loader);loader=null;}
            catch(IOException|ServiceConfigurationError|LinkageError|RuntimeException|URISyntaxException invalid){failure.accept("plugin jar:"+filename,invalid);}finally{if(loader!=null)loader.close();}
        }}catch(IOException|RuntimeException|Error invalid){for(var loader:loaders)try{loader.close();}catch(IOException cleanup){invalid.addSuppressed(cleanup);}throw invalid;}return new PluginCatalog(candidates,loaders);
    }
    @Override public void close()throws IOException{if(closed)return;closed=true;IOException problem=null;for(var loader:loaders)try{loader.close();}catch(IOException error){if(problem==null)problem=error;else problem.addSuppressed(error);}if(problem!=null)throw problem;}
}
