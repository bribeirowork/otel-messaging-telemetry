package com.telemetry.messaging.infrastructure.exporter.backend;

import com.telemetry.messaging.domain.backend.BackendConnection;
import com.telemetry.messaging.domain.backend.TelemetryBackend;

import java.util.Map;

/**
 * Elastic Observability (APM Server, Elastic Cloud or managed OTLP endpoint). Traces show up in Kibana,
 * under Observability &gt; APM. The credential can be an API Key ({@code ApiKey ...}) or a secret token ({@code Bearer ...}).
 * Without a prefix it is treated as an API Key.
 */
public class ElasticBackendProfile extends AbstractBackendProfile {

    @Override
    public TelemetryBackend backend() {
        return TelemetryBackend.ELASTIC;
    }

    @Override
    protected String baseUrl(BackendConnection connection) {
        return requiredEndpoint(connection, "https://my-deployment.apm.us-east-1.aws.cloud.es.io:443");
    }

    @Override
    protected Map<String, String> authenticationHeaders(BackendConnection connection) {
        return connection.apiKeyValue()
                .map(key -> key.startsWith("ApiKey ") || key.startsWith("Bearer ") ? key : "ApiKey " + key)
                .map(value -> Map.of("Authorization", value))
                .orElse(Map.of()); // a self-hosted APM Server may have no authentication
    }
}
