// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.scene;

import java.util.*;
import net.battlespades.config.ServerConfig;
import net.battlespades.rules.GameRules;
import net.battlespades.player.AdmissionPolicy;
import org.tomlj.TomlTable;

/** Config fields actually consumed by the common source scene builders. */
public record SceneConfig(String name,int port,long steamId,ModeCatalog.Definition mode,String mapName,
                          boolean sameTeamCollision,boolean friendlyFire,boolean fallDamage,int scoreLimit,
                          int fogColor,String skybox,Team team1,Team team2,GameRules rules,String modeCode,AdmissionPolicy.Settings admission) {
    public record Team(String name,int color){}
    public SceneConfig {Objects.requireNonNull(rules);Objects.requireNonNull(mode);Objects.requireNonNull(admission);}
    public SceneConfig(String name,int port,long steamId,ModeCatalog.Definition mode,String mapName,boolean sameTeamCollision,boolean friendlyFire,boolean fallDamage,int scoreLimit,int fogColor,String skybox,Team team1,Team team2,GameRules rules,String modeCode){this(name,port,steamId,mode,mapName,sameTeamCollision,friendlyFire,fallDamage,scoreLimit,fogColor,skybox,team1,team2,rules,modeCode,AdmissionPolicy.Settings.defaults());}
    public SceneConfig(String name,int port,long steamId,ModeCatalog.Definition mode,String mapName,boolean sameTeamCollision,boolean friendlyFire,boolean fallDamage,int scoreLimit,int fogColor,String skybox,Team team1,Team team2,GameRules rules){this(name,port,steamId,mode,mapName,sameTeamCollision,friendlyFire,fallDamage,scoreLimit,fogColor,skybox,team1,team2,rules,mode.code());}
    public static SceneConfig from(ServerConfig core){
        var raw=core.raw();var rules=GameRules.serverDefaults();var ruleTable=raw.getTable("game_rules");if(ruleTable!=null)rules=rules.apply(ruleTable);
        double respawn=number(raw,"game.respawn_time",5);boolean fall=bool(raw,"game.fall_damage",true);
        rules=rules.compatibility(Map.of("RULE_RESPAWN_TIMES",respawn,"RULE_ENABLE_FALL_ON_WATER_DAMAGE",fall));
        String name=core.name();int count=name.codePointCount(0,name.length());if(count>31)name=name.substring(0,name.offsetByCodePoints(0,31));
        String modeCode=ModeCatalog.resolve(text(raw,"game.default_mode","tdm"));var mode=ModeCatalog.get(modeCode);
        // Hardcore uses extra rule, class and wire-profile policies. Reject until they are ported.
        if(modeCode.startsWith("hc_"))throw new IllegalArgumentException("Hardcore scene policy is not implemented");
        return new SceneConfig(name,core.port(),core.steamId(),mode,text(raw,"game.default_map","MayanJungle"),bool(raw,"game.same_team_collision",false),bool(raw,"game.friendly_fire",false),fall,(int)Math.max(0,number(raw,"game.score_limit",10)),color(raw,"world.fog_color_rgb",0x0c0d0b),normalizeSkybox(text(raw,"world.default_skybox",DEFAULT_SKYBOX)),new Team(text(raw,"teams.team1_name","TEAM1_COLOR"),color(raw,"teams.team1_color",0x2c75b3)),new Team(text(raw,"teams.team2_name","TEAM2_COLOR"),color(raw,"teams.team2_color",0x89b32c)),rules,modeCode,AdmissionPolicy.Settings.from(raw));
    }
    public static final String DEFAULT_SKYBOX="User_Grassland.txt";
    static String text(TomlTable t,String key,String fallback){Object v=t.get(key);if(v==null)return fallback;if(v instanceof String s)return s;throw new IllegalArgumentException(key+" must be text");}
    static boolean bool(TomlTable t,String key,boolean fallback){Object v=t.get(key);if(v==null)return fallback;if(v instanceof Boolean b)return b;throw new IllegalArgumentException(key+" must be boolean");}
    static double number(TomlTable t,String key,double fallback){Object v=t.get(key);if(v==null)return fallback;if(v instanceof Number n&&Double.isFinite(n.doubleValue()))return n.doubleValue();throw new IllegalArgumentException(key+" must be finite numeric");}
    static int color(TomlTable t,String key,int fallback){var array=t.getArray(key);if(array==null)return fallback;if(array.size()!=3)throw new IllegalArgumentException(key+" must contain three bytes");int color=0;for(int i=0;i<3;i++){long n=array.getLong(i);if(n<0||n>255)throw new IllegalArgumentException(key+" component");color=(color<<8)|(int)n;}return color;}
    /** Mesh path normalization follows source map_metadata.normalize_skybox_name. */
    public static String normalizeSkybox(String value){
        String normalized=validSkybox(value);return normalized!=null?normalized:DEFAULT_SKYBOX;
    }
    public static String validSkybox(String value){if(value==null)return null;String name=net.battlespades.player.NameUnicode.strip(value);return name.matches("[A-Za-z0-9][A-Za-z0-9 _.\\-]{0,62}\\.txt")?name:null;}
}
