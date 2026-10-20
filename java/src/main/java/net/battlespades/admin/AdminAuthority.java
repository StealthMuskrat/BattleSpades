// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.admin;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.DoubleSupplier;
import org.tomlj.TomlTable;

/** Process-owned credential authority. Principals are connection tokens, never compact player IDs or typed names. */
public final class AdminAuthority {
    public record Settings(String password,String creatorToken,Set<String> autoAdmin,int attempts){
        public Settings{Objects.requireNonNull(password);Objects.requireNonNull(creatorToken);autoAdmin=normalize(autoAdmin);if(attempts<1||attempts>1000)throw new IllegalArgumentException("Admin attempts must be 1..1000");if(!creatorToken.isEmpty()&&!validCreatorToken(creatorToken))throw new IllegalArgumentException("Invalid [admin] creator_token");}
        @Override public String toString(){return "AdminSettings[password=<redacted>, creatorToken=<redacted>, autoAdmin="+autoAdmin+", attempts="+attempts+"]";}
        public static Settings from(TomlTable raw){String password=raw.getString("admin.password",()->"changeme"),token=raw.getString("admin.creator_token",()->"");var auto=raw.getArray("admin.auto_admin");var ids=new ArrayList<String>();if(auto!=null)for(int i=0;i<auto.size();i++){if(!(auto.get(i) instanceof String s))throw new IllegalArgumentException("admin.auto_admin entries must be strings");ids.add(s);}long attempts=raw.getLong("anticheat.admin_login_attempts",()->3L);if(attempts<1||attempts>1000)throw new IllegalArgumentException("Invalid admin_login_attempts");return new Settings(password,token,new LinkedHashSet<>(ids),(int)attempts);}
    }
    public static final class Principal {private final AdminAuthority issuer;private boolean admin,roomHost;private Principal(AdminAuthority issuer){this.issuer=issuer;}}
    public record Result(String message,boolean kick,boolean banAddress,int failures){}
    private record Failure(int count,double stamp){}
    private final Thread owner=Thread.currentThread();private final Settings settings;private final DoubleSupplier monotonic;private final LinkedHashMap<String,Failure> failures=new LinkedHashMap<>();private boolean claimed;
    public AdminAuthority(Settings settings,DoubleSupplier monotonic){this.settings=Objects.requireNonNull(settings);this.monotonic=Objects.requireNonNull(monotonic);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Admin authority outside owner");}
    private void valid(Principal p){owned();if(p==null||p.issuer!=this)throw new IllegalArgumentException("Principal belongs to another authority");}
    public Principal principal(Set<String> verifiedIdentities){owned();var p=new Principal(this);p.admin=!Collections.disjoint(settings.autoAdmin(),normalize(verifiedIdentities));return p;}
    public boolean admin(Principal p){valid(p);return p.admin;}public boolean roomHost(Principal p){valid(p);return p.roomHost;}
    private static boolean equal(String a,String b){return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    public Result login(Principal p,String address,String raw){valid(p);if(p.admin)return result("You are already an admin.");if(passwordProblem(settings.password())!=null)return result("Admin login is disabled on this server: the operator has not set a secure admin password.");String attempt=raw.strip();if(attempt.isEmpty())return result("Usage: /admin <password>");if(equal(attempt,settings.password())){failures.remove(address);p.admin=true;return result("You are now an admin.");}return reject(address,"Invalid password.");}
    public Result claim(Principal p,String address,String raw){valid(p);if(settings.creatorToken().isEmpty())return result("This server has no room host to claim.");if(p.admin)return result("You are already an admin.");String attempt=raw.strip();if(attempt.isEmpty())return result("Usage: /claimhost <token>");boolean matches=equal(attempt,settings.creatorToken());if(!matches||claimed)return reject(address,matches?"The room host was already claimed.":"Invalid host token.");claimed=true;failures.remove(address);p.admin=true;p.roomHost=true;return result("You created this room, so you are its admin.");}
    private static Result result(String message){return new Result(message,false,false,0);}
    private Result reject(String address,String message){double now=monotonic.getAsDouble();if(!Double.isFinite(now)||now<0)throw new IllegalStateException("Invalid authentication clock");failures.entrySet().removeIf(e->now-e.getValue().stamp()>600);int count=failures.containsKey(address)?failures.get(address).count()+1:1;if(count>=settings.attempts()){failures.remove(address);return new Result(message,true,address!=null&&!address.equals("unknown")&&!isLoopback(address),count);}if(failures.size()>=4096&&!failures.containsKey(address))failures.remove(failures.keySet().iterator().next());failures.put(address,new Failure(count,now));return new Result(message,false,false,count);}
    /** Only a private client-hosted room may echo its generated password to an authenticated administrator. */
    public Optional<String> roomPassword(Principal p){valid(p);return p.admin&&!settings.creatorToken().isEmpty()&&passwordProblem(settings.password())==null?Optional.of(settings.password()):Optional.empty();}
    public boolean playerHosted(){owned();return !settings.creatorToken().isEmpty();}
    public static String passwordProblem(String password){if(password==null||password.isBlank())return "empty";if(password.equals("changeme"))return "shipped default";return password.codePointCount(0,password.length())<12?"shorter than 12 characters":null;}
    public static boolean validCreatorToken(String value){return value!=null&&value.length()>=24&&value.length()<=128&&value.chars().allMatch(c->c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||c=='-'||c=='_');}
    public static Optional<String> normalizeIdentity(String input){if(input==null)return Optional.empty();String text=input.strip();if(text.length()>256)return Optional.empty();if(text.matches("[0-9]{15,}"))return Optional.of("steam:"+new BigInteger(text));int colon=text.indexOf(':');if(colon<0)return Optional.empty();String kind=text.substring(0,colon).strip().toLowerCase(Locale.ROOT),id=text.substring(colon+1).strip();if(id.isEmpty()||id.length()>128)return Optional.empty();if(kind.equals("steam")&&id.matches("[0-9]+"))return Optional.of("steam:"+new BigInteger(id));if(kind.equals("aosplay")&&id.codePoints().allMatch(c->Character.isLetterOrDigit(c)||Character.getType(c)==Character.LETTER_NUMBER||Character.getType(c)==Character.OTHER_NUMBER||c=='-'||c=='_'))return Optional.of("aosplay:"+id.toLowerCase(Locale.ROOT));return Optional.empty();}
    private static Set<String> normalize(Collection<String> values){var result=new LinkedHashSet<String>();for(var value:values)normalizeIdentity(value).ifPresent(result::add);return Collections.unmodifiableSet(result);}
    public static boolean isLoopback(String address){if(address==null)return false;String host=address.strip().replace("[","").replace("]","");if(host.equalsIgnoreCase("localhost")||host.equals("::1")||host.matches("(?:0*:){7}0*1"))return true;var parts=host.split("\\.",-1);if(parts.length!=4||!parts[0].equals("127"))return false;for(var part:parts)try{if(!part.matches("[0-9]{1,3}")||Integer.parseInt(part)>255)return false;}catch(NumberFormatException e){return false;}return true;}
}
