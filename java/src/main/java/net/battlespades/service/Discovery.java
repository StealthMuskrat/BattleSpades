// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.service;

import java.io.ByteArrayOutputStream;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import net.battlespades.scene.ModeCatalog;
import net.battlespades.world.JsonData;

/** Source A2S and game-port LAN protocol. Owned by the network/scene thread. */
public final class Discovery {
    private final Thread owner=Thread.currentThread();
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Discovery outside owner");}
    public record Player(Object identity,int id,String name,long score,boolean bot){public Player{Objects.requireNonNull(identity);}}
    public record Connection(Object player,boolean bot,boolean reserved){}
    public record State(String name,String map,String mode,int port,long steamId,int maxPlayers,boolean password,
            boolean secure,String gameVersion,char operatingSystem,String tags,boolean friendlyFire,boolean fallDamage,
            double respawnTime,long scoreLimit,String team1,String team2,long score1,long score2,
            List<Player> players,List<Connection> connections){
        public State{players=List.copyOf(players);connections=List.copyOf(connections);}
    }
    public record Population(int players,int humans,int bots,int maximum){}
    private final Supplier<State> state;private final DoubleSupplier monotonic;private final IntSupplier random;
    private final IdentityHashMap<Object,Double> joined=new IdentityHashMap<>();
    private int challenge,ticks;
    public Discovery(Supplier<State> state,DoubleSupplier monotonic,IntSupplier random){this.state=Objects.requireNonNull(state);this.monotonic=Objects.requireNonNull(monotonic);this.random=Objects.requireNonNull(random);challenge=random.getAsInt();}
    public static boolean a2s(byte[] data){return data.length>=5&&data[0]==-1&&data[1]==-1&&data[2]==-1&&data[3]==-1;}
    public static boolean recognized(byte[] data){return a2s(data)||Arrays.equals(data,HELLO)||Arrays.equals(data,LAN);}
    private static final byte[] HELLO="HELLO".getBytes(StandardCharsets.US_ASCII),LAN="HELLOLAN".getBytes(StandardCharsets.US_ASCII),QUERY="Source Engine Query\0".getBytes(StandardCharsets.US_ASCII);
    public void update(){owned();if(++ticks%60==0)track(state.get());if(ticks>=18000){challenge=random.getAsInt();ticks=0;}}
    private void track(State s){var current=new IdentityHashMap<Object,Double>();double now=monotonic.getAsDouble();for(var p:s.players())current.put(p.identity(),joined.getOrDefault(p.identity(),now));joined.clear();joined.putAll(current);}
    public static Population population(State s){int maximum=Math.clamp(s.maxPlayers(),1,255),bots=0;var humans=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());for(var p:s.players())if(p.bot())bots++;else humans.add(p.identity());int count=humans.size();for(var c:s.connections())if(c.player()!=null){if(!c.bot()&&humans.add(c.player()))count++;}else if(c.reserved())count++;count=Math.min(count,maximum);bots=Math.min(bots,maximum-count);return new Population(count+bots,count,bots,maximum);}
    public static String tags(String mode,int version,int playlist,String region,String skin,List<String> relay){
        var d=ModeCatalog.get(mode);String result="v"+version+";playlist="+playlist+(region.isEmpty()?"":";region="+region)+";mode=0001"+(d.classic()?";classic":"");
        String texture=skin.isEmpty()&&d.mafia()?"mafia":skin;if(!texture.isEmpty())result+=";skin="+texture;
        if(utf8(result).length>=128)throw new IllegalArgumentException("Steam tags must fit 127 UTF-8 bytes");
        for(String tag:relay)if(utf8(result+";"+tag).length<128)result+=";"+tag;
        String gameplay=";gamemode="+d.code();return utf8(result+gameplay).length<128?result+gameplay:result;
    }
    /** null: not this protocol; empty: recognized but invalid. Never retains the supplied scene. */
    public byte[] reply(byte[] data){owned();
        if(Arrays.equals(data,HELLO))return "HI".getBytes(StandardCharsets.US_ASCII);
        if(Arrays.equals(data,LAN))return lan(state.get());
        if(!a2s(data))return null;
        int header=data[4]&255,provided=-1;
        if(header==0x54){if(data.length==5)return info(state.get());else{if(data.length<25||!Arrays.equals(data,5,25,QUERY,0,20))return new byte[0];if(data.length>=29)provided=integer(data,25);}}
        else if(header==0x55||header==0x56){if(data.length>=9)provided=integer(data,5);}
        else if(header!=0x57)return new byte[0];
        if(provided==-1||provided!=challenge)return new Wire('A').i32(challenge).bytes();
        State s=state.get();return switch(header){case 0x54->info(s);case 0x55->players(s);case 0x56->rules(s);default->new byte[0];};
    }
    private static int integer(byte[] data,int at){return ByteBuffer.wrap(data,at,4).order(ByteOrder.LITTLE_ENDIAN).getInt();}
    private static byte[] utf8(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static byte[] lan(State s){var p=population(s);var entries=new LinkedHashMap<String,Object>();entries.put("name",s.name());entries.put("players_current",p.players());entries.put("players_max",p.maximum());entries.put("map",s.map());entries.put("game_mode",s.mode());entries.put("game_version","1.0a1");var values=new ArrayList<String>();entries.forEach((k,v)->values.add(JsonData.encodeAscii(k).stripTrailing()+": "+JsonData.encodeAscii(v).stripTrailing()));return ("{"+String.join(", ",values)+"}").getBytes(StandardCharsets.US_ASCII);}
    private static byte[] info(State s){var p=population(s);return new Wire('I').u8(168).text(s.name()).text(s.map()).text("aceofspades").text("Ace of Spades").u16(224540&65535).u8(p.players()).u8(p.maximum()).u8(p.bots()).u8('d').u8(s.operatingSystem()).u8(s.password()?1:0).u8(s.secure()?1:0).text(s.gameVersion()).u8(0xb1).u16(s.port()).i64(s.steamId()).text(s.tags()).i64(224540).bytes();}
    private byte[] players(State s){track(s);var list=s.players().stream().sorted(Comparator.comparingInt(Player::id)).limit(255).toList();var w=new Wire('D').u8(list.size());double now=monotonic.getAsDouble();int index=0;for(var p:list)w.u8(index++).text(p.name()==null||p.name().isEmpty()?"Player"+p.id():p.name()).i32(Math.clamp(p.score(),Integer.MIN_VALUE,Integer.MAX_VALUE)).f32((float)(now-joined.get(p.identity())));return w.bytes();}
    private static byte[] rules(State s){var rows=new LinkedHashMap<String,String>();rows.put("mode",s.mode());rows.put("map",s.map());rows.put("friendly_fire",s.friendlyFire()?"1":"0");rows.put("fall_damage",s.fallDamage()?"1":"0");rows.put("respawn_time",""+(long)s.respawnTime());rows.put("score_limit",""+s.scoreLimit());rows.put("team1",s.team1());rows.put("team2",s.team2());rows.put("team1_score",""+s.score1());rows.put("team2_score",""+s.score2());var w=new Wire('E').u16(rows.size());rows.forEach((k,v)->w.text(k).text(v));return w.bytes();}
    private static final class Wire {
        private final ByteArrayOutputStream out=new ByteArrayOutputStream();Wire(int type){out.writeBytes(new byte[]{-1,-1,-1,-1,(byte)type});}
        Wire u8(int v){out.write(v);return this;}Wire u16(int v){return u8(v).u8(v>>>8);}Wire i32(int v){return u16(v).u16(v>>>16);}Wire i64(long v){return i32((int)v).i32((int)(v>>>32));}Wire f32(float v){return i32(Float.floatToIntBits(v));}Wire text(String v){out.writeBytes(utf8(v));return u8(0);}byte[] bytes(){return out.toByteArray();}
    }
}
