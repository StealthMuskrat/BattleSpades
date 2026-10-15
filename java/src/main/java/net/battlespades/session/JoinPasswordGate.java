// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.LinkedHashMap;

/** Source join_password.py policy. Secrets and candidates are never formatted by this service. */
public final class JoinPasswordGate {
    public record Policy(String secret,int maxAttempts,long timeoutMillis,long lockoutMillis) {
        public Policy {
            if(secret.indexOf('\0')>=0 || secret.getBytes(StandardCharsets.UTF_8).length>64 || maxAttempts<1 || maxAttempts>10 || timeoutMillis<1000 || lockoutMillis<0)
                throw new IllegalArgumentException("Invalid join password policy");
        }
        @Override public String toString() {return "JoinPasswordPolicy[secret=<redacted>, maxAttempts="+maxAttempts+"]";}
        public static Policy open() {return new Policy("",3,60000,60000);}
    }
    public enum Answer { ACCEPTED, RETRY, REFUSED }
    private final Thread owner=Thread.currentThread();
    private final Policy policy;
    private final byte[] secret;
    private final LinkedHashMap<String,Long> locks=new LinkedHashMap<>();
    public JoinPasswordGate(Policy policy) {this.policy=policy;secret=policy.secret.getBytes(StandardCharsets.UTF_8);}
    private void owned() {if(Thread.currentThread()!=owner) throw new IllegalStateException("Password gate accessed outside owner");}
    public boolean enabled() {return secret.length>0;}
    public long timeoutMillis() {return policy.timeoutMillis;}
    public boolean locked(String address,long now) {
        owned();Long until=locks.get(address);if(until==null) return false;
        if(now>=until) {locks.remove(address);return false;}return true;
    }
    public int lockCount() {owned();return locks.size();}
    private void lock(String address,long now) {
        if(policy.lockoutMillis==0) return;
        if(locks.size()>=4096) locks.entrySet().stream().sorted(Comparator.comparingLong(e->e.getValue())).limit(1024).map(e->e.getKey()).toList().forEach(locks::remove);
        locks.put(address,now+policy.lockoutMillis);
    }
    public Session session() {owned();return new Session();}
    public final class Session {
        private int attempts;
        private long lastAttempt=-1;
        private boolean cleared;
        public boolean cleared() {owned();if(!enabled()) cleared=true;return cleared;}
        public int attempts() {owned();return attempts;}
        public Answer answer(String address,String candidate,long now) {
            owned();if(cleared()) return Answer.ACCEPTED;
            if(attempts>=policy.maxAttempts || locked(address,now)) return Answer.REFUSED;
            boolean rushed=lastAttempt>=0 && now-lastAttempt<500;lastAttempt=now;
            byte[] given=(candidate==null?"":candidate).getBytes(StandardCharsets.UTF_8);
            boolean correct;
            if(given.length<=64) correct=MessageDigest.isEqual(secret,given);else {MessageDigest.isEqual(secret,secret);correct=false;}
            if(correct && !rushed) {cleared=true;return Answer.ACCEPTED;}
            attempts++;if(attempts>=policy.maxAttempts) {lock(address,now);return Answer.REFUSED;}return Answer.RETRY;
        }
    }
}
