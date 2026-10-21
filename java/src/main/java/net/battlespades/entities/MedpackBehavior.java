// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.Objects;
import net.battlespades.player.PlayerLife;

/** Original per-pack uses and per-recipient Hardcore cooldown. Healing and live mode/time are required ports. */
public final class MedpackBehavior extends DamageableEntityBehavior {
    public interface Care {boolean hardcore();double wall();void heal(PlayerLife player,int amount);}
    private final int team,healAmount;private int uses;private final Care care;
    public MedpackBehavior(int team,int healAmount,int uses,double health,Care care){super(health);this.team=team;this.healAmount=healAmount;this.uses=uses;this.care=Objects.requireNonNull(care);touchRadius=3;}
    public int team(){return team;}
    public int uses(){return uses;}
    @Override public boolean onTouch(MapEntity entity,PlayerLife player,EntityContext context){
        if(player.team()!=team||player.health()>=player.maxHealth())return false;
        if(care.hardcore()){double now=care.wall();if(!(now-player.lastDamageWall()>=3&&now>=player.hardcoreNextHeal()))return false;player.hardcoreNextHeal(now+2);}
        care.heal(player,healAmount);uses--;if(uses<=0)remove(entity,context);return true;
    }
}
