package com.telemetry.messaging.domain.http;

/**
 * Outbound port that starts SERVER spans for incoming HTTP requests.
 * The span continues the caller's trace when the request carries a trace context (e.g. {@code traceparent}),
 * and becomes the parent of every span created while handling it, including {@code @TracedProducer} spans.
 */
public interface HttpServerTracer {

    HttpServerSpan start(HttpRequestMetadata metadata, HttpRequestHeaders headers);
}
