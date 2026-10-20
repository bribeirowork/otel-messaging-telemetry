package com.telemetry.messaging.domain.tracing;

import com.telemetry.messaging.domain.model.TraceIdentity;

/**
 * Active span of a messaging operation. Must always be closed (try-with-resources).
 */
public interface MessagingSpan extends AutoCloseable {

    TraceIdentity identity();

    void setAttribute(String key, String value);

    void recordFailure(Throwable error);

    /** Ends the span and restores the previous context. */
    @Override
    void close();
}
