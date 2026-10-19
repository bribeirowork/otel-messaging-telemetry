package com.bribeiro.telemetry.messaging.application.usecase;

import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrier;
import com.bribeiro.telemetry.messaging.domain.exception.MessagingTelemetryException;
import com.bribeiro.telemetry.messaging.domain.model.MessageMetadata;
import com.bribeiro.telemetry.messaging.domain.model.MessagingOperation;
import com.bribeiro.telemetry.messaging.domain.tracing.MessagingSpan;
import com.bribeiro.telemetry.messaging.domain.tracing.MessagingTracer;

import java.util.Objects;

/**
 * Template shared by the use cases: opens the span, runs the work, records failures and closes the span.
 */
abstract class TracedMessagingUseCase {

    private final MessagingTracer tracer;

    protected TracedMessagingUseCase(MessagingTracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer é obrigatório");
    }

    protected abstract MessagingOperation supportedOperation();

    public <T> T execute(MessageMetadata metadata, MessageCarrier carrier, TracedWork<T> work) throws Throwable {
        Objects.requireNonNull(metadata, "metadata é obrigatório");
        Objects.requireNonNull(carrier, "carrier é obrigatório");
        Objects.requireNonNull(work, "work é obrigatório");
        if (metadata.operation() != supportedOperation()) {
            throw new MessagingTelemetryException("O caso de uso " + getClass().getSimpleName()
                    + " aceita apenas a operação " + supportedOperation() + ", recebido: " + metadata.operation());
        }

        try (MessagingSpan span = tracer.startSpan(metadata, carrier)) {
            try {
                return work.execute();
            } catch (Throwable error) {
                span.recordFailure(error);
                throw error;
            }
        }
    }
}
