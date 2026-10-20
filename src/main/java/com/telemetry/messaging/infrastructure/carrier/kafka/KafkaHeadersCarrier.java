package com.telemetry.messaging.infrastructure.carrier.kafka;

import com.telemetry.messaging.domain.carrier.MessageCarrier;
import com.telemetry.messaging.domain.model.Destination;
import com.telemetry.messaging.domain.model.MessagingSystem;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Carrier over Kafka {@link Headers} (ProducerRecord / ConsumerRecord).
 */
public class KafkaHeadersCarrier implements MessageCarrier {

    private final Headers headers;
    private final String topic;
    private final String messageId;

    public KafkaHeadersCarrier(Headers headers, String topic, String messageId) {
        this.headers = headers;
        this.topic = topic;
        this.messageId = messageId;
    }

    @Override
    public Iterable<String> keys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Header header : headers) {
            keys.add(header.key());
        }
        return keys;
    }

    @Override
    public Optional<String> get(String key) {
        Header header = headers.lastHeader(key);
        return header == null || header.value() == null
                ? Optional.empty()
                : Optional.of(new String(header.value(), StandardCharsets.UTF_8));
    }

    @Override
    public void put(String key, String value) {
        try {
            headers.remove(key);
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalStateException readOnly) {
            // headers of an already sent record become read-only
        }
    }

    @Override
    public MessagingSystem system() {
        return MessagingSystem.KAFKA;
    }

    @Override
    public Optional<Destination> destination() {
        return Optional.ofNullable(topic).map(Destination::of);
    }

    @Override
    public Optional<String> messageId() {
        return Optional.ofNullable(messageId);
    }
}
