package com.telemetry.messaging.infrastructure.opentelemetry;

import com.telemetry.messaging.domain.model.TraceIdentity;
import com.telemetry.messaging.domain.tracing.MessagingSpan;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import java.util.concurrent.atomic.AtomicBoolean;

final class OpenTelemetryMessagingSpan implements MessagingSpan {

    private final Span span;
    private final Scope scope;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    OpenTelemetryMessagingSpan(Span span, Scope scope) {
        this.span = span;
        this.scope = scope;
    }

    @Override
    public TraceIdentity identity() {
        return new TraceIdentity(span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
    }

    @Override
    public void setAttribute(String key, String value) {
        if (key != null && value != null) {
            span.setAttribute(key, value);
        }
    }

    @Override
    public void recordFailure(Throwable error) {
        span.recordException(error);
        span.setAttribute(MessagingAttributes.ERROR_TYPE, error.getClass().getName());
        span.setStatus(StatusCode.ERROR, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            scope.close();
            span.end();
        }
    }
}
