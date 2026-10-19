package com.bribeiro.telemetry.messaging.infrastructure.exporter.backend;

import com.bribeiro.telemetry.messaging.domain.backend.BackendConnection;
import com.bribeiro.telemetry.messaging.domain.backend.TelemetryBackend;

import java.util.Map;

/**
 * Dynatrace: environment OTLP API ({@code /api/v2/otlp}), authenticated with {@code Authorization: Api-Token <token>}.
 * The token needs the {@code openTelemetryTrace.ingest} scope. Dynatrace only accepts OTLP as binary protobuf,
 * which is exactly the format sent by this library.
 */
public class DynatraceBackendProfile extends AbstractBackendProfile {

    private static final String OTLP_PATH = "/api/v2/otlp";

    @Override
    public TelemetryBackend backend() {
        return TelemetryBackend.DYNATRACE;
    }

    @Override
    protected String baseUrl(BackendConnection connection) {
        String endpoint = requiredEndpoint(connection, "https://{environment-id}.live.dynatrace.com").strip();
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        // accepts both the environment URL and a URL already containing /api/v2/otlp (or an ActiveGate)
        return endpoint.contains(OTLP_PATH) ? endpoint : endpoint + OTLP_PATH;
    }

    @Override
    protected Map<String, String> authenticationHeaders(BackendConnection connection) {
        String token = requiredApiKey(connection, "Gere um Access Token com o escopo openTelemetryTrace.ingest.");
        return Map.of("Authorization", token.startsWith("Api-Token ") ? token : "Api-Token " + token);
    }
}
