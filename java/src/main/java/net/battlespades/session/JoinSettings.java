// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import net.battlespades.protocol.MapSyncTransfer;
import org.tomlj.TomlParseResult;

public record JoinSettings(int maxInboundBytes,int maxTicketBytes,int packetBudget,int replayBudget,
        int controlCapacity,int controlBytes,int clockLoopBias,long transitionGraceMillis,boolean repairAir,
        MapSyncTransfer.Mode mapMode,JoinPasswordGate.Policy password) {
    public JoinSettings {
        if(maxInboundBytes<16 || maxTicketBytes<0 || maxTicketBytes>maxInboundBytes-5 || packetBudget<1 || replayBudget<1
                || controlCapacity<4 || controlBytes<maxInboundBytes || transitionGraceMillis<0 || transitionGraceMillis>5000)
            throw new IllegalArgumentException("Invalid join resource limits");
        java.util.Objects.requireNonNull(mapMode);java.util.Objects.requireNonNull(password);
    }
    public static JoinSettings defaults() {return new JoinSettings(65536,2048,64,256,1024,1024*1024,0,1250,false,MapSyncTransfer.Mode.FULL,JoinPasswordGate.Policy.open());}
    public static JoinSettings from(TomlParseResult raw) {
        String secret=text(raw,"server.password","");int attempts=(int)Math.max(1,Math.min(10,integer(raw,"server.password_max_attempts",3)));
        long timeout=(long)(Math.min(600,Math.max(5,number(raw,"server.password_timeout_seconds",60)))*1000);
        long lockout=(long)(Math.min(86400,Math.max(0,number(raw,"server.password_lockout_seconds",60)))*1000);
        var mode=switch(text(raw,"game.map_sync_mode","full").toLowerCase(java.util.Locale.ROOT)) {
            case "full" -> MapSyncTransfer.Mode.FULL;case "auto" -> MapSyncTransfer.Mode.AUTO;
            default -> MapSyncTransfer.Mode.FULL;
        };
        long bias=integer(raw,"debug.clock_sync_loop_bias",0);if(bias<Integer.MIN_VALUE || bias>Integer.MAX_VALUE) throw new IllegalArgumentException("Clock loop bias overflow");
        long grace=(long)(Math.min(5,Math.max(0,number(raw,"network.transition_grace_seconds",1.25)))*1000);
        Object air=raw.get("network.map_air_catchup_enabled");if(air!=null && !(air instanceof Boolean)) throw new IllegalArgumentException("map_air_catchup_enabled must be boolean");
        int replay=(int)Math.min(Integer.MAX_VALUE,Math.max(1,integer(raw,"network.map_air_catchup_batch_limit",256)));
        return new JoinSettings(65536,2048,64,replay,1024,1024*1024,(int)bias,grace,Boolean.TRUE.equals(air),mode,new JoinPasswordGate.Policy(secret,attempts,timeout,lockout));
    }
    private static String text(TomlParseResult r,String key,String fallback) {Object v=r.get(key);if(v==null) return fallback;if(v instanceof String s) return s;throw new IllegalArgumentException(key+" must be string");}
    private static long integer(TomlParseResult r,String key,long fallback) {Object v=r.get(key);if(v==null) return fallback;if(v instanceof Long n) return n;throw new IllegalArgumentException(key+" must be integer");}
    private static double number(TomlParseResult r,String key,double fallback) {Object v=r.get(key);double n=v==null?fallback:v instanceof Number x?x.doubleValue():Double.NaN;if(!Double.isFinite(n)) throw new IllegalArgumentException(key+" must be finite numeric");return n;}
}
