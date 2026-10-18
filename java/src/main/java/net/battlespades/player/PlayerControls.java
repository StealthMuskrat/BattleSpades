// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.Objects;
import net.battlespades.protocol.*;

/** Authenticated actor routing for live menu/equipment controls; bind once to its LifeRuntime. */
public final class PlayerControls {
    private final TeamChanges teams;private final SelectionChanges selections;private final PaletteRelay palette;
    public PlayerControls(TeamChanges teams,SelectionChanges selections,PaletteRelay palette){this.teams=Objects.requireNonNull(teams);this.selections=Objects.requireNonNull(selections);this.palette=Objects.requireNonNull(palette);}
    public boolean packet(PlayerLife p,GamePacket packet){
        if(packet instanceof SimplePackets.ChangeTeam value)teams.change(p,value.team(),false,false);
        else if(packet instanceof SimplePackets.ChangeClass value)selections.change(p,value.class_id());
        else if(packet instanceof PlayerPackets.SetClassLoadout value)selections.loadout(p,value);
        else if(packet instanceof SimplePackets.SetColor value)palette.set(p,value.value());
        else if(packet instanceof SimplePackets.NewPlayerConnection value){if(p.team()==0&&value.team()!=0)teams.change(p,value.team(),true,false);}
        else return false;return true;
    }
    public void forget(PlayerLife p){teams.forget(p);selections.forget(p);palette.forget(p);}
    public void resetPalette(){palette.reset();}
}
