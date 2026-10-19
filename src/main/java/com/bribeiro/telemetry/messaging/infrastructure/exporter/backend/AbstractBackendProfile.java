package com.bribeiro.telemetry.messaging.infrastructure.exporter.backend;

import com.bribeiro.telemetry.messaging.domain.backend.BackendConnection;
import com.bribeiro.telemetry.messaging.domain.backend.BackendProfile;
import com.bribeiro.telemetry.messaging.domain.backend.ExporterEndpoint;
import com.bribeiro.telemetry.messaging.domain.exception.InvalidTelemetryConfigurationException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Profile template: each vendor defines the URL and the authentication headers;
 * the user's custom headers always have the final say.
 */
abstract class AbstractBackendProfile implements BackendProfile {

    @Override
    public final ExporterEndpoint resolve(BackendConnection connection) {
        Map<String, String> headers = new LinkedHashMap<>(authenticationHeaders(connection));
        headers.putAll(connection.headers());
        return new ExporterEndpoint(baseUrl(connection), headers);
    }

    protected abstract String baseUrl(BackendConnection connection);

    protected abstract Map<String, String> authenticationHeaders(BackendConnection connection);

    protected String requiredApiKey(BackendConnection connection, String hint) {
        return connection.apiKeyValue().orElseThrow(() -> new InvalidTelemetryConfigurationException(
                "messaging.telemetry.exporter.api-key é obrigatório para " + backend() + ". " + hint));
    }

    protected String requiredEndpoint(BackendConnection connection, String example) {
        return connection.endpointValue().orElseThrow(() -> new InvalidTelemetryConfigurationException(
                "messaging.telemetry.exporter.endpoint é obrigatório para " + backend() + ". Exemplo: " + example));
    }
}
