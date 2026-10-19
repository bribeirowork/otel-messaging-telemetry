package com.bribeiro.telemetry.messaging.domain.carrier;

import java.util.List;
import java.util.Optional;

/**
 * Null Object carrier used when the traced method does not receive any adaptable message.
 * The span is still created, just without context propagation.
 */
public final class EmptyMessageCarrier implements MessageCarrier {

    public static final EmptyMessageCarrier INSTANCE = new EmptyMessageCarrier();

    private EmptyMessageCarrier() {
    }

    @Override
    public Iterable<String> keys() {
        return List.of();
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.empty();
    }

    @Override
    public void put(String key, String value) {
        // nowhere to write to
    }

    @Override
    public boolean isWritable() {
        return false;
    }
}
