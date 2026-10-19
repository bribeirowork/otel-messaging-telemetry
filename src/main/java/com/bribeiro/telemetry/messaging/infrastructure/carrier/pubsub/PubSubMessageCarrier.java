package com.bribeiro.telemetry.messaging.infrastructure.carrier.pubsub;

import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrier;
import com.bribeiro.telemetry.messaging.domain.model.MessagingSystem;
import com.google.pubsub.v1.PubsubMessage;

import java.util.Map;
import java.util.Optional;

/**
 * Read-only carrier over {@link PubsubMessage} (Google Cloud Pub/Sub).
 * To publish, add the headers with
 * {@code PubsubMessage.newBuilder().putAllAttributes(telemetry.currentPropagationHeaders())}.
 */
public class PubSubMessageCarrier implements MessageCarrier {

    private final PubsubMessage message;
    private final Map<String, String> attributes;

    public PubSubMessageCarrier(PubsubMessage message) {
        this.message = message;
        this.attributes = message.getAttributesMap();
    }

    @Override
    public Iterable<String> keys() {
        return attributes.keySet();
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    @Override
    public void put(String key, String value) {
        // PubsubMessage is immutable (protobuf)
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public MessagingSystem system() {
        return MessagingSystem.GCP_PUBSUB;
    }

    @Override
    public Optional<String> messageId() {
        return Optional.of(message.getMessageId()).filter(id -> !id.isBlank());
    }
}
