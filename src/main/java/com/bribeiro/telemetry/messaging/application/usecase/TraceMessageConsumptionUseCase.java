package com.bribeiro.telemetry.messaging.application.usecase;

import com.bribeiro.telemetry.messaging.domain.model.MessagingOperation;
import com.bribeiro.telemetry.messaging.domain.tracing.MessagingTracer;

/**
 * Traces the processing of a message: extracts the producer context and creates a CONSUMER span as its child.
 */
public final class TraceMessageConsumptionUseCase extends TracedMessagingUseCase {

    public TraceMessageConsumptionUseCase(MessagingTracer tracer) {
        super(tracer);
    }

    @Override
    protected MessagingOperation supportedOperation() {
        return MessagingOperation.PROCESS;
    }
}
