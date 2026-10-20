package com.telemetry.messaging.infrastructure.opentelemetry;

import com.telemetry.messaging.domain.carrier.MessageCarrier;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

/**
 * Bridge between the {@link MessageCarrier} port and the OpenTelemetry propagators (W3C, B3, Jaeger...).
 */
enum MessageCarrierTextMapAccessor implements TextMapGetter<MessageCarrier>, TextMapSetter<MessageCarrier> {

    INSTANCE;

    @Override
    public Iterable<String> keys(MessageCarrier carrier) {
        return carrier.keys();
    }

    @Override
    public String get(MessageCarrier carrier, String key) {
        return carrier == null ? null : carrier.get(key).orElse(null);
    }

    @Override
    public void set(MessageCarrier carrier, String key, String value) {
        if (carrier != null && carrier.isWritable() && value != null) {
            carrier.put(key, value);
        }
    }
}
