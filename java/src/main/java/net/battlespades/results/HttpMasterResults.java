// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.results;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import net.battlespades.admin.AdminAuthority;
import net.battlespades.world.MetadataDocument;

/** Actual asynchronous master stats request. Credentials stay in the HTTP header and never enter the durable envelope. */
public final class HttpMasterResults implements ResultPublisher.Sender,AutoCloseable {
    private final HttpClient client;private final URI endpoint;private final String token;private final Duration timeout;
    public HttpMasterResults(URI base,String token,Duration timeout){
        Objects.requireNonNull(base);Objects.requireNonNull(token);Objects.requireNonNull(timeout);String scheme=base.getScheme(),host=base.getHost();if(host==null||!("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)&&AdminAuthority.isLoopback(host))||base.getRawUserInfo()!=null||base.getRawQuery()!=null||base.getRawFragment()!=null)throw new IllegalArgumentException("Master requires HTTPS or explicit loopback HTTP");
        if(token.isBlank()||token.length()>4096||token.chars().anyMatch(c->c<32||c==127))throw new IllegalArgumentException("Invalid master write credential");this.token=token;this.timeout=timeout.compareTo(Duration.ofSeconds(1))<0?Duration.ofSeconds(1):timeout.compareTo(Duration.ofSeconds(15))>0?Duration.ofSeconds(15):timeout;endpoint=URI.create(base.toString().replaceAll("/+$","")+"/api/master/stats");client=HttpClient.newBuilder().connectTimeout(this.timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Override public String toString(){return "HttpMasterResults[endpoint="+endpoint+", credential=<redacted>]";}
    @Override public CompletionStage<Boolean> post(ResultOutbox.Event event){var request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Authorization","Bearer "+token).header("Content-Type","application/json").header("User-Agent","BattleSpades/1.0 RevivalBridge/1").POST(HttpRequest.BodyPublishers.ofString(event.json(),StandardCharsets.UTF_8)).build();return client.sendAsync(request,info->new BoundedBody()).thenApply(response->{if(response.statusCode()!=200)return false;try{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.body())).toString();Object value=text.isEmpty()?Map.of():MetadataDocument.json(text);return value instanceof Map<?,?> row&&Boolean.TRUE.equals(row.get("accepted"));}catch(CharacterCodingException|IllegalArgumentException error){throw new CompletionException(new IOException("Master returned invalid JSON",error));}});}
    @Override public void close(){client.close();}
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private static final int MAX=65536;private final CompletableFuture<byte[]> body=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return body;}
        public void onSubscribe(Flow.Subscription value){if(subscription!=null){value.cancel();return;}subscription=value;value.request(Long.MAX_VALUE);}
        public void onNext(List<ByteBuffer> buffers){if(body.isDone())return;for(var buffer:buffers){if(buffer.remaining()>MAX-bytes.size()){subscription.cancel();body.completeExceptionally(new IOException("Master response exceeds size limit"));return;}byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);}}
        public void onError(Throwable error){body.completeExceptionally(error);}public void onComplete(){body.complete(bytes.toByteArray());}
    }
}
