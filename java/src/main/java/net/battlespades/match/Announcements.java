// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.regex.Pattern;
import net.battlespades.protocol.*;
/** Source's bounded HUD builders; localized delivery remains the caller's transport boundary. */
public final class Announcements {
    private Announcements(){}
    private static final Map<String,String> VARIABLES=Map.of("TEAM_NEUTRAL","Neutral","TEAM1_COLOR","Blue","TEAM2_COLOR","Green","ZOMBIE_TEAM","Zombie","SURVIVOR_TEAM","Survivor");
    private static final Pattern TOKEN=Pattern.compile("(?<![A-Z0-9_])(TEAM_NEUTRAL|TEAM1_COLOR|TEAM2_COLOR|ZOMBIE_TEAM|SURVIVOR_TEAM)(?![A-Z0-9_])");
    private static String bounded(String s){Objects.requireNonNull(s);if(s.isEmpty()||s.codePointCount(0,s.length())>256)throw new IllegalArgumentException("Announcement string bound");return s;}
    public static String resolve(String text){return TOKEN.matcher(text).replaceAll(m->VARIABLES.get(m.group()));}
    public static SimplePackets.ChatMessage overlay(String text){return new SimplePackets.ChatMessage(255,3,bounded(resolve(text)));}
    public static MatchPackets.LocalisedMessage localised(String id,List<String> parameters,boolean localise,boolean override){var values=parameters.stream().map(Announcements::bounded).toList();if(values.size()>16)throw new IllegalArgumentException("Announcement parameter bound");return new MatchPackets.LocalisedMessage(3,localise?1:0,bounded(id),values,override?1:0);}
    public static String safeName(String name){return name.matches("[A-Za-z_][A-Za-z0-9_]*")?name+" ":name;}
}
