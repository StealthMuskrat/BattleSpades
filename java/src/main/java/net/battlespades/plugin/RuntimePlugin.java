// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.plugin;
import java.util.concurrent.*;
import net.battlespades.match.ModeEvents;
import net.battlespades.session.*;
/** Trusted Java plugin SPI. Lifecycle and event prefixes run on the gameplay owner. */
public interface RuntimePlugin {
    interface Context {ApplicationHost host();StandardMatchDomain match();Executor owner();void broadcast(String message);void log(String message);}
    String name();
    default String version(){return "1.0.0";}
    default String author(){return "Unknown";}
    default String description(){return "";}
    default CompletionStage<Void> onLoad(Context context){return CompletableFuture.completedFuture(null);}
    default CompletionStage<Void> onUnload(){return CompletableFuture.completedFuture(null);}
    default CompletionStage<Void> onEnable(){return CompletableFuture.completedFuture(null);}
    default CompletionStage<Void> onDisable(){return CompletableFuture.completedFuture(null);}
    /** Source dispatch ignores return values; cancellation policies stay with authoritative owners. */
    default CompletionStage<Void> onEvent(ModeEvents.Event event){return CompletableFuture.completedFuture(null);}
}
