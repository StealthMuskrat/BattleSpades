// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.Objects;
import net.battlespades.physics.PlayerPhysics.Vector;

/** Detached mode role input; mode policy selection itself is migrated separately. */
public record BotModeDecision(Vector position,String role,boolean sprint,double arrivalRadius,String directive,
                              Posture posture,double objectivePriority,double engagementRadius,Vector watchPosition) {
    public enum Posture {ASSAULT,BALANCED,DEFEND,ESCORT,EVASIVE,SURVIVE,BUILD,MINE}
    public BotModeDecision {Objects.requireNonNull(position);Objects.requireNonNull(role);Objects.requireNonNull(directive);Objects.requireNonNull(posture);}
}
