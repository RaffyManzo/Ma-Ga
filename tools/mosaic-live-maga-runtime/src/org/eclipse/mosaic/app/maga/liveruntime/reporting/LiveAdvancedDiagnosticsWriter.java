package org.eclipse.mosaic.app.maga.liveruntime.reporting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Buffered writer for transition-only advanced diagnostic events. */
public final class LiveAdvancedDiagnosticsWriter implements AutoCloseable {
    public static final String EVENT_FILE_NAME = "live_advanced_mobility_events.jsonl";
    public static final String SUMMARY_FILE_NAME = "live_advanced_diagnostics_summary.json";

    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();
    private final Path summaryPath;
    private final BufferedWriter eventWriter;
    private final int flushBatchSize;

    private long eventCount;
    private long flushCount;
    private int pendingSinceFlush;
    private boolean closed;

    public LiveAdvancedDiagnosticsWriter(Path reportingDir, int flushBatchSize) throws IOException {
        if (flushBatchSize < 1) {
            throw new IllegalArgumentException("flushBatchSize must be >= 1");
        }
        Files.createDirectories(reportingDir);
        this.summaryPath = reportingDir.resolve(SUMMARY_FILE_NAME);
        this.eventWriter = Files.newBufferedWriter(
                reportingDir.resolve(EVENT_FILE_NAME),
                StandardCharsets.UTF_8
        );
        this.flushBatchSize = flushBatchSize;
    }

    public void write(LiveAdvancedDiagnosticsEvent event) throws IOException {
        ensureOpen();
        eventWriter.write(gson.toJson(event.asMap()));
        eventWriter.newLine();
        eventCount++;
        pendingSinceFlush++;
        if (pendingSinceFlush >= flushBatchSize) {
            flushInternal();
        }
    }

    public void writeSummary(Map<String, Object> summary) throws IOException {
        ensureOpen();
        flushInternal();
        Files.writeString(
                summaryPath,
                gson.toJson(summary),
                StandardCharsets.UTF_8
        );
    }

    public long getEventCount() {
        return eventCount;
    }

    public long getFlushCount() {
        return flushCount;
    }

    public int getFlushBatchSize() {
        return flushBatchSize;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        flushInternal();
        closed = true;
        eventWriter.close();
    }

    private void flushInternal() throws IOException {
        if (pendingSinceFlush <= 0) {
            return;
        }
        eventWriter.flush();
        flushCount++;
        pendingSinceFlush = 0;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Advanced diagnostics writer is already closed");
        }
    }
}
