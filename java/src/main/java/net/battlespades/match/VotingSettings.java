// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import net.battlespades.player.NameUnicode;
import net.battlespades.player.SourceRandom;
import net.battlespades.rules.GameRules;
import org.tomlj.TomlTable;

/** Immutable lobby configuration; discovery/navigation/catalog IO is performed once by the startup worker. */
public record VotingSettings(List<String> rotation,boolean shuffle,Map<String,Object> overrides,
        MapRotation.Settings fit,KickBallot.Settings kicks) {
    public VotingSettings{rotation=List.copyOf(rotation);overrides=Map.copyOf(overrides);Objects.requireNonNull(fit);Objects.requireNonNull(kicks);}
    public static VotingSettings from(TomlTable raw,GameRules rules){
        Objects.requireNonNull(raw);Objects.requireNonNull(rules);var rotation=new ArrayList<String>();var seen=new HashSet<String>();Object configured=raw.get("lobby.map_rotation");
        if(configured!=null){if(!(configured instanceof org.tomlj.TomlArray rows))throw new IllegalArgumentException("lobby.map_rotation must be an array");for(int i=0;i<rows.size();i++){
            if(!(rows.get(i) instanceof String text))throw new IllegalArgumentException("Map rotation entries must be strings");String name=NameUnicode.strip(text);if(name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf('\0')>=0)throw new IllegalArgumentException("Unsafe map rotation name");int dot=name.lastIndexOf('.');if(dot>0)name=name.substring(0,dot);if(name.isEmpty()||name.equals(".")||name.equals(".."))throw new IllegalArgumentException("Empty map rotation name");if(seen.add(NameUnicode.casefold(name)))rotation.add(name);
        }}
        var sizes=new LinkedHashMap<String,Object>();Object overrides=raw.get("lobby.map_size_overrides");if(overrides!=null){if(!(overrides instanceof TomlTable table))throw new IllegalArgumentException("lobby.map_size_overrides must be a table");for(var entry:table.toMap().entrySet()){
            Object value=entry.getValue();if(value instanceof String text){String key=NameUnicode.strip(text).toLowerCase(Locale.ROOT);if(!Set.of("small","medium","large").contains(key))throw new IllegalArgumentException("Invalid map size class");sizes.put(entry.getKey(),key);}
            else if(value instanceof Number n&&Double.isFinite(n.doubleValue()))sizes.put(entry.getKey(),Math.clamp(n.longValue(),1,262144));else throw new IllegalArgumentException("Map size override must be a finite area or size class");
        }}
        var fit=new MapRotation.Settings(flag(raw,"map_vote_size_fit",true),number(raw,"map_vote_area_per_player",8000,1,262144),number(raw,"map_vote_min_area",24000,0,262144),integer(raw,"map_vote_recent_exclude",2,0,32),number(raw,"map_vote_bot_weight",1,0,1),flag(raw,"map_vote_retail_max_players",true));
        var kicks=new KickBallot.Settings(number(raw,"votekick_cooldown_seconds",300,0,3600),number(raw,"votekick_cancelled_cooldown_seconds",45,0,3600),integer(raw,"votekick_min_team_players",3,0,32),rules.number("RULE_VOTES_REQUIRED_FOR_KICK"));
        return new VotingSettings(rotation,flag(raw,"map_rotation_shuffle",true),sizes,fit,kicks);
    }
    /** Run on the startup/preflight worker before attaching the host. No map IO occurs during ranking or voting. */
    public MapCatalog prepareCatalog(Path root,SourceRandom random)throws IOException{return MapCatalog.load(root,rotation,shuffle,overrides,random);}
    private static boolean flag(TomlTable raw,String key,boolean fallback){Object value=raw.get("lobby."+key);if(value==null)return fallback;if(value instanceof Boolean b)return b;throw new IllegalArgumentException("lobby."+key+" must be boolean");}
    private static double number(TomlTable raw,String key,double fallback,double min,double max){Object value=raw.get("lobby."+key);if(value==null)return fallback;if(value instanceof Number n&&Double.isFinite(n.doubleValue()))return Math.max(min,Math.min(max,n.doubleValue()));throw new IllegalArgumentException("lobby."+key+" must be finite numeric");}
    private static int integer(TomlTable raw,String key,int fallback,int min,int max){Object value=raw.get("lobby."+key);if(value==null)return fallback;if(value instanceof Long n)return Math.clamp(n,min,max);throw new IllegalArgumentException("lobby."+key+" must be integer");}
}
