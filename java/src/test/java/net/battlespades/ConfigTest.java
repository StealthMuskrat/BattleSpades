// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.battlespades.app.Main;
import net.battlespades.config.ServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @TempDir Path temp;
    private Path config(String data) throws IOException { Path file=temp.resolve("config.toml");Files.writeString(file,data);return file; }
    @Test void defaultsAndRawNestedValuesSurvive() throws IOException {
        var c=ServerConfig.load(config("[server]\nname=\"café\"\n[custom]\narray=[1,2,3]\n"));
        assertEquals("café",c.name());assertEquals(27015,c.port());assertEquals(24,c.maxPlayers());
        assertEquals(3,c.raw().getArray("custom.array").size());assertEquals(90087911866072064L,c.steamId());
    }
    @Test void invalidSyntaxTypeAndBoundsFailAtStartup() {
        for(String data:new String[]{"[server]\nport=0","[server]\nport=65536","[server]\nport=1.2","[server]\nmax_players=256","[server]\ntick_rate=120","[server]\nport=\"27015\"","= malformed"}) {
            assertThrows(IllegalArgumentException.class,()->ServerConfig.load(config(data)));
        }
    }
    @Test void nativeIntegrationCannotBeSilentlyIgnored() throws IOException {
        Path file=config("[steam]\nenabled=true\n");
        var c=ServerConfig.load(file);assertEquals(java.util.List.of("steam.enabled"),c.unsupportedIntegrations());
        assertEquals(2,Main.run(new String[]{"--check-config",file.toString()},System.out,System.err));
    }
    @Test void launcherDoesNotAdvertisePlayableServer() {
        var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();
        assertEquals(2,Main.run(new String[0],new PrintStream(output),new PrintStream(error)));
        assertTrue(error.toString(java.nio.charset.StandardCharsets.UTF_8).contains("incomplete"));
    }
}
