// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Packet 106: presence/contested actions have no trailing ownership fields. */
public record TerritoryBaseState(int baseIndex,int action,int controlledBy,int attackedBy,double captureAmount) implements GamePacket {
    public int id(){return 106;}
    private static boolean shortForm(int action){return action==3||action==4||action==6||action==7;}
    public void write(Wire.Writer w){w.u8(id()).u8(baseIndex).u8(action);if(!shortForm(action))w.u8(controlledBy).u8(attackedBy).i16(Fixed.encodePosition(captureAmount));}
    static TerritoryBaseState read(Wire.Reader r){int index=r.u8(),action=r.u8();return shortForm(action)?new TerritoryBaseState(index,action,0,0,.5):new TerritoryBaseState(index,action,r.u8(),r.u8(),Fixed.decodePosition(r.i16()));}
}
