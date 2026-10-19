// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.app;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import net.battlespades.config.ServerConfig;

/** Migration diagnostic launcher. Refuses gameplay startup until compatibility is implemented. */
public final class Main {
    private Main() {}
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    public static int run(String[] args, PrintStream out, PrintStream error) {
        if (args.length == 1 && args[0].equals("--help")) {
            out.println("BattleSpades Java 25 migration (not a playable server yet)");
            out.println("  java -jar BattleSpades.jar --check-config PATH");
            out.println("  java -jar BattleSpades.jar --version");
            out.println("  java -jar BattleSpades.jar --ugc-assets RETAIL_ROOT");
            out.println("  java -jar BattleSpades.jar --ugc-create RETAIL_ROOT DIRECTORY NAME TERRAIN MODE");
            out.println("  java -jar BattleSpades.jar --ugc-check DIRECTORY NAME");
            return 0;
        }
        if(args.length>0&&args[0].startsWith("--ugc-"))return net.battlespades.ugc.UgcCommands.run(args,out,error);
        if (args.length == 1 && args[0].equals("--version")) {
            out.println("BattleSpades 0.1.0-java25-dev; protocol 168; gameplay incomplete");
            return 0;
        }
        if (args.length == 2 && args[0].equals("--check-config")) {
            try {
                var config = ServerConfig.load(Path.of(args[1]));
                net.battlespades.scene.SceneConfig.from(config);
                if (!config.unsupportedIntegrations().isEmpty()) {
                    error.println("Unsupported native integrations enabled: " + config.unsupportedIntegrations());
                    return 2;
                }
                out.printf("Core configuration valid: %s, UDP %d, %d players, 60 Hz%n", config.name(), config.port(), config.maxPlayers());
                out.println("Validation covers core/network/join, Match Lobby rules and common scene settings; full gameplay is pending.");
                return 0;
            } catch (IOException | IllegalArgumentException exception) {
                error.println("Configuration error: " + exception.getMessage());
                return 2;
            }
        }
        error.println("Java migration incomplete: a configured gameplay scene, player simulation and game modes are not available.");
        error.println("Use --help for diagnostics. Continue using the reference server for gameplay.");
        return 2;
    }
}
