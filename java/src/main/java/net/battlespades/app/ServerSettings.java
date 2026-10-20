// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.app;
import java.nio.file.*;
import java.util.*;
import net.battlespades.config.ServerConfig;
import net.battlespades.scene.SceneConfig;
import net.battlespades.session.StandardMatchFactory;
/** Validated standalone settings. Unsupported integrations fail before opening sockets or stores. */
public record ServerSettings(ServerConfig core,SceneConfig scene,Path maps,Path prefabs,Path logs,Path bans,Path achievements,String bind,int journalLimit){
    public static ServerSettings from(ServerConfig core){var raw=core.raw();var scene=SceneConfig.from(core);net.battlespades.update.ServerUpdates.url(raw.getString("updates.update_manifest_url",()->net.battlespades.update.ServerUpdates.DEFAULT_MANIFEST));grace(raw);var missing=new ArrayList<>(core.unsupportedIntegrations());if(raw.getBoolean("bots.enabled",()->true))missing.add("bots.enabled");if(raw.getBoolean("revival.enabled",()->true))missing.add("revival.enabled");if(!StandardMatchFactory.codes().contains(scene.modeCode()))missing.add("mode "+scene.modeCode());for(String key:List.of("debug.debug_parity","debug.debug_selfrow","debug.movement_debug_capture","debug.packet_trace"))if(raw.getBoolean(key,()->false))missing.add(key);if(!missing.isEmpty())throw new IllegalArgumentException("Standalone migration does not yet support: "+String.join(", ",missing));String bind=raw.getString("server.bind",()->"0.0.0.0");int journal=Math.toIntExact(raw.getLong("network.world_journal_limit",()->65536));if(journal<1||journal>1000000)throw new IllegalArgumentException("World journal must be 1..1000000");Path achievements=raw.getBoolean("achievements.enabled",()->true)?core.resolve(raw.getString("java.achievements_path",()->"state/achievements-java.json")):null;return new ServerSettings(core,scene,core.resolve(raw.getString("world.maps_path",()->"maps")),core.resolve(raw.getString("world.prefabs_path",()->"prefabs")),core.resolve("logs"),core.resolve(raw.getString("admin.bans_path",()->"bans.json")),achievements,bind,journal);}
    public double graceSeconds(){return grace(core.raw());}
    private static double grace(org.tomlj.TomlTable raw){Object value=raw.get("lobby.transition_grace_seconds");if(value==null)return .5;if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0||n.doubleValue()>60)throw new IllegalArgumentException("Transition grace must be 0..60 seconds");return n.doubleValue();}
    public Path map(String name){if(name.isEmpty()||name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf('\0')>=0)throw new IllegalArgumentException("Unsafe map name");return maps.resolve(name+".vxl");}
}
