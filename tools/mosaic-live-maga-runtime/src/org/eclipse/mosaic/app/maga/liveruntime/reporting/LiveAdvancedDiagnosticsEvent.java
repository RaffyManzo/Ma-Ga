package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight JSONL event used by the G04 advanced mobility diagnostics.
 *
 * <p>The event is deliberately independent from the MA-GA decision path. It is
 * only a deterministic container for values that have already been produced by
 * the live snapshot and runtime reporting pipeline.</p>
 */
public final class LiveAdvancedDiagnosticsEvent {
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public LiveAdvancedDiagnosticsEvent(
            String eventType,
            String runId,
            Long seed
    ) {
        fields.put("eventType", eventType);
        fields.put("runId", runId);
        fields.put("seed", seed);
    }

    public LiveAdvancedDiagnosticsEvent put(String key, Object value) {
        fields.put(key, value);
        return this;
    }

    Map<String, Object> asMap() {
        return new LinkedHashMap<>(fields);
    }
}
