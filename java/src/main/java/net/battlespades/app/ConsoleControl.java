// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.app;
import java.io.*;
import java.util.concurrent.*;
/** Bounded stdin ingress and stdout egress; terminal I/O never runs in an authoritative frame. */
final class ConsoleControl implements AutoCloseable {
    private final ArrayBlockingQueue<String> commands=new ArrayBlockingQueue<>(64),replies=new ArrayBlockingQueue<>(256);private final CompletableFuture<Void> drained=new CompletableFuture<>();private volatile boolean closing;
    ConsoleControl(InputStream input,PrintStream output,boolean read){Thread.ofPlatform().daemon().name("server-console-output").start(()->{try{while(!closing||!replies.isEmpty()){String line;try{line=replies.poll(100,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){continue;}if(line!=null){output.println(line);output.flush();}}}finally{drained.complete(null);}});if(read)Thread.ofPlatform().daemon().name("server-console-input").start(()->{try{var in=new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8);var line=new StringBuilder();boolean overflow=false;int c;while(!closing&&(c=in.read())!=-1){if(c=='\n'){if(!overflow&&!line.isEmpty()&&!commands.offer(line.toString()))reply("Console command queue full");line.setLength(0);overflow=false;}else if(c!='\r'){if(line.length()<1024)line.append((char)c);else overflow=true;}}if(!overflow&&!line.isEmpty())commands.offer(line.toString());}catch(IOException error){reply("Console input failed: "+error.getMessage());}});}
    String poll(){return commands.poll();}void reply(String line){if(!closing&&!replies.offer(line)){replies.poll();replies.offer(line);}}
    @Override public void close()throws IOException{closing=true;try{drained.get(5,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("Console drain interrupted",error);}catch(ExecutionException|TimeoutException error){throw new IOException("Console output did not drain",error);}}
}
