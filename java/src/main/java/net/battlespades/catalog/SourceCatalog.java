// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.tomlj.*;

/** Immutable source data exported separately from the handwritten Java policies. */
public final class SourceCatalog {
    private SourceCatalog() {}
    public static TomlParseResult load(String name) {
        try(var stream=Objects.requireNonNull(SourceCatalog.class.getResourceAsStream(name+".toml"))){
            var result=Toml.parse(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
            if(result.hasErrors())throw new IllegalStateException("Catalog "+name+": "+result.errors());
            return result;
        }catch(IOException error){throw new IllegalStateException("Cannot read catalog "+name,error);}
    }
    public static List<Integer> integers(TomlArray values){
        var result=new ArrayList<Integer>();for(int i=0;i<values.size();i++)result.add(Math.toIntExact(values.getLong(i)));return List.copyOf(result);
    }
    public static List<String> strings(TomlArray values){return values.toList().stream().map(String.class::cast).toList();}
}
