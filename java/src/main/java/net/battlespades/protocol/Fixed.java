// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Exact shared/packet.pyx conversions; generic protocol 8.8 is a separate format. */
public final class Fixed {
    private Fixed() {}
    public static int encodePosition(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite fixed value");
        double scaled = value * 64 + 0.5;
        double mag = Math.min(32767, Math.abs(scaled < 0 ? Math.ceil(scaled) : Math.floor(scaled)));
        return (int) mag | (scaled <= -1 ? 0x8000 : 0);
    }
    public static double decodePosition(int value) { return ((value & 0x8000) == 0 ? 1 : -1) * ((value & 0x7fff) / 64.0); }
    public static int encodeOrientation(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite orientation");
        float bounded = Math.max(-16383f / 8192f, Math.min(16383f / 8192f, value));
        float scaled = bounded * 8192f;
        int raw = (int)(scaled >= 0 ? Math.floor(scaled + 0.5) : Math.ceil(scaled - 0.5));
        return switch(raw) { case 16383 -> 0x5ff7; case -16383 -> 0xdff7; default -> raw & 0xffff; };
    }
    public static float decodeOrientation(int value) { return (short)value / 8192.0f; }
    public static int encodeQuarter(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 63.75f) throw new IllegalArgumentException("Quarter-unit value out of range");
        int whole = (int)value;
        int fraction = (int)((value - whole) * 4 + 0.5);
        return (whole << 2) + fraction;
    }
    public static float decodeQuarter(int value) { return value / 4.0f; }
}
