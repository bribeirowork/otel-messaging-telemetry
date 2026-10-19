package com.bribeiro.telemetry.messaging.application;

import com.bribeiro.telemetry.messaging.application.usecase.TraceMessageConsumptionUseCase;
import com.bribeiro.telemetry.messaging.application.usecase.TraceMessagePublicationUseCase;
import com.bribeiro.telemetry.messaging.application.usecase.TracedWork;
import com.bribeiro.telemetry.messaging.domain.carrier.EmptyMessageCarrier;
import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrier;
import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrierResolver;
import com.bribeiro.telemetry.messaging.domain.exception.MessagingTelemetryException;
import com.bribeiro.telemetry.messaging.domain.model.MessageMetadata;
import com.bribeiro.telemetry.messaging.domain.model.TraceIdentity;
import com.bribeiro.telemetry.messaging.domain.tracing.MessagingTracer;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Public facade for programmatic use (without annotations).
 *
 * <pre>{@code
 * telemetry.tracePublish(metadata, producerRecord, () -> kafkaTemplate.send(producerRecord));
 * }</pre>
 */
public class MessagingTelemetry {

    private final TraceMessagePublicationUseCase publication;
    private final TraceMessageConsumptionUseCase consumption;
    private final MessageCarrierResolver carrierResolver;
    private final MessagingTracer tracer;

    public MessagingTelemetry(TraceMessagePublicationUseCase publication,
                              TraceMessageConsumptionUseCase consumption,
                              MessageCarrierResolver carrierResolver,
                              MessagingTracer tracer) {
        this.publication = publication;
        this.consumption = consumption;
        this.carrierResolver = carrierResolver;
        this.tracer = tracer;
    }

    public <T> T tracePublish(MessageMetadata metadata, Object message, Supplier<T> work) {
        return unchecked(() -> publication.execute(metadata, carrierOf(message), work::get));
    }

    public void tracePublish(MessageMetadata metadata, Object message, Runnable work) {
        tracePublish(metadata, message, () -> {
            work.run();
            return null;
        });
    }

    public <T> T traceConsume(MessageMetadata metadata, Object message, Supplier<T> work) {
        return unchecked(() -> consumption.execute(metadata, carrierOf(message), work::get));
    }

    public void traceConsume(MessageMetadata metadata, Object message, Runnable work) {
        traceConsume(metadata, message, () -> {
            work.run();
            return null;
        });
    }

    /**
     * W3C headers ({@code traceparent}, {@code tracestate}, {@code baggage}) of the current context.
     * Use it to attach the context to immutable messages, such as SQS {@code SendMessageRequest} or {@code PubsubMessage}.
     */
    public Map<String, String> currentPropagationHeaders() {
        return tracer.currentPropagationHeaders();
    }

    public Optional<TraceIdentity> currentTrace() {
        return tracer.currentTrace();
    }

    private MessageCarrier carrierOf(Object message) {
        if (message instanceof MessageCarrier carrier) {
            return carrier;
        }
        return carrierResolver.resolve(message).orElse(EmptyMessageCarrier.INSTANCE);
    }

    private static <T> T unchecked(TracedWork<T> work) {
        try {
            return work.execute();
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable checked) {
            throw new MessagingTelemetryException("Falha ao executar operação rastreada", checked);
        }
    }
}
