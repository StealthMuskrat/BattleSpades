// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
/** Persistent immutable source humanization parameters. Difficulty adaptation always starts from this base. */
public record BotProfile(String name,String difficulty,double skill,double aggression,double caution,double teamwork,double creativity,double reactionTime,double trackingDelay,double turnSpeed,double turnAcceleration,double recoilControl,double burstDiscipline,double preferredRange,double aimNoise,List<Integer> classPreferences){
    public BotProfile{Objects.requireNonNull(name);Objects.requireNonNull(difficulty);classPreferences=List.copyOf(classPreferences);}
}
