package com.bribeiro.telemetry.messaging.infrastructure.opentelemetry;

import com.bribeiro.telemetry.messaging.application.usecase.TraceMessageConsumptionUseCase;
import com.bribeiro.telemetry.messaging.application.usecase.TraceMessagePublicationUseCase;
import com.bribeiro.telemetry.messaging.domain.model.MessageMetadata;
import com.bribeiro.telemetry.messaging.domain.model.MessagingOperation;
import com.bribeiro.telemetry.messaging.domain.model.MessagingSystem;
import com.bribeiro.telemetry.messaging.infrastructure.carrier.generic.MapMessageCarrier;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenTelemetryMessagingTracerTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private TraceMessagePublicationUseCase publication;
    private TraceMessageConsumptionUseCase consumption;

    @BeforeEach
    void setUp() {
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        OpenTelemetryMessagingTracer tracer = new OpenTelemetryMessagingTracer(sdk, "test");
        publication = new TraceMessagePublicationUseCase(tracer);
        consumption = new TraceMessageConsumptionUseCase(tracer);
    }

    @Test
    void consumerSpanShouldContinueProducerTraceThroughHeaders() throws Throwable {
        Map<String, Object> headers = new HashMap<>();

        publication.execute(metadata(MessagingOperation.PUBLISH), new MapMessageCarrier(headers), () -> "sent");
        assertThat(headers).containsKey("traceparent");

        consumption.execute(metadata(MessagingOperation.PROCESS), new MapMessageCarrier(headers), () -> "processed");

        List<SpanData> spans = exporter.getFinishedSpanItems();
        SpanData producer = spans.get(0);
        SpanData consumer = spans.get(1);

        assertThat(producer.getKind()).isEqualTo(SpanKind.PRODUCER);
        assertThat(producer.getName()).isEqualTo("send orders");
        assertThat(consumer.getKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(consumer.getTraceId()).isEqualTo(producer.getTraceId());
        assertThat(consumer.getParentSpanId()).isEqualTo(producer.getSpanId());
        assertThat(consumer.getAttributes().get(MessagingAttributes.SYSTEM)).isEqualTo("kafka");
        assertThat(consumer.getAttributes().get(MessagingAttributes.DESTINATION_NAME)).isEqualTo("orders");
        assertThat(consumer.getAttributes().get(MessagingAttributes.MESSAGE_ID)).isEqualTo("msg-1");
    }

    @Test
    void shouldRecordFailureAndRethrow() {
        assertThatThrownBy(() -> consumption.execute(metadata(MessagingOperation.PROCESS),
                new MapMessageCarrier(new HashMap<>()), () -> {
                    throw new IllegalStateException("out of stock");
                }))
                .isInstanceOf(IllegalStateException.class);

        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getAttributes().get(MessagingAttributes.ERROR_TYPE)).isEqualTo(IllegalStateException.class.getName());
        assertThat(span.getEvents()).anyMatch(event -> event.getName().equals("exception"));
    }

    @Test
    void shouldRejectOperationNotSupportedByUseCase() {
        assertThatThrownBy(() -> publication.execute(metadata(MessagingOperation.PROCESS),
                new MapMessageCarrier(new HashMap<>()), () -> null))
                .hasMessageContaining("PUBLISH");
    }

    private static MessageMetadata metadata(MessagingOperation operation) {
        return MessageMetadata.builder(MessagingSystem.KAFKA, operation)
                .destination("orders")
                .messageId("msg-1")
                .build();
    }
}
