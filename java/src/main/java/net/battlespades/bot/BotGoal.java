// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
record BotGoal(List<Object> key,Vector position,String role,double arrivalRadius,boolean sprint,Vector watchPosition){BotGoal{key=Collections.unmodifiableList(new ArrayList<>(key));}}
