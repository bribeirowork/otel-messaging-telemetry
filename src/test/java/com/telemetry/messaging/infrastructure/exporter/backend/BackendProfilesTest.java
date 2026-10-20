package com.telemetry.messaging.infrastructure.exporter.backend;

import com.telemetry.messaging.domain.backend.BackendConnection;
import com.telemetry.messaging.domain.backend.ExporterEndpoint;
import com.telemetry.messaging.domain.backend.TelemetryBackend;
import com.telemetry.messaging.domain.exception.InvalidTelemetryConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackendProfilesTest {

    private final BackendProfileRegistry registry = new BackendProfileRegistry(List.of());

    @Test
    void newRelicShouldUseUsEndpointAndApiKeyHeaderByDefault() {
        ExporterEndpoint endpoint = resolve(connection(TelemetryBackend.NEW_RELIC, null, "license-123", null));

        assertThat(endpoint.tracesUrl()).isEqualTo("https://otlp.nr-data.net/v1/traces");
        assertThat(endpoint.headers()).containsEntry("api-key", "license-123");
    }

    @Test
    void newRelicShouldUseEuEndpointForEuRegion() {
        ExporterEndpoint endpoint = resolve(connection(TelemetryBackend.NEW_RELIC, null, "key", "eu"));

        assertThat(endpoint.tracesUrl()).isEqualTo("https://otlp.eu01.nr-data.net/v1/traces");
    }

    @Test
    void newRelicShouldRequireLicenseKey() {
        assertThatThrownBy(() -> resolve(connection(TelemetryBackend.NEW_RELIC, null, null, null)))
                .isInstanceOf(InvalidTelemetryConfigurationException.class)
                .hasMessageContaining("api-key");
    }

    @Test
    void dynatraceShouldAppendOtlpPathAndApiTokenPrefix() {
        ExporterEndpoint endpoint = resolve(connection(
                TelemetryBackend.DYNATRACE, "https://abc12345.live.dynatrace.com/", "dt0c01.TOKEN", null));

        assertThat(endpoint.tracesUrl()).isEqualTo("https://abc12345.live.dynatrace.com/api/v2/otlp/v1/traces");
        assertThat(endpoint.headers()).containsEntry("Authorization", "Api-Token dt0c01.TOKEN");
    }

    @Test
    void dynatraceShouldRequireEndpoint() {
        assertThatThrownBy(() -> resolve(connection(TelemetryBackend.DYNATRACE, null, "token", null)))
                .isInstanceOf(InvalidTelemetryConfigurationException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void elasticShouldPrefixApiKeyAndKeepBearerToken() {
        ExporterEndpoint apiKey = resolve(connection(TelemetryBackend.ELASTIC, "https://apm.example.com:443", "base64key", null));
        ExporterEndpoint bearer = resolve(connection(TelemetryBackend.ELASTIC, "https://apm.example.com", "Bearer secret", null));

        assertThat(apiKey.headers()).containsEntry("Authorization", "ApiKey base64key");
        assertThat(bearer.headers()).containsEntry("Authorization", "Bearer secret");
    }

    @Test
    void otlpShouldDefaultToLocalCollectorAndNormalizeTracesSuffix() {
        assertThat(resolve(connection(TelemetryBackend.OTLP, null, null, null)).tracesUrl())
                .isEqualTo("http://localhost:4318/v1/traces");
        assertThat(resolve(connection(TelemetryBackend.OTLP, "http://collector:4318/v1/traces", null, null)).tracesUrl())
                .isEqualTo("http://collector:4318/v1/traces");
    }

    @Test
    void customHeadersShouldOverrideProfileHeaders() {
        BackendConnection connection = new BackendConnection(TelemetryBackend.NEW_RELIC, null, "key", null,
                Map.of("api-key", "override", "x-team", "payments"), null, null, null);

        assertThat(resolve(connection).headers())
                .containsEntry("api-key", "override")
                .containsEntry("x-team", "payments");
    }

    @Test
    void connectionToStringShouldNotLeakApiKey() {
        assertThat(connection(TelemetryBackend.NEW_RELIC, null, "super-secret", null).toString())
                .doesNotContain("super-secret");
    }

    private ExporterEndpoint resolve(BackendConnection connection) {
        return registry.profileFor(connection.backend()).resolve(connection);
    }

    private static BackendConnection connection(TelemetryBackend backend, String endpoint, String apiKey, String region) {
        return new BackendConnection(backend, endpoint, apiKey, region, Map.of(), null, null, null);
    }
}
