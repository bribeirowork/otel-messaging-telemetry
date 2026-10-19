package com.bribeiro.telemetry.messaging.application.usecase;

import com.bribeiro.telemetry.messaging.domain.http.HttpRequestHeaders;
import com.bribeiro.telemetry.messaging.domain.http.HttpRequestMetadata;
import com.bribeiro.telemetry.messaging.domain.http.HttpServerSpan;
import com.bribeiro.telemetry.messaging.domain.http.HttpServerTracer;

import java.util.Objects;

/**
 * Traces an incoming HTTP request: opens the SERVER span that the web adapter completes once the response is known.
 */
public final class TraceHttpRequestUseCase {

    private final HttpServerTracer tracer;

    public TraceHttpRequestUseCase(HttpServerTracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer é obrigatório");
    }

    public HttpServerSpan start(HttpRequestMetadata metadata, HttpRequestHeaders headers) {
        Objects.requireNonNull(metadata, "metadata é obrigatório");
        Objects.requireNonNull(headers, "headers é obrigatório");
        return tracer.start(metadata, headers);
    }
}
