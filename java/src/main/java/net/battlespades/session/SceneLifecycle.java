// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

/** Shared strict lifecycle default for scenes and gameplay delegates. */
public interface SceneLifecycle {
    default void transitionDetach(int playerId){throw new UnsupportedOperationException("Transition detach service is not bound");}
}
