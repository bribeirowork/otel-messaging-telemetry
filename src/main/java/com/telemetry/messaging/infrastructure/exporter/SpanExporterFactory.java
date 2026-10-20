package com.telemetry.messaging.infrastructure.exporter;

import com.telemetry.messaging.domain.backend.BackendConnection;
import com.telemetry.messaging.domain.backend.ExporterEndpoint;
import com.telemetry.messaging.infrastructure.exporter.backend.BackendProfileRegistry;
import com.telemetry.messaging.infrastructure.exporter.feign.OtlpTraceClient;
import com.telemetry.messaging.infrastructure.exporter.feign.OtlpTraceClientFactory;
import com.telemetry.messaging.infrastructure.exporter.otlp.OtlpProtobufMapper;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the {@link SpanExporter} matching the configured backend.
 */
public class SpanExporterFactory {

    private static final Logger log = LoggerFactory.getLogger(SpanExporterFactory.class);

    private final BackendProfileRegistry registry;

    public SpanExporterFactory(BackendProfileRegistry registry) {
        this.registry = registry;
    }

    public SpanExporter create(BackendConnection connection) {
        return switch (connection.backend()) {
            case NONE -> SpanExporter.composite();
            case LOGGING -> new LoggingSpanExporter();
            case NEW_RELIC, DYNATRACE, ELASTIC, OTLP -> otlp(connection);
        };
    }

    private SpanExporter otlp(BackendConnection connection) {
        ExporterEndpoint endpoint = registry.profileFor(connection.backend()).resolve(connection);
        OtlpTraceClient client = OtlpTraceClientFactory.create(
                endpoint, connection.compression(), connection.connectTimeout(), connection.readTimeout());
        log.info("Messaging telemetry exportando traces para {} em {}", connection.backend(), endpoint.tracesUrl());
        return new FeignOtlpSpanExporter(client, new OtlpProtobufMapper(), connection.compression(), endpoint.tracesUrl());
    }
}
