package com.bribeiro.telemetry.messaging.infrastructure.exporter.backend;

import com.bribeiro.telemetry.messaging.domain.backend.BackendConnection;
import com.bribeiro.telemetry.messaging.domain.backend.TelemetryBackend;

import java.util.Map;

/**
 * Any OTLP/HTTP receiver (OpenTelemetry Collector, Jaeger, Grafana Tempo, SigNoz...).
 * When an api-key is provided, it is sent as {@code Authorization: Bearer}.
 */
public class OtlpBackendProfile extends AbstractBackendProfile {

    static final String DEFAULT_ENDPOINT = "http://localhost:4318";

    @Override
    public TelemetryBackend backend() {
        return TelemetryBackend.OTLP;
    }

    @Override
    protected String baseUrl(BackendConnection connection) {
        return connection.endpointValue().orElse(DEFAULT_ENDPOINT);
    }

    @Override
    protected Map<String, String> authenticationHeaders(BackendConnection connection) {
        return connection.apiKeyValue()
                .map(key -> Map.of("Authorization", key.contains(" ") ? key : "Bearer " + key))
                .orElse(Map.of());
    }
}
