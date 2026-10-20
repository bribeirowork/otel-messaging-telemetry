package com.telemetry.messaging.spring.web;

import com.telemetry.messaging.domain.http.HttpRequestHeaders;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Optional;

/**
 * Adapts the headers of an {@link HttpServletRequest} to the {@link HttpRequestHeaders} port.
 */
class ServletRequestHeaders implements HttpRequestHeaders {

    private final HttpServletRequest request;

    ServletRequestHeaders(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public Iterable<String> names() {
        return Collections.list(request.getHeaderNames());
    }

    @Override
    public Optional<String> get(String name) {
        return Optional.ofNullable(request.getHeader(name));
    }
}
