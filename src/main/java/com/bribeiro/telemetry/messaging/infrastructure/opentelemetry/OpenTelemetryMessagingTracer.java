package com.bribeiro.telemetry.messaging.infrastructure.opentelemetry;

import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrier;
import com.bribeiro.telemetry.messaging.domain.model.MessageMetadata;
import com.bribeiro.telemetry.messaging.domain.model.MessagingOperation;
import com.bribeiro.telemetry.messaging.domain.model.TraceIdentity;
import com.bribeiro.telemetry.messaging.domain.tracing.MessagingSpan;
import com.bribeiro.telemetry.messaging.domain.tracing.MessagingTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter for the {@link MessagingTracer} port using the OpenTelemetry API.
 */
public class OpenTelemetryMessagingTracer implements MessagingTracer {

    public static final String INSTRUMENTATION_NAME = "com.bribeiro.telemetry.messaging";

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    public OpenTelemetryMessagingTracer(OpenTelemetry openTelemetry, String instrumentationVersion) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, instrumentationVersion);
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
    }

    @Override
    public MessagingSpan startSpan(MessageMetadata metadata, MessageCarrier carrier) {
        boolean publishing = metadata.operation() == MessagingOperation.PUBLISH;

        Context parent = publishing
                ? Context.current()
                : propagator.extract(Context.current(), carrier, MessageCarrierTextMapAccessor.INSTANCE);

        SpanBuilder builder = tracer.spanBuilder(metadata.resolveSpanName())
                .setParent(parent)
                .setSpanKind(publishing ? SpanKind.PRODUCER : SpanKind.CONSUMER)
                .setAttribute(MessagingAttributes.SYSTEM, metadata.system().semanticName())
                .setAttribute(MessagingAttributes.OPERATION_TYPE, metadata.operation().semanticName())
                .setAttribute(MessagingAttributes.OPERATION_NAME, metadata.operation().spanVerb())
                .setAttribute(MessagingAttributes.DESTINATION_NAME, metadata.destination().name());

        metadata.messageId().ifPresent(id -> builder.setAttribute(MessagingAttributes.MESSAGE_ID, id));
        metadata.conversationId().ifPresent(id -> builder.setAttribute(MessagingAttributes.CONVERSATION_ID, id));
        metadata.payloadSizeBytes().ifPresent(size -> builder.setAttribute(MessagingAttributes.BODY_SIZE, size));
        metadata.customAttributes().forEach(builder::setAttribute);

        Span span = builder.startSpan();
        Context spanContext = parent.with(span);

        if (publishing && carrier.isWritable()) {
            propagator.inject(spanContext, carrier, MessageCarrierTextMapAccessor.INSTANCE);
        }
        return new OpenTelemetryMessagingSpan(span, spanContext.makeCurrent());
    }

    @Override
    public Map<String, String> currentPropagationHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        propagator.inject(Context.current(), headers, (map, key, value) -> {
            if (map != null && value != null) {
                map.put(key, value);
            }
        });
        return headers;
    }

    @Override
    public Optional<TraceIdentity> currentTrace() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid()
                ? Optional.of(new TraceIdentity(context.getTraceId(), context.getSpanId()))
                : Optional.empty();
    }
}
