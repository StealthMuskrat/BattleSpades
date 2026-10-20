// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/** Initial composition root. Full raw TOML retained; only listed settings have behavioral parity. */
public record ServerConfig(String name, int port, int maxPlayers, long steamId,
        int maxConnections, int incomingCapacity, int drainBudget,
        boolean requireProtocolVersion, Path root, TomlParseResult raw) {
    public static ServerConfig load(Path path) throws IOException {
        var raw = Toml.parse(path);
        if (raw.hasErrors()) throw new IllegalArgumentException("Invalid TOML: " + raw.errors());
        String name = text(raw, "server.name", "BattleSpades Server");
        int port = bounded(raw, "server.port", 27015, 1, 65535);
        int players = bounded(raw, "server.max_players", 24, 1, 255);
        int rate = bounded(raw, "server.tick_rate", 60, 1, 240);
        if (rate != 60) throw new IllegalArgumentException("server.tick_rate must be 60 for protocol 168");
        net.battlespades.plugin.PluginCatalog.Settings.from(raw,path.toAbsolutePath().getParent());
        net.battlespades.session.HostedDiscovery.Settings.from(raw,port,integer(raw,"server.steam_id",90087911866072064L));
        net.battlespades.service.LoggingRuntime.Settings.from(raw,path.toAbsolutePath().getParent().resolve("logs"));
        net.battlespades.session.JoinSettings.from(raw);
        net.battlespades.replication.ReplicationService.Settings.from(raw);
        net.battlespades.combat.LagCompensation.Settings.from(raw);
        return new ServerConfig(name, port, players, integer(raw, "server.steam_id", 90087911866072064L),
            bounded(raw, "network.max_connections", 64, 1, 4095),
            bounded(raw, "network.max_pending_packets", 4096, 1, 1_000_000),
            bounded(raw, "network.packet_drain_budget", 4096, 1, 1_000_000),
            bool(raw, "network.require_protocol_version", true), path.toAbsolutePath().normalize().getParent(), raw);
    }
    private static int bounded(TomlParseResult t, String key, int fallback, int min, int max) {
        long value = integer(t, key, fallback);
        if (value < min || value > max) throw new IllegalArgumentException(key + " outside " + min + ".." + max);
        return (int)value;
    }
    private static long integer(TomlParseResult t, String key, long fallback) {
        Object value = t.get(key);
        if (value == null) return fallback;
        if (value instanceof Long number) return number;
        throw new IllegalArgumentException(key + " must be an integer");
    }
    private static boolean bool(TomlParseResult t, String key, boolean fallback) {
        Object value = t.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean b) return b;
        throw new IllegalArgumentException(key + " must be a boolean");
    }
    private static String text(TomlParseResult t, String key, String fallback) {
        Object value = t.get(key);
        if (value == null) return fallback;
        if (value instanceof String s) return s;
        throw new IllegalArgumentException(key + " must be a string");
    }
    /** Optional original integrations requiring native services cannot be enabled. */
    public List<String> unsupportedIntegrations() {
        return List.of("steam.enabled", "steam_host.enabled").stream()
            .filter(key -> bool(raw, key, false)).toList();
    }
    public net.battlespades.session.JoinSettings joinSettings() { return net.battlespades.session.JoinSettings.from(raw); }
    public Path resolve(String relative) { return root.resolve(relative).normalize(); }
}
