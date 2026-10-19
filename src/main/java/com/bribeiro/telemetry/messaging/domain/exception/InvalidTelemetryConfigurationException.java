package com.bribeiro.telemetry.messaging.domain.exception;

/**
 * Thrown when the telemetry backend configuration is invalid (e.g. missing endpoint or credential).
 */
public class InvalidTelemetryConfigurationException extends MessagingTelemetryException {

    public InvalidTelemetryConfigurationException(String message) {
        super(message);
    }
}
