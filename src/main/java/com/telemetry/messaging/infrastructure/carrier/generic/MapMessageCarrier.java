package com.telemetry.messaging.infrastructure.carrier.generic;

import com.telemetry.messaging.domain.carrier.MessageCarrier;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Generic carrier over a {@code Map}. Works for any protocol whose send operation accepts a header map
 * (e.g. {@code KafkaTemplate} with headers, Pub/Sub attributes, HTTP headers of a webhook...).
 */
public class MapMessageCarrier implements MessageCarrier {

    private final Map<String, Object> headers;

    @SuppressWarnings("unchecked")
    public MapMessageCarrier(Map<String, ?> headers) {
        this.headers = (Map<String, Object>) headers;
    }

    @Override
    public Iterable<String> keys() {
        return headers.keySet();
    }

    @Override
    public Optional<String> get(String key) {
        Object value = headers.get(key);
        if (value instanceof byte[] bytes) {
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        }
        return Optional.ofNullable(value).map(String::valueOf);
    }

    @Override
    public void put(String key, String value) {
        try {
            headers.put(key, value);
        } catch (UnsupportedOperationException immutableMap) {
            // immutable map: nowhere to propagate the context
        }
    }
}
