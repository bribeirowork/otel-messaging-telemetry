package com.telemetry.messaging.domain.model;

import com.telemetry.messaging.domain.exception.MessagingTelemetryException;

/**
 * Value object representing a message destination: topic (Kafka/Pub/Sub), queue (SQS) or exchange (RabbitMQ).
 */
public record Destination(String name) {

    private static final String UNKNOWN = "unknown";

    public Destination {
        if (name == null || name.isBlank()) {
            throw new MessagingTelemetryException("O nome do destino da mensagem não pode ser vazio");
        }
        name = name.strip();
    }

    public static Destination of(String name) {
        return new Destination(name);
    }

    public static Destination unknown() {
        return new Destination(UNKNOWN);
    }

    public static Destination ofNullable(String name) {
        return name == null || name.isBlank() ? unknown() : new Destination(name);
    }

    public boolean isUnknown() {
        return UNKNOWN.equals(name);
    }
}
