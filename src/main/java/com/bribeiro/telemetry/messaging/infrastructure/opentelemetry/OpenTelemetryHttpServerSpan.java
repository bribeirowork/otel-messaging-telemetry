package com.bribeiro.telemetry.messaging.infrastructure.opentelemetry;

import com.bribeiro.telemetry.messaging.domain.http.HttpOutcome;
import com.bribeiro.telemetry.messaging.domain.http.HttpRequestMetadata;
import com.bribeiro.telemetry.messaging.domain.http.HttpServerSpan;
import com.bribeiro.telemetry.messaging.domain.model.TraceIdentity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import java.util.concurrent.atomic.AtomicBoolean;

final class OpenTelemetryHttpServerSpan implements HttpServerSpan {

    private final Span span;
    private final Scope scope;
    private final HttpRequestMetadata metadata;
    private final AtomicBoolean ended = new AtomicBoolean(false);
    private final AtomicBoolean scopeClosed = new AtomicBoolean(false);

    OpenTelemetryHttpServerSpan(Span span, Scope scope, HttpRequestMetadata metadata) {
        this.span = span;
        this.scope = scope;
        this.metadata = metadata;
    }

    @Override
    public TraceIdentity identity() {
        return new TraceIdentity(span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
    }

    @Override
    public void complete(HttpOutcome outcome) {
        end(outcome, null);
    }

    @Override
    public void fail(Throwable error, HttpOutcome outcome) {
        end(outcome, error);
    }

    @Override
    public void leaveScope() {
        if (scopeClosed.compareAndSet(false, true)) {
            scope.close();
        }
    }

    private void end(HttpOutcome outcome, Throwable error) {
        if (!ended.compareAndSet(false, true)) {
            return;
        }
        span.setAttribute(HttpAttributes.RESPONSE_STATUS_CODE, (long) outcome.statusCode());
        if (outcome.route() != null) {
            span.setAttribute(HttpAttributes.ROUTE, outcome.route());
            span.updateName(metadata.spanName(outcome.route()));
        }

        if (error != null) {
            span.recordException(error);
            span.setAttribute(MessagingAttributes.ERROR_TYPE, error.getClass().getName());
            span.setStatus(StatusCode.ERROR, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        } else if (outcome.isServerError()) {
            span.setAttribute(MessagingAttributes.ERROR_TYPE, String.valueOf(outcome.statusCode()));
            span.setStatus(StatusCode.ERROR);
        }
        span.end();
    }
}
