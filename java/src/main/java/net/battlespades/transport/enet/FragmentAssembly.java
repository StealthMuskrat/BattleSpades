// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.transport.enet;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Optional;
import net.battlespades.protocol.ProtocolException;

/** One bounded fragment group; lifetime/group-count limits must be enforced by its peer owner. */
public final class FragmentAssembly {
    private final byte[] data;
    private final BitSet received;
    private final int[] offsets;
    private final int[] lengths;
    private int fragmentsReceived;
    private long bytesReceived;
    public FragmentAssembly(int totalLength, int fragmentCount, int maxLength, int maxFragments) {
        if (totalLength <= 0 || totalLength > maxLength || fragmentCount < 1 || fragmentCount > maxFragments || fragmentCount > totalLength) {
            throw new ProtocolException("Fragment group exceeds bounds");
        }
        data = new byte[totalLength]; received = new BitSet(fragmentCount);
        offsets = new int[fragmentCount]; lengths = new int[fragmentCount];
        Arrays.fill(offsets, -1);
    }
    public Optional<byte[]> accept(int number, int offset, byte[] fragment) {
        if (number < 0 || number >= offsets.length || offset < 0 || offset > data.length || fragment.length == 0 || fragment.length > data.length - offset) {
            throw new ProtocolException("Invalid fragment range");
        }
        if (received.get(number)) {
            if (offsets[number] != offset || lengths[number] != fragment.length || !Arrays.equals(data, offset, offset + fragment.length, fragment, 0, fragment.length)) {
                throw new ProtocolException("Conflicting duplicate fragment");
            }
            return Optional.empty();
        }
        for (int i = received.nextSetBit(0); i >= 0; i = received.nextSetBit(i + 1)) {
            if (offset < offsets[i] + lengths[i] && offsets[i] < offset + fragment.length) throw new ProtocolException("Overlapping fragments");
        }
        System.arraycopy(fragment, 0, data, offset, fragment.length);
        offsets[number] = offset; lengths[number] = fragment.length; received.set(number);
        fragmentsReceived++; bytesReceived += fragment.length;
        if (fragmentsReceived == offsets.length) {
            if (bytesReceived != data.length) throw new ProtocolException("Fragment group contains gaps");
            return Optional.of(data.clone());
        }
        return Optional.empty();
    }
}
