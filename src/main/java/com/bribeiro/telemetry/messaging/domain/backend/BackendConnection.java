package com.bribeiro.telemetry.messaging.domain.backend;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Value object holding the data the user provided to connect to a backend.
 * Each {@link BackendProfile} interprets this data according to the vendor's rules.
 *
 * @param backend  selected vendor
 * @param endpoint base URL provided (optional for some vendors, which have a default value)
 * @param apiKey   credential (license key, API token, API key...)
 * @param region   vendor region, e.g. {@code US} or {@code EU} on New Relic
 * @param headers  additional headers sent on every request
 */
public record BackendConnection(
        TelemetryBackend backend,
        String endpoint,
        String apiKey,
        String region,
        Map<String, String> headers,
        Compression compression,
        Duration connectTimeout,
        Duration readTimeout) {

    public BackendConnection {
        Objects.requireNonNull(backend, "backend é obrigatório");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        compression = compression == null ? Compression.GZIP : compression;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
    }

    public Optional<String> endpointValue() {
        return Optional.ofNullable(endpoint).filter(value -> !value.isBlank());
    }

    public Optional<String> apiKeyValue() {
        return Optional.ofNullable(apiKey).filter(value -> !value.isBlank());
    }

    public Optional<String> regionValue() {
        return Optional.ofNullable(region).filter(value -> !value.isBlank());
    }

    @Override
    public String toString() {
        // never expose the credential in logs
        return "BackendConnection[backend=" + backend + ", endpoint=" + endpoint + ", region=" + region
                + ", apiKey=" + (apiKeyValue().isPresent() ? "****" : "<vazio>") + "]";
    }
}
