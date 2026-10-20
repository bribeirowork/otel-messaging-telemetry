package com.telemetry.messaging.infrastructure.opentelemetry;

import com.telemetry.messaging.domain.http.HttpRequestHeaders;
import com.telemetry.messaging.domain.http.HttpRequestMetadata;
import com.telemetry.messaging.domain.http.HttpServerSpan;
import com.telemetry.messaging.domain.http.HttpServerTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

/**
 * Adapter for the {@link HttpServerTracer} port using the OpenTelemetry API.
 */
public class OpenTelemetryHttpServerTracer implements HttpServerTracer {

    private static final TextMapGetter<HttpRequestHeaders> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpRequestHeaders headers) {
            return headers.names();
        }

        @Override
        public String get(HttpRequestHeaders headers, String key) {
            return headers == null ? null : headers.get(key).orElse(null);
        }
    };

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    public OpenTelemetryHttpServerTracer(OpenTelemetry openTelemetry, String instrumentationVersion) {
        this.tracer = openTelemetry.getTracer(OpenTelemetryMessagingTracer.INSTRUMENTATION_NAME, instrumentationVersion);
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
    }

    @Override
    public HttpServerSpan start(HttpRequestMetadata metadata, HttpRequestHeaders headers) {
        Context parent = propagator.extract(Context.current(), headers, GETTER);

        // the route is only known after handler mapping, so the span is renamed when it completes
        SpanBuilder builder = tracer.spanBuilder(metadata.spanName(null))
                .setParent(parent)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(HttpAttributes.REQUEST_METHOD, metadata.method())
                .setAttribute(HttpAttributes.URL_PATH, metadata.path());

        metadata.originalMethod().ifPresent(method -> builder.setAttribute(HttpAttributes.REQUEST_METHOD_ORIGINAL, method));
        metadata.scheme().ifPresent(scheme -> builder.setAttribute(HttpAttributes.URL_SCHEME, scheme));
        metadata.serverAddress().ifPresent(address -> builder.setAttribute(HttpAttributes.SERVER_ADDRESS, address));
        metadata.serverPort().ifPresent(port -> builder.setAttribute(HttpAttributes.SERVER_PORT, port.longValue()));
        metadata.clientAddress().ifPresent(address -> builder.setAttribute(HttpAttributes.CLIENT_ADDRESS, address));
        metadata.userAgent().ifPresent(agent -> builder.setAttribute(HttpAttributes.USER_AGENT, agent));

        Span span = builder.startSpan();
        return new OpenTelemetryHttpServerSpan(span, parent.with(span).makeCurrent(), metadata);
    }
}
