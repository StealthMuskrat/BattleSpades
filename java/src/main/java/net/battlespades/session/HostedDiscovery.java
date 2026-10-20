// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.*;
import org.tomlj.TomlTable;
import net.battlespades.service.Discovery;
import net.battlespades.transport.UdpEndpoint;
import net.battlespades.protocol.ProtocolException;
/** Portable shared game-port query binding and exclusive optional Steam-range LAN endpoint. */
public final class HostedDiscovery implements AutoCloseable {
    private final Thread owner=Thread.currentThread();
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Discovery outside owner");}
    public record Settings(boolean lan,Set<Integer> reserved,List<Integer> candidates,String version,long steamId,int protocol,int playlist,String region,String skin,List<String> relay,boolean password){
        public Settings{reserved=Set.copyOf(reserved);candidates=List.copyOf(candidates);relay=List.copyOf(relay);}
        public static Settings from(TomlTable t,int gamePort,long steamId){var reserved=new HashSet<Integer>();if(t.getBoolean("steam.enabled",()->false)){reserved.add(Math.toIntExact(t.getLong("steam.steam_port",()->8766)));int query=Math.toIntExact(t.getLong("steam.query_port",()->0));reserved.add(query==0?gamePort+1:query);}return new Settings(t.getBoolean("server.lan_discovery",()->true),reserved,List.of(27015,27016,27017,27018,27019,27020),t.getString("steam.game_version",()->"1.0.0.0"),steamId,Math.toIntExact(t.getLong("steam.protocol_version",()->168)),Math.toIntExact(t.getLong("steam.playlist_id",()->8)),t.getString("steam.region",()->""),t.getString("steam.texture_skin",()->""),List.of(),!t.getString("server.password",()->"").isEmpty());}
    }
    private final ApplicationHost host;private final Supplier<StandardMatchDomain> match;private final Settings settings;private final Discovery protocol;private final Consumer<Throwable> failure;private final AutoCloseable lease;
    private final int gamePort;private UdpEndpoint extra;private Integer lanPort;private boolean closed;
    public HostedDiscovery(ApplicationHost host,Supplier<StandardMatchDomain> match,Settings settings,DoubleSupplier clock,IntSupplier challenge,Consumer<Throwable> failure)throws IOException{
        this.host=Objects.requireNonNull(host);if(host.discovery().isPresent())throw new IllegalStateException("Discovery already bound");this.match=Objects.requireNonNull(match);this.settings=Objects.requireNonNull(settings);this.failure=Objects.requireNonNull(failure);gamePort=((InetSocketAddress)host.address()).getPort();protocol=new Discovery(this::snapshot,clock,challenge);
        lease=host.connectionless(data->{if(!Discovery.recognized(data))return null;try{return protocol.reply(data);}catch(RuntimeException problem){failure.accept(problem);return new byte[0];}});
        if(settings.lan()){if(settings.candidates().contains(gamePort))lanPort=gamePort;else for(int port:settings.candidates()){if(settings.reserved().contains(port))continue;try{extra=new UdpEndpoint(new InetSocketAddress(port),4096,65507);lanPort=((InetSocketAddress)extra.address()).getPort();break;}catch(IOException unavailable){failure.accept(unavailable);}}}
        host.discovery(this);
    }
    private Discovery.State snapshot(){var m=match.get();var players=m.lives().players().stream().map(p->new Discovery.Player(p,p.id(),p.name(),m.binding().row(p).score,p.bot())).toList();var connections=new ArrayList<Discovery.Connection>();for(var session:host.transitionSessions()){int id=session.playerId().orElse(-1);var player=players.stream().filter(p->p.id()==id).findFirst().orElse(null);connections.add(new Discovery.Connection(player==null?null:player.identity(),player!=null&&player.bot(),id>=0));}var first=m.teams().stream().filter(t->t.id==2).findFirst().orElseThrow();var second=m.teams().stream().filter(t->t.id==3).findFirst().orElseThrow();String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);return new Discovery.State(m.serverName(),m.mapName(),m.modeCode(),gamePort,settings.steamId(),host.playerLimit(),settings.password(),false,settings.version(),os.startsWith("windows")?'w':os.startsWith("mac")?'m':'l',Discovery.tags(m.modeCode(),settings.protocol(),settings.playlist(),settings.region(),settings.skin(),settings.relay()),m.friendlyFire(),m.fallDamage(),m.respawnTime(),m.mode().scoreLimit(),first.name,second.name,first.score,second.score,players,connections);}
    public OptionalInt lanPort(){owned();return lanPort==null?OptionalInt.empty():OptionalInt.of(lanPort);}
    public void update(){owned();if(!closed)protocol.update();}
    /** Dedicated discovery ports accept A2S only; LAN clients must receive the actual game port. */
    public void service()throws IOException{owned();if(closed||extra==null)return;for(int n=0;n<64;n++){Optional<UdpEndpoint.Datagram> incoming;try{incoming=extra.receive();}catch(ProtocolException oversized){continue;}if(incoming.isEmpty())break;var d=incoming.orElseThrow();if(!Discovery.a2s(d.payload()))continue;try{byte[] response=protocol.reply(d.payload());if(response!=null&&response.length>0)extra.send(d.source(),response);}catch(RuntimeException problem){failure.accept(problem);}}}
    @Override public void close()throws IOException{owned();if(closed)return;closed=true;lanPort=null;try{lease.close();}catch(Exception error){throw new IOException("Close discovery interception",error);}finally{if(extra!=null)extra.close();}}
}
