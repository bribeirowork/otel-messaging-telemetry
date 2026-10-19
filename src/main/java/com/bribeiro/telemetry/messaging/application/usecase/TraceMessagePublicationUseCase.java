package com.bribeiro.telemetry.messaging.application.usecase;

import com.bribeiro.telemetry.messaging.domain.model.MessagingOperation;
import com.bribeiro.telemetry.messaging.domain.tracing.MessagingTracer;

/**
 * Traces the publication of a message: creates the PRODUCER span and injects the context into the headers.
 */
public final class TraceMessagePublicationUseCase extends TracedMessagingUseCase {

    public TraceMessagePublicationUseCase(MessagingTracer tracer) {
        super(tracer);
    }

    @Override
    protected MessagingOperation supportedOperation() {
        return MessagingOperation.PUBLISH;
    }
}
