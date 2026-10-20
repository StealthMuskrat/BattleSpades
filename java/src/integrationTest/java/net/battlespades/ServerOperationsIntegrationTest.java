// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import net.battlespades.app.Main;
import net.battlespades.update.ServerUpdates;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ServerOperationsIntegrationTest {
    @TempDir Path temp;
    private static byte[] zip(Map<String,String> files)throws IOException{var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){for(var e:files.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}}return bytes.toByteArray();}
    private static Map<String,Object> manifest(byte[] bytes,List<String> mirrors){return Map.of("schema",2,"product","BattleSpades","components",Map.of("server",Map.of("version","99.0.0","package","server.zip","urls",mirrors,"size",bytes.length,"sha256",hash(bytes),"root","bundle","preserve",List.of("config.toml","bans.json"))));}
    private static String hash(byte[] data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    @Test void actualHttpMirrorsVerifyCacheStageAndPreserveInstallationThroughCli(){assertTimeoutPreemptively(Duration.ofSeconds(20),()->{
        byte[] archive=zip(Map.of("bundle/VERSION","99.0.0\n","bundle/config.toml","shipped","bundle/BattleSpades.jar","new portable server"));var requests=new java.util.concurrent.atomic.AtomicInteger();var http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);String base="http://127.0.0.1:"+http.getAddress().getPort();
        var document=manifest(archive,List.of(base+"/bad",base+"/good"));byte[] json=JsonData.encode(document).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        http.createContext("/manifest",exchange->{try(exchange){exchange.sendResponseHeaders(200,json.length);exchange.getResponseBody().write(json);}});http.createContext("/bad",exchange->{requests.incrementAndGet();try(exchange){exchange.sendResponseHeaders(200,4);exchange.getResponseBody().write(new byte[4]);}});http.createContext("/good",exchange->{requests.incrementAndGet();try(exchange){exchange.sendResponseHeaders(200,archive.length);exchange.getResponseBody().write(archive);}});http.createContext("/oversized",exchange->{try(exchange){exchange.sendResponseHeaders(200,0);try{exchange.getResponseBody().write(new byte[1024*1024+1]);}catch(IOException cancelled){/* Bounded subscriber cancels oversized bodies. */}}});http.start();
        try{var release=ServerUpdates.check("0.2.0",base+"/manifest");assertNotNull(release);assertNull(ServerUpdates.check("99.0.0",base+"/manifest"));var messages=new ArrayList<String>();Path download=ServerUpdates.download(release,temp.resolve("cache"),messages::add);assertEquals(2,requests.get());assertArrayEquals(archive,Files.readAllBytes(download));assertEquals(download,ServerUpdates.download(release,temp.resolve("cache"),messages::add));assertEquals(2,requests.get());assertFalse(Files.exists(download.resolveSibling("server.zip.partial")));
            Files.writeString(temp.resolve("config.toml"),"[updates]\nupdate_check_enabled=false\n");Files.writeString(temp.resolve("VERSION"),"operator version");Files.writeString(temp.resolve("BattleSpades.jar"),"current portable server");byte[] config=Files.readAllBytes(temp.resolve("config.toml"));var output=new ByteArrayOutputStream();assertEquals(0,Main.run(new String[]{"--update","--config",temp.resolve("config.toml").toString(),"--manifest",base+"/manifest"},new PrintStream(output),System.err));Path staged=temp.resolve("update/staging/server-99.0.0");assertEquals("new portable server",Files.readString(staged.resolve("payload/bundle/BattleSpades.jar")));assertTrue(Files.readString(staged.resolve("staged.json")).contains("config.toml"));assertArrayEquals(config,Files.readAllBytes(temp.resolve("config.toml")));assertEquals("current portable server",Files.readString(temp.resolve("BattleSpades.jar")));assertFalse(Files.exists(temp.resolve("update/download/server.zip")));assertThrows(IOException.class,()->ServerUpdates.check("0.2.0",base+"/oversized"));
            byte[] evil=zip(Map.of("../escape","bad","VERSION","99.0.0"));Path evilZip=temp.resolve("evil.zip");Files.write(evilZip,evil);var evilRelease=ServerUpdates.parse(MetadataDocument.json(JsonData.encode(manifest(evil,List.of(base+"/good")))));assertThrows(IOException.class,()->ServerUpdates.stage(temp,evilRelease,evilZip));assertFalse(Files.exists(temp.resolve("update/staging/server-99.0.0.extracting")));assertEquals("new portable server",Files.readString(staged.resolve("payload/bundle/BattleSpades.jar")));
        }finally{http.stop(0);}
    });}
    @Test void packagedStandaloneConsoleAndTimedShutdownUseTheProductionLoop(){assertTimeoutPreemptively(Duration.ofSeconds(45),()->{
        Path maps=temp.resolve("maps");Files.createDirectories(maps);net.battlespades.support.StandardPorts.writeMap(maps.resolve("First.vxl"));Path config=temp.resolve("config.toml");Files.writeString(config,"[server]\nlan_discovery=false\n[game]\ndefault_map='First'\n[bots]\nenabled=false\n[revival]\nenabled=false\n[logging]\nconsole=false\n[updates]\nupdate_check_enabled=false\n");
        int port;try(var reservation=new DatagramSocket(new InetSocketAddress("127.0.0.1",0))){port=reservation.getLocalPort();}
        Path output=temp.resolve("console.txt"),error=temp.resolve("error.txt");var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-jar",System.getProperty("battlespades.applicationJar"),"--serve","--config",config.toString(),"--bind","127.0.0.1","--port",Integer.toString(port),"--control-stdin","--shutdown-after","2"));
        var process=new ProcessBuilder(command).redirectOutput(output.toFile()).redirectError(error.toFile()).start();try{process.getOutputStream().write("status\nsay Console integration\nstop\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));process.getOutputStream().close();assertTrue(process.waitFor(12,TimeUnit.SECONDS),"Standalone process failed to stop");assertEquals(0,process.exitValue(),()->{try{return Files.readString(error);}catch(IOException e){return e.toString();}});String text=Files.readString(output);assertTrue(text.contains("READY "),text);assertTrue(text.contains("Announced"),text);assertTrue(text.contains("Stopped"),text);assertTrue(text.contains("\"port\":"+port),text);}finally{process.destroyForcibly();}
        Path repository=Path.of(System.getProperty("battlespades.applicationJar")).getParent().getParent().getParent().getParent();for(String extension:List.of("vxl","json"))Files.copy(repository.resolve("maps/MayanJungle."+extension),maps.resolve("MayanJungle."+extension));Files.writeString(config,Files.readString(config).replace("default_map='First'","default_map='MayanJungle'"));
        command.remove("--control-stdin");command.set(command.size()-1,"0.05");var timed=new ProcessBuilder(command).redirectOutput(output.toFile()).redirectError(error.toFile()).start();try{assertTrue(timed.waitFor(25,TimeUnit.SECONDS));assertEquals(0,timed.exitValue(),Files.readString(error));assertTrue(Files.readString(output).contains("READY "));assertTrue(Files.readString(output).contains("MayanJungle"));}finally{timed.destroyForcibly();}
    });}
}
