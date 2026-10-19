package com.bribeiro.telemetry.messaging.infrastructure.exporter;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Exporter for local development: writes a summary of each span to the log.
 */
public class LoggingSpanExporter implements SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(LoggingSpanExporter.class);

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        for (SpanData span : spans) {
            log.info("span name='{}' kind={} traceId={} spanId={} parentSpanId={} status={} durationMs={} attributes={}",
                    span.getName(),
                    span.getKind(),
                    span.getTraceId(),
                    span.getSpanId(),
                    span.getParentSpanId(),
                    span.getStatus().getStatusCode(),
                    (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000,
                    span.getAttributes());
        }
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }
}
