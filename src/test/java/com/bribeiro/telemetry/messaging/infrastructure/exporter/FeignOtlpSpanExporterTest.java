package com.bribeiro.telemetry.messaging.infrastructure.exporter;

import com.bribeiro.telemetry.messaging.domain.backend.BackendConnection;
import com.bribeiro.telemetry.messaging.domain.backend.Compression;
import com.bribeiro.telemetry.messaging.domain.backend.TelemetryBackend;
import com.bribeiro.telemetry.messaging.infrastructure.exporter.backend.BackendProfileRegistry;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts a local HTTP server acting as the backend and checks what the Feign client actually sends.
 */
class FeignOtlpSpanExporterTest {

    private HttpServer server;
    private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();

    record CapturedRequest(String path, Map<String, List<String>> headers, byte[] body) {
    }

    @BeforeEach
    void startFakeBackend() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            captured.set(new CapturedRequest(exchange.getRequestURI().getPath(),
                    Map.copyOf(exchange.getRequestHeaders()), exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(responseStatus.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopFakeBackend() {
        server.stop(0);
    }

    @Test
    void shouldSendGzippedProtobufWithNewRelicAuthentication() throws Exception {
        SpanExporter exporter = exporterFor(TelemetryBackend.NEW_RELIC, "license-xyz", Compression.GZIP);
        List<SpanData> spans = sampleSpans();

        CompletableResultCode result = exporter.export(spans).join(5, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
        CapturedRequest request = captured.get();
        assertThat(request.path()).isEqualTo("/v1/traces");
        assertThat(header(request, "api-key")).isEqualTo("license-xyz");
        assertThat(header(request, "content-type")).isEqualTo("application/x-protobuf");
        assertThat(header(request, "content-encoding")).isEqualTo("gzip");

        ExportTraceServiceRequest body = ExportTraceServiceRequest.parseFrom(
                new GZIPInputStream(new ByteArrayInputStream(request.body())).readAllBytes());
        io.opentelemetry.proto.trace.v1.Span protoSpan = body.getResourceSpans(0).getScopeSpans(0).getSpans(0);

        assertThat(protoSpan.getName()).isEqualTo("send orders");
        assertThat(protoSpan.getKind()).isEqualTo(io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_PRODUCER);
        assertThat(protoSpan.getTraceId().toByteArray()).isEqualTo(spans.get(0).getSpanContext().getTraceIdBytes());
        assertThat(body.getResourceSpans(0).getResource().getAttributesList())
                .anyMatch(kv -> kv.getKey().equals("service.name") && kv.getValue().getStringValue().equals("orders-api"));
    }

    @Test
    void shouldSendDynatraceApiTokenUncompressed() throws Exception {
        SpanExporter exporter = exporterFor(TelemetryBackend.DYNATRACE, "dt0c01.abc", Compression.NONE);

        CompletableResultCode result = exporter.export(sampleSpans()).join(5, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
        assertThat(captured.get().path()).isEqualTo("/api/v2/otlp/v1/traces");
        assertThat(header(captured.get(), "authorization")).isEqualTo("Api-Token dt0c01.abc");
        assertThat(ExportTraceServiceRequest.parseFrom(captured.get().body()).getResourceSpansCount()).isEqualTo(1);
    }

    @Test
    void shouldRetryOnServiceUnavailableAndReportFailureWithoutThrowing() {
        responseStatus.set(503);
        SpanExporter exporter = exporterFor(TelemetryBackend.OTLP, null, Compression.NONE);

        CompletableResultCode result = exporter.export(sampleSpans()).join(10, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isFalse();
        assertThat(calls.get()).isEqualTo(3);
    }

    private SpanExporter exporterFor(TelemetryBackend backend, String apiKey, Compression compression) {
        String endpoint = "http://localhost:" + server.getAddress().getPort();
        BackendConnection connection = new BackendConnection(backend, endpoint, apiKey, null, Map.of(), compression, null, null);
        return new SpanExporterFactory(new BackendProfileRegistry(List.of())).create(connection);
    }

    private static String header(CapturedRequest request, String name) {
        return request.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(entry -> entry.getValue().get(0))
                .findFirst()
                .orElse(null);
    }

    private static List<SpanData> sampleSpans() {
        InMemorySpanExporter memory = InMemorySpanExporter.create();
        // shutting down the provider clears the InMemorySpanExporter, so spans are read before close
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().toBuilder().put("service.name", "orders-api").build())
                .addSpanProcessor(SimpleSpanProcessor.create(memory))
                .build()) {
            Span span = provider.get("test").spanBuilder("send orders").setSpanKind(SpanKind.PRODUCER).startSpan();
            span.setAttribute("messaging.system", "kafka");
            span.end();
            return memory.getFinishedSpanItems();
        }
    }
}
