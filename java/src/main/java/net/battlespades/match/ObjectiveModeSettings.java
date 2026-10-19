// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.session.*;
import org.tomlj.TomlTable;
/** Shared configured round length, including the source lobby choices. */
public final class ObjectiveModeSettings {
    private ObjectiveModeSettings(){}
    public static double time(TomlTable raw,String code){String p="modes."+code+".time_limit";double time=CtfMode.Settings.number(raw,p,net.battlespades.scene.ModeCatalog.get(code).timeLimit());if(raw.get(p)==null&&raw.get("lobby.match_length_minutes")!=null){long m=raw.getLong("lobby.match_length_minutes");if(!Set.of(5L,10L,15L,20L,25L,30L,35L,40L,45L,50L,55L,60L,90L).contains(m))throw new IllegalArgumentException("Unsupported lobby match length");time=m*60.;}return Math.max(0,time);}
}
