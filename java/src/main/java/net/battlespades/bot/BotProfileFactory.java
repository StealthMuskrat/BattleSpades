// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.math.BigInteger;
import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.player.SourceRandom;
/** Source-identical trait and name streams, including legacy name-draw consumption and release. */
public final class BotProfileFactory {
    public record Range(double low,double high){}
    public record Band(Range skill,Range reaction,Range tracking,Range turnSpeed,Range turnAcceleration,Range aimNoise){}
    private static final org.tomlj.TomlParseResult DATA=SourceCatalog.load("bot-foundations");
    private static final Map<String,Band> BANDS=loadBands();
    private static Range range(org.tomlj.TomlTable table,String key){var a=table.getArray(key);return new Range(a.getDouble(0),a.getDouble(1));}
    private static Map<String,Band> loadBands(){var result=new LinkedHashMap<String,Band>();for(String name:List.of("casual","normal","hard")){var t=DATA.getTable("bands."+name);result.put(name,new Band(range(t,"skill"),range(t,"reaction"),range(t,"tracking"),range(t,"turn_speed"),range(t,"turn_acceleration"),range(t,"aim_noise")));}return Collections.unmodifiableMap(result);}
    public static Map<String,Band> bands(){return BANDS;}
    private static List<String> names(String key){return SourceCatalog.strings(DATA.getArray(key));}
    private static final List<String> HANDLES=names("handles"),TITLES=names("titles"),ADJECTIVES=names("adjectives"),NOUNS=names("nouns"),LEGACY_CALLSIGNS=names("legacy_callsigns"),LEGACY_ADJECTIVES=names("legacy_adjectives"),LEGACY_NOUNS=names("legacy_nouns"),SUFFIXES=names("suffixes");
    private static final List<Integer> CLASSES=SourceCatalog.integers(DATA.getArray("classes"));
    private final Thread owner=Thread.currentThread();private final SourceRandom random,nameRandom;private final int maxLength;private final Set<String> used=new HashSet<>(),legacyUsed=new HashSet<>();private final Map<String,String> legacyFor=new HashMap<>();
    public BotProfileFactory(long seed,int maxLength){this(BigInteger.valueOf(seed),maxLength);}public BotProfileFactory(BigInteger seed,int maxLength){random=new SourceRandom(seed);nameRandom=new SourceRandom(seed.multiply(BigInteger.valueOf(7919)).add(BigInteger.valueOf(0x6e616d65L)));this.maxLength=Math.clamp(maxLength,3,15);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Profiles outside owner");}
    public void release(String name){owned();String key=name.toLowerCase(Locale.ROOT);used.remove(key);legacyUsed.remove(legacyFor.getOrDefault(key,""));legacyFor.remove(key);}
    private double uniform(Range range){return random.uniform(range.low(),range.high());}
    public BotProfile create(String difficulty){owned();String selected=difficulty.toLowerCase(Locale.ROOT);if(!BANDS.containsKey(selected)){double roll=random.random();selected=roll<.2?"casual":roll<.8?"normal":"hard";}var band=BANDS.get(selected);double skill=uniform(band.skill());return new BotProfile(uniqueName(),selected,skill,random.uniform(.25,.90),random.uniform(.20,.90),random.uniform(.30,.95),random.uniform(.15,.85),uniform(band.reaction()),uniform(band.tracking()),uniform(band.turnSpeed()),uniform(band.turnAcceleration()),.30+skill*.65,random.uniform(.35,.90),random.uniform(14,36),uniform(band.aimNoise()),random.sample(CLASSES,Math.min(2,CLASSES.size())));}
    private String uniqueName(){String legacy=burnLegacy();for(int i=0;i<512;i++){String candidate=styledName();String key=candidate.toLowerCase(Locale.ROOT);if(candidate.length()>=3&&candidate.length()<=maxLength&&candidate.chars().allMatch(c->c<128)&&used.add(key)){legacyFor.put(key,legacy);return candidate;}}String candidate=String.format(Locale.ROOT,"Bot%04d",used.size());candidate=candidate.substring(0,Math.min(maxLength,candidate.length()));used.add(candidate.toLowerCase(Locale.ROOT));return candidate;}
    private String burnLegacy(){for(int i=0;i<512;i++){String candidate=switch(random.below(4)){case 0->random.choice(LEGACY_CALLSIGNS);case 1->random.choice(LEGACY_CALLSIGNS)+(10+random.below(90));case 2->random.choice(LEGACY_ADJECTIVES)+random.choice(LEGACY_NOUNS);default->random.choice(LEGACY_NOUNS)+(2+random.below(88));};candidate=candidate.substring(0,Math.min(15,candidate.length()));if(candidate.length()>=3&&legacyUsed.add(candidate))return candidate;}return "";}
    private String styledName(){double roll=nameRandom.random();String value;if(roll<.30)value=nameRandom.choice(HANDLES);else if(roll<.50)value=nameRandom.choice(HANDLES)+nameRandom.choice(SUFFIXES);else if(roll<.68)value=nameRandom.choice(ADJECTIVES)+nameRandom.choice(NOUNS);else if(roll<.78)value=nameRandom.choice(TITLES)+nameRandom.choice(HANDLES);else if(roll<.88)value=(nameRandom.choice(ADJECTIVES)+"_"+nameRandom.choice(NOUNS)).toLowerCase(Locale.ROOT);else if(roll<.95)value=nameRandom.choice(HANDLES).toLowerCase(Locale.ROOT)+(1+nameRandom.below(99));else{String core=nameRandom.choice(NOUNS);value=nameRandom.random()<.6?"xX"+core+"Xx":"x"+core+"x";}return value.length()>15?nameRandom.choice(HANDLES):value;}
}
