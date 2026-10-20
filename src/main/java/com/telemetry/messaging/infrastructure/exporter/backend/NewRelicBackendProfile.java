package com.telemetry.messaging.infrastructure.exporter.backend;

import com.telemetry.messaging.domain.backend.BackendConnection;
import com.telemetry.messaging.domain.backend.TelemetryBackend;

import java.util.Locale;
import java.util.Map;

/**
 * New Relic: native OTLP endpoint, authenticated through the {@code api-key} header with the Ingest License Key.
 */
public class NewRelicBackendProfile extends AbstractBackendProfile {

    static final String US_ENDPOINT = "https://otlp.nr-data.net";
    static final String EU_ENDPOINT = "https://otlp.eu01.nr-data.net";
    static final String FEDRAMP_ENDPOINT = "https://gov-otlp.nr-data.net";

    @Override
    public TelemetryBackend backend() {
        return TelemetryBackend.NEW_RELIC;
    }

    @Override
    protected String baseUrl(BackendConnection connection) {
        return connection.endpointValue().orElseGet(() -> switch (connection.regionValue()
                .map(region -> region.toUpperCase(Locale.ROOT)).orElse("US")) {
            case "EU" -> EU_ENDPOINT;
            case "FEDRAMP", "GOV" -> FEDRAMP_ENDPOINT;
            default -> US_ENDPOINT;
        });
    }

    @Override
    protected Map<String, String> authenticationHeaders(BackendConnection connection) {
        return Map.of("api-key", requiredApiKey(connection, "Use a 'Ingest - License' key da conta New Relic."));
    }
}
