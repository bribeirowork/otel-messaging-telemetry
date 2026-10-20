package com.telemetry.messaging.infrastructure.carrier.generic;

import com.telemetry.messaging.domain.carrier.MessageCarrier;
import com.telemetry.messaging.domain.carrier.MessageCarrierFactory;

import java.util.Map;

public class MapMessageCarrierFactory implements MessageCarrierFactory {

    @Override
    public boolean supports(Object candidate) {
        return candidate instanceof Map<?, ?>;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MessageCarrier create(Object candidate) {
        return new MapMessageCarrier((Map<String, ?>) candidate);
    }

    /** Lowest priority: only used when no specific adapter recognizes the argument. */
    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }
}
