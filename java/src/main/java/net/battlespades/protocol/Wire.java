// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Little-endian application fields from shared/bytes.pyx. No ENet byte order here. */
public final class Wire {
    private Wire() {}

    public static final class Reader {
        private final byte[] data;
        private int position;
        public Reader(byte[] data) { this.data = Objects.requireNonNull(data).clone(); }
        public int remaining() { return data.length - position; }
        public int position() { return position; }
        private void require(int size) {
            if (size < 0 || size > remaining()) throw new ProtocolException("Truncated field at " + position + ", need " + size);
        }
        public int u8() { require(1); return data[position++] & 255; }
        public int i8() { return (byte) u8(); }
        public int u16() { return u8() | u8() << 8; }
        public int i16() { return (short) u16(); }
        public int i32() { require(4); return u8() | u8() << 8 | u8() << 16 | u8() << 24; }
        public long u32() { return Integer.toUnsignedLong(i32()); }
        /** Raw uint64 bit pattern; convert with Long.toUnsignedString when presenting. */
        public long u64() { return u32() | u32() << 32; }
        public float f32() { return Float.intBitsToFloat(i32()); }
        public byte[] bytes(int size) {
            require(size);
            byte[] result = Arrays.copyOfRange(data, position, position + size);
            position += size;
            return result;
        }
        public String string() {
            int start = position;
            while (remaining() > 0) {
                if (u8() == 0) return new String(data, start, position - start - 1, StandardCharsets.UTF_8);
            }
            throw new ProtocolException("Unterminated string at " + start);
        }
        public int color() { return u8() | u8() << 8 | u8() << 16; }
        public void end() { if (remaining() != 0) throw new ProtocolException("Trailing packet bytes: " + remaining()); }
    }

    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        public Writer u8(int value) {
            if (value < 0 || value > 255) throw new IllegalArgumentException("uint8 out of range: " + value);
            out.write(value); return this;
        }
        /** Signed/unsigned 16-bit bit pattern, matching Cython short assignment. */
        public Writer i16(int value) {
            if (value < Short.MIN_VALUE || value > 65535) throw new IllegalArgumentException("16-bit out of range: " + value);
            out.write(value); out.write(value >>> 8); return this;
        }
        public Writer i32(int value) {
            out.write(value); out.write(value >>> 8); out.write(value >>> 16); out.write(value >>> 24); return this;
        }
        public Writer u32(long value) {
            if (value < 0 || value > 0xffff_ffffL) throw new IllegalArgumentException("uint32 out of range");
            return i32((int) value);
        }
        public Writer u64(long value) { i32((int)value); return i32((int)(value >>> 32)); }
        public Writer f32(float value) { return i32(Float.floatToRawIntBits(value)); }
        public Writer bytes(byte[] value) { out.writeBytes(value); return this; }
        public Writer string(String value) {
            Objects.requireNonNull(value);
            if (value.indexOf('\0') >= 0) throw new IllegalArgumentException("Embedded NUL");
            out.writeBytes(value.getBytes(StandardCharsets.UTF_8)); out.write(0); return this;
        }
        public Writer color(int rgb) {
            if ((rgb & 0xff00_0000) != 0) throw new IllegalArgumentException("RGB out of range");
            return u8(rgb & 255).u8((rgb >>> 8) & 255).u8((rgb >>> 16) & 255);
        }
        public byte[] toByteArray() { return out.toByteArray(); }
    }
}
