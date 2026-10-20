// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.battlespades.app.Main;
import net.battlespades.update.ServerUpdates;
import net.battlespades.world.MetadataDocument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ServerUpdatesTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    @Test void unchangedSourceVersionAndManifestPoliciesMatch()throws Exception{
        Map<String,Object> fixture;try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/golden/updates.json"))){fixture=map(MetadataDocument.json(new String(input.readAllBytes(),StandardCharsets.UTF_8)));}
        assertEquals(fixture.get("version"),Main.SOURCE_VERSION);
        for(Object value:(List<?>)fixture.get("versions")){var row=map(value);assertEquals(row.get("newer"),ServerUpdates.newer((String)row.get("candidate"),(String)row.get("current")),row.toString());}
        for(Object value:(List<?>)fixture.get("manifests")){var row=map(value);if(!Boolean.TRUE.equals(row.get("valid"))){assertThrows(IllegalArgumentException.class,()->ServerUpdates.parse(row.get("document")),row.toString());continue;}var expected=map(row.get("release"));var actual=ServerUpdates.parse(row.get("document"));assertEquals(expected.get("version"),actual.version());assertEquals(expected.get("sha256"),actual.sha256());assertEquals(expected.get("root"),actual.root());assertEquals(expected.get("preserve"),actual.preserve());assertEquals(expected.get("required"),actual.required());assertEquals(expected.get("protocol"),actual.protocol());}
        assertNull(ServerUpdates.parse(Map.of("schema",2L,"product","BattleSpades","components",Map.of())));
        for(String unsafe:List.of("http://localhost.evil/a","http://127.0.0.1.evil/a","https://user:password@host/a","file:///tmp/a"))assertThrows(IllegalArgumentException.class,()->ServerUpdates.url(unsafe));
    }
}
