package com.telemetry.messaging.domain.backend;

import com.telemetry.messaging.domain.exception.InvalidTelemetryConfigurationException;

import java.util.Map;

/**
 * Resolved OTLP/HTTP endpoint: base URL (without the {@code /v1/traces} suffix) and authentication headers.
 */
public record ExporterEndpoint(String baseUrl, Map<String, String> headers) {

    private static final String TRACES_SUFFIX = "/v1/traces";

    public ExporterEndpoint {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new InvalidTelemetryConfigurationException("A URL do endpoint OTLP não pode ser vazia");
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new InvalidTelemetryConfigurationException("A URL do endpoint OTLP deve começar com http:// ou https://");
        }
        baseUrl = normalize(baseUrl);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /** Accepts both the base URL and the full URL ending in /v1/traces. */
    private static String normalize(String url) {
        String value = url.strip();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith(TRACES_SUFFIX)) {
            value = value.substring(0, value.length() - TRACES_SUFFIX.length());
        }
        return value;
    }

    public String tracesUrl() {
        return baseUrl + TRACES_SUFFIX;
    }

    @Override
    public String toString() {
        return "ExporterEndpoint[baseUrl=" + baseUrl + ", headers=" + headers.keySet() + "]";
    }
}
