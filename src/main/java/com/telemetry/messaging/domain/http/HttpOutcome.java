package com.telemetry.messaging.domain.http;

import com.telemetry.messaging.domain.exception.MessagingTelemetryException;

/**
 * Result of a handled HTTP request.
 *
 * @param statusCode response status code
 * @param route      matched route template (e.g. {@code /orders/{id}}), or {@code null} when no handler matched
 */
public record HttpOutcome(int statusCode, String route) {

    public HttpOutcome {
        if (statusCode < 100 || statusCode > 599) {
            throw new MessagingTelemetryException("Status HTTP inválido: " + statusCode);
        }
        route = route == null || route.isBlank() ? null : route;
    }

    /**
     * For SERVER spans only 5xx is a failure: 4xx responses are the client's error, not the service's.
     */
    public boolean isServerError() {
        return statusCode >= 500;
    }
}
