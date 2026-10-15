// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Invalid peer input, rejected before domain dispatch. */
public final class ProtocolException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    public ProtocolException(String message) { super(message); }
}
