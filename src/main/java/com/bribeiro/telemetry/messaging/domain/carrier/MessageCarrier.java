package com.bribeiro.telemetry.messaging.domain.carrier;

import com.bribeiro.telemetry.messaging.domain.model.Destination;
import com.bribeiro.telemetry.messaging.domain.model.MessagingSystem;

import java.util.Optional;

/**
 * Port that abstracts "where the headers live" in a message.
 * <p>
 * This is what makes the library protocol agnostic: Kafka uses {@code Headers}, SQS uses
 * {@code MessageAttributes}, RabbitMQ uses {@code MessageProperties} and Pub/Sub uses {@code attributes},
 * but for the domain they are all just a key/value set where the trace context is propagated.
 */
public interface MessageCarrier {

    /** Keys present in the message. */
    Iterable<String> keys();

    /** Reads a header. */
    Optional<String> get(String key);

    /** Writes a header. Only called when {@link #isWritable()} is true. */
    void put(String key, String value);

    /**
     * Some message types are immutable (e.g. {@code PubsubMessage}, {@code software.amazon.awssdk...Message}).
     * In those cases the context is not injected and the producer should use
     * {@code MessagingTelemetry#currentPropagationHeaders()}.
     */
    default boolean isWritable() {
        return true;
    }

    /** Messaging system the message came from, when it can be inferred from the type. */
    default MessagingSystem system() {
        return MessagingSystem.GENERIC;
    }

    /** Destination (topic/queue) when the message object itself carries it, e.g. {@code ConsumerRecord#topic()}. */
    default Optional<Destination> destination() {
        return Optional.empty();
    }

    /** Message identifier when available in the object itself. */
    default Optional<String> messageId() {
        return Optional.empty();
    }
}
