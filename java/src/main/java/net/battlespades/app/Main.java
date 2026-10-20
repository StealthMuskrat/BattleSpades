// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.app;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import net.battlespades.config.ServerConfig;

/** Portable diagnostics and standalone world creation; ordinary match startup remains gated. */
public final class Main {
    private Main() {}
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    public static int run(String[] args, PrintStream out, PrintStream error) {
        if (args.length == 1 && args[0].equals("--help")) {
            out.println("BattleSpades Java 25: world creator and migration diagnostics");
            out.println("  java -jar BattleSpades.jar --check-config PATH");
            out.println("  java -jar BattleSpades.jar --version");
            out.println("  java -jar BattleSpades.jar --results-import INPUT_NDJSON OUTBOX_DIRECTORY");
            out.println("  java -jar BattleSpades.jar --ugc-assets RETAIL_ROOT");
            out.println("  java -jar BattleSpades.jar --ugc-create RETAIL_ROOT DIRECTORY NAME TERRAIN MODE");
            out.println("  java -jar BattleSpades.jar --ugc-check DIRECTORY NAME");
            out.println("  java -jar BattleSpades.jar --ugc-host --retail-root RETAIL_ROOT --project NAME [--config PATH] [--publish-root hosted_ugc] [--control-stdin]");
            return 0;
        }
        if(args.length>0&&args[0].equals("--results-import")){
            if(args.length!=3){error.println("Usage: --results-import INPUT_NDJSON OUTBOX_DIRECTORY");return 2;}
            try{int count=new net.battlespades.results.ResultOutbox(Path.of(args[2]),null).importJsonLines(Path.of(args[1]));out.println("Imported "+count+" result envelopes; existing identical IDs are retained.");return 0;}catch(IOException|IllegalArgumentException|IllegalStateException failure){error.println("Result import failed: "+failure.getMessage());return 2;}
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
        error.println("Ordinary gameplay startup is incomplete. Use --ugc-host for world creation.");
        error.println("Use --help for commands.");
        return 2;
    }
}
