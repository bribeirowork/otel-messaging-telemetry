package com.telemetry.messaging.domain.http;

import com.telemetry.messaging.domain.exception.MessagingTelemetryException;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Value object describing an incoming HTTP request handled by a controller.
 * Holds only what is safe to export: the query string and the body are never captured.
 */
public final class HttpRequestMetadata {

    /** Methods known by the HTTP semantic conventions; anything else is reported as {@code _OTHER}. */
    private static final Set<String> KNOWN_METHODS =
            Set.of("GET", "HEAD", "POST", "PUT", "DELETE", "CONNECT", "OPTIONS", "TRACE", "PATCH");
    private static final String OTHER_METHOD = "_OTHER";

    private final String method;
    private final String originalMethod;
    private final String path;
    private final String scheme;
    private final String serverAddress;
    private final Integer serverPort;
    private final String clientAddress;
    private final String userAgent;

    private HttpRequestMetadata(Builder builder) {
        if (builder.method == null || builder.method.isBlank()) {
            throw new MessagingTelemetryException("O método HTTP da requisição é obrigatório");
        }
        if (builder.path == null || !builder.path.startsWith("/")) {
            throw new MessagingTelemetryException("O path da requisição deve começar com '/'");
        }
        String normalized = builder.method.strip().toUpperCase(Locale.ROOT);
        this.method = KNOWN_METHODS.contains(normalized) ? normalized : OTHER_METHOD;
        this.originalMethod = builder.method.strip();
        this.path = builder.path;
        this.scheme = builder.scheme;
        this.serverAddress = builder.serverAddress;
        this.serverPort = builder.serverPort;
        this.clientAddress = builder.clientAddress;
        this.userAgent = builder.userAgent;
    }

    public static Builder builder(String method, String path) {
        return new Builder(method, path);
    }

    /**
     * Span name following the semantic conventions: {@code "{method} {route}"}, or just the method while the
     * route is unknown (raw paths are never used, to keep span names low-cardinality).
     */
    public String spanName(String route) {
        return route == null || route.isBlank() ? method : method + " " + route;
    }

    public String method() {
        return method;
    }

    /** The method as sent by the client, present only when it was mapped to {@code _OTHER}. */
    public Optional<String> originalMethod() {
        return OTHER_METHOD.equals(method) ? Optional.of(originalMethod) : Optional.empty();
    }

    public String path() {
        return path;
    }

    public Optional<String> scheme() {
        return Optional.ofNullable(scheme);
    }

    public Optional<String> serverAddress() {
        return Optional.ofNullable(serverAddress);
    }

    public Optional<Integer> serverPort() {
        return Optional.ofNullable(serverPort);
    }

    public Optional<String> clientAddress() {
        return Optional.ofNullable(clientAddress);
    }

    public Optional<String> userAgent() {
        return Optional.ofNullable(userAgent);
    }

    public static final class Builder {

        private final String method;
        private final String path;
        private String scheme;
        private String serverAddress;
        private Integer serverPort;
        private String clientAddress;
        private String userAgent;

        private Builder(String method, String path) {
            this.method = method;
            this.path = path;
        }

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder serverAddress(String serverAddress) {
            this.serverAddress = serverAddress;
            return this;
        }

        public Builder serverPort(Integer serverPort) {
            this.serverPort = serverPort != null && serverPort > 0 ? serverPort : null;
            return this;
        }

        public Builder clientAddress(String clientAddress) {
            this.clientAddress = clientAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public HttpRequestMetadata build() {
            return new HttpRequestMetadata(this);
        }
    }
}
