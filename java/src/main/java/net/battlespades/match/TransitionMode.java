// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.concurrent.CompletionStage;

/** Actual lifecycle required by match transitions; catalog metadata is not a mode implementation. */
public interface TransitionMode extends ModeEvents.Mode {
    CompletionStage<Void> cancelEndSequence();
    void beginRetirement(boolean endRound);
    CompletionStage<Void> deactivate();
    CompletionStage<Void> startMode();
    default CompletionStage<Void> cancelForTransition(boolean ownEnding){return cancelEndSequence();}
    default CompletionStage<Void> deactivateForTransition(boolean ownEnding){return deactivate();}
}
