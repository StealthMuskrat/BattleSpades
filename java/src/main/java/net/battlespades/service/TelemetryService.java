// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.service;
import java.util.*;
import java.util.concurrent.*;
/** Metrics and optional queue logger share one host lifetime. */
public final class TelemetryService {
    private final RuntimeMetrics metrics=new RuntimeMetrics();private final LoggingRuntime logging;private CompletionStage<Void> stopping;
    public TelemetryService(LoggingRuntime logging){this.logging=logging;}
    public RuntimeMetrics metrics(){return metrics;}public Optional<LoggingRuntime> logging(){return Optional.ofNullable(logging);}
    public Map<String,Number> snapshot(){var snapshot=new LinkedHashMap<>(metrics.snapshot());snapshot.put("logging_dropped_records",logging==null?0:logging.droppedRecords());return Collections.unmodifiableMap(snapshot);}
    public boolean stopped(){return stopping!=null&&stopping.toCompletableFuture().isDone();}
    public CompletionStage<Void> stop(){if(stopping==null)stopping=logging==null?CompletableFuture.completedFuture(null):logging.stop();return stopping;}
}
