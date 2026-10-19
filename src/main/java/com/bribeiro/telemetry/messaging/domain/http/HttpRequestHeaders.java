package com.bribeiro.telemetry.messaging.domain.http;

import java.util.Optional;

/**
 * Read-only view of the headers of an incoming HTTP request, where the caller's trace context arrives.
 */
public interface HttpRequestHeaders {

    Iterable<String> names();

    Optional<String> get(String name);
}
