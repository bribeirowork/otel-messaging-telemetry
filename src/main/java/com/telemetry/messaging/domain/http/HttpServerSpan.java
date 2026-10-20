package com.telemetry.messaging.domain.http;

import com.telemetry.messaging.domain.model.TraceIdentity;

/**
 * Active SERVER span of an HTTP request.
 * <p>
 * Ending the span and leaving its scope are separate steps, because asynchronous requests
 * ({@code CompletableFuture}, {@code DeferredResult}) leave the request thread before the response is written.
 */
public interface HttpServerSpan {

    TraceIdentity identity();

    /** Records the response and ends the span. Calls after the first one are ignored. */
    void complete(HttpOutcome outcome);

    /** Records the failure, then the response, and ends the span. */
    void fail(Throwable error, HttpOutcome outcome);

    /** Restores the previous context on the current thread. Must be called on the thread that started the span. */
    void leaveScope();
}
