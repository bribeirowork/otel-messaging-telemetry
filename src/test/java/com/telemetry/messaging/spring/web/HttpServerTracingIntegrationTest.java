package com.telemetry.messaging.spring.web;

import com.telemetry.messaging.infrastructure.opentelemetry.HttpAttributes;
import com.telemetry.messaging.infrastructure.opentelemetry.MessagingAttributes;
import com.telemetry.messaging.spring.annotation.EnableMessagingTelemetry;
import com.telemetry.messaging.spring.annotation.TracedProducer;
import com.telemetry.messaging.spring.config.MessagingTelemetryConfigurer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = HttpServerTracingIntegrationTest.TestApplication.class,
        properties = {
                "spring.application.name=orders-api",
                "messaging.telemetry.exporter.backend=NONE",
                "messaging.telemetry.http.exclude-paths=/internal/**"
        })
@AutoConfigureMockMvc
class HttpServerTracingIntegrationTest {

    static final InMemorySpanExporter SPANS = InMemorySpanExporter.create();
    static final String CALLER_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    static final String CALLER_SPAN_ID = "00f067aa0ba902b7";

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void reset() {
        SPANS.reset();
    }

    @Test
    void shouldCreateServerSpanNamedAfterRoute() throws Exception {
        mockMvc.perform(get("/orders/42").header("User-Agent", "curl/8.0")).andExpect(status().isOk());

        SpanData span = single();
        assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(span.getName()).isEqualTo("GET /orders/{id}");
        assertThat(span.getAttributes().get(HttpAttributes.ROUTE)).isEqualTo("/orders/{id}");
        assertThat(span.getAttributes().get(HttpAttributes.URL_PATH)).isEqualTo("/orders/42");
        assertThat(span.getAttributes().get(HttpAttributes.REQUEST_METHOD)).isEqualTo("GET");
        assertThat(span.getAttributes().get(HttpAttributes.RESPONSE_STATUS_CODE)).isEqualTo(200L);
        assertThat(span.getAttributes().get(HttpAttributes.USER_AGENT)).isEqualTo("curl/8.0");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    }

    @Test
    void shouldContinueCallerTraceAndBeParentOfPublishedMessage() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("traceparent", "00-" + CALLER_TRACE_ID + "-" + CALLER_SPAN_ID + "-01"))
                .andExpect(status().isAccepted());

        List<SpanData> spans = SPANS.getFinishedSpanItems();
        SpanData producer = spans.stream().filter(s -> s.getKind() == SpanKind.PRODUCER).findFirst().orElseThrow();
        SpanData server = spans.stream().filter(s -> s.getKind() == SpanKind.SERVER).findFirst().orElseThrow();

        assertThat(server.getName()).isEqualTo("POST /orders");
        assertThat(server.getTraceId()).isEqualTo(CALLER_TRACE_ID);
        assertThat(server.getParentSpanId()).isEqualTo(CALLER_SPAN_ID);

        assertThat(producer.getTraceId()).isEqualTo(CALLER_TRACE_ID);
        assertThat(producer.getParentSpanId()).isEqualTo(server.getSpanId());
        assertThat(producer.getAttributes().get(MessagingAttributes.DESTINATION_NAME)).isEqualTo("orders-created");
    }

    @Test
    void clientErrorShouldNotMarkSpanAsError() throws Exception {
        mockMvc.perform(get("/orders/0")).andExpect(status().isNotFound());

        SpanData span = single();
        assertThat(span.getAttributes().get(HttpAttributes.RESPONSE_STATUS_CODE)).isEqualTo(404L);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    }

    @Test
    void unmatchedRouteShouldUseOnlyTheMethodAsSpanName() throws Exception {
        mockMvc.perform(get("/does-not-exist/123")).andExpect(status().isNotFound());

        SpanData span = single();
        assertThat(span.getName()).isEqualTo("GET");
        assertThat(span.getAttributes().get(HttpAttributes.ROUTE)).isNull();
    }

    @Test
    void exceptionHandledByExceptionHandlerShouldBeRecorded() throws Exception {
        mockMvc.perform(get("/orders/fail/handled")).andExpect(status().isInternalServerError());

        SpanData span = single();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getAttributes().get(MessagingAttributes.ERROR_TYPE)).isEqualTo(IllegalStateException.class.getName());
        assertThat(span.getEvents()).anyMatch(event -> event.getName().equals("exception"));
    }

    @Test
    void unhandledExceptionShouldBeRecordedAsServerError() {
        assertThatThrownBy(() -> mockMvc.perform(get("/orders/fail/unhandled")));

        SpanData span = single();
        assertThat(span.getName()).isEqualTo("GET /orders/fail/unhandled");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getAttributes().get(HttpAttributes.RESPONSE_STATUS_CODE)).isEqualTo(500L);
    }

    @Test
    void asyncRequestShouldEndSpanOnlyWhenResponseIsWritten() throws Exception {
        MvcResult result = mockMvc.perform(get("/orders/async")).andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        SpanData span = single();
        assertThat(span.getName()).isEqualTo("GET /orders/async");
        assertThat(span.getAttributes().get(HttpAttributes.RESPONSE_STATUS_CODE)).isEqualTo(200L);
    }

    @Test
    void excludedPathsShouldNotBeTraced() throws Exception {
        mockMvc.perform(get("/internal/ping")).andExpect(status().isOk());

        assertThat(SPANS.getFinishedSpanItems()).isEmpty();
    }

    private static SpanData single() {
        assertThat(SPANS.getFinishedSpanItems()).hasSize(1);
        return SPANS.getFinishedSpanItems().get(0);
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableMessagingTelemetry
    static class TestApplication implements MessagingTelemetryConfigurer {

        @Override
        public void configureTracerProvider(SdkTracerProviderBuilder tracerProvider) {
            tracerProvider.addSpanProcessor(SimpleSpanProcessor.create(SPANS));
        }

        @Bean
        OrderController orderController(OrderPublisher publisher) {
            return new OrderController(publisher);
        }

        @Bean
        OrderPublisher orderPublisher() {
            return new OrderPublisher();
        }
    }

    static class OrderPublisher {

        @TracedProducer
        public void publish(ProducerRecord<String, String> record) {
            // kafkaTemplate.send(record)
        }
    }

    @RestController
    static class OrderController {

        private final OrderPublisher publisher;

        OrderController(OrderPublisher publisher) {
            this.publisher = publisher;
        }

        @GetMapping("/orders/{id}")
        ResponseEntity<String> find(@PathVariable String id) {
            return "0".equals(id) ? ResponseEntity.notFound().build() : ResponseEntity.ok("{\"id\":" + id + "}");
        }

        @PostMapping("/orders")
        ResponseEntity<Void> create() {
            publisher.publish(new ProducerRecord<>("orders-created", "42", "{\"id\":42}"));
            return ResponseEntity.accepted().build();
        }

        @GetMapping("/orders/fail/handled")
        String failHandled() {
            throw new IllegalStateException("database unavailable");
        }

        @GetMapping("/orders/fail/unhandled")
        String failUnhandled() {
            throw new UnsupportedOperationException("not implemented");
        }

        @GetMapping("/orders/async")
        CompletableFuture<String> async() {
            return CompletableFuture.supplyAsync(() -> "done");
        }

        @GetMapping("/internal/ping")
        String ping() {
            return "pong";
        }

        @ExceptionHandler(IllegalStateException.class)
        ResponseEntity<String> handle(IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error.getMessage());
        }
    }
}
