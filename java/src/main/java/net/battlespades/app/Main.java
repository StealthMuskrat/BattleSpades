// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.app;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import net.battlespades.config.ServerConfig;

/** Portable dedicated hosting, world creation and migration diagnostics. */
public final class Main {
    public static final String SOURCE_VERSION="0.3.0-beta.2";
    private Main() {}
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    public static int run(String[] args, PrintStream out, PrintStream error) {
        if (args.length == 1 && args[0].equals("--help")) {
            out.println("BattleSpades Java 25: dedicated host, world creator and migration diagnostics");
            out.println("  java -jar BattleSpades.jar --serve --config PATH [--bind HOST] [--port PORT] [--control-stdin] [--shutdown-after SECONDS]");
            out.println("  java -jar BattleSpades.jar --check-config PATH");
            out.println("  java -jar BattleSpades.jar --version");
            out.println("  java -jar BattleSpades.jar --update [--config PATH] [--manifest URL]");
            out.println("  java -jar BattleSpades.jar --results-import INPUT_NDJSON OUTBOX_DIRECTORY");
            out.println("  java -jar BattleSpades.jar --ugc-assets RETAIL_ROOT");
            out.println("  java -jar BattleSpades.jar --ugc-create RETAIL_ROOT DIRECTORY NAME TERRAIN MODE");
            out.println("  java -jar BattleSpades.jar --ugc-check DIRECTORY NAME");
            out.println("  java -jar BattleSpades.jar --ugc-host --retail-root RETAIL_ROOT --project NAME [--config PATH] [--publish-root hosted_ugc] [--control-stdin]");
            return 0;
        }
        if(args.length>0&&args[0].equals("--update")){
            try{Path config=Path.of("config.toml");String manifest=null;for(int i=1;i<args.length;i++){if(i+1>=args.length)throw new IllegalArgumentException("Missing update option value");switch(args[i++]){case "--config"->config=Path.of(args[i]);case "--manifest"->manifest=args[i];default->throw new IllegalArgumentException("Unknown update option");}}var core=ServerConfig.load(config);if(manifest==null)manifest=core.raw().getString("updates.update_manifest_url",()->net.battlespades.update.ServerUpdates.DEFAULT_MANIFEST);return net.battlespades.update.ServerUpdates.run(core.root(),SOURCE_VERSION,manifest,out::println);}catch(IOException|IllegalArgumentException failure){error.println("Update failed: "+failure.getMessage());return 1;}
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
        return ServerCommands.run(args,System.in,out,error);
    }
}
