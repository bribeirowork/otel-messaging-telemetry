package com.telemetry.messaging.domain.exception;

/**
 * Base exception of the library. Signals violations of the telemetry domain rules.
 */
public class MessagingTelemetryException extends RuntimeException {

    public MessagingTelemetryException(String message) {
        super(message);
    }

    public MessagingTelemetryException(String message, Throwable cause) {
        super(message, cause);
    }
}
