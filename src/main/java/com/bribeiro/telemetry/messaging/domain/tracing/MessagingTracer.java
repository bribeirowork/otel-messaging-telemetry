package com.bribeiro.telemetry.messaging.domain.tracing;

import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrier;
import com.bribeiro.telemetry.messaging.domain.model.MessageMetadata;
import com.bribeiro.telemetry.messaging.domain.model.TraceIdentity;

import java.util.Map;
import java.util.Optional;

/**
 * Outbound port for the tracing mechanism. The default implementation uses OpenTelemetry,
 * but the domain and application layers do not depend on it.
 */
public interface MessagingTracer {

    /**
     * Starts a span for the message and makes it the current span.
     * <ul>
     *     <li>PUBLISH: uses the current context as parent and injects the context into the carrier.</li>
     *     <li>PROCESS: extracts the context from the carrier and uses it as parent, continuing the producer trace.</li>
     * </ul>
     */
    MessagingSpan startSpan(MessageMetadata metadata, MessageCarrier carrier);

    /** Propagation headers (e.g. {@code traceparent}) of the current context, to attach manually to messages. */
    Map<String, String> currentPropagationHeaders();

    Optional<TraceIdentity> currentTrace();
}
