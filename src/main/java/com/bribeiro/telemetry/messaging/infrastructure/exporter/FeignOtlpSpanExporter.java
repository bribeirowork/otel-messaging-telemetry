package com.bribeiro.telemetry.messaging.infrastructure.exporter;

import com.bribeiro.telemetry.messaging.domain.backend.Compression;
import com.bribeiro.telemetry.messaging.infrastructure.exporter.feign.OtlpTraceClient;
import com.bribeiro.telemetry.messaging.infrastructure.exporter.otlp.OtlpProtobufMapper;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link SpanExporter} that sends spans over OTLP/HTTP (protobuf) using the Feign client.
 * It is driven by the {@code BatchSpanProcessor}, so it runs outside the business thread.
 */
public class FeignOtlpSpanExporter implements SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(FeignOtlpSpanExporter.class);

    private final OtlpTraceClient client;
    private final OtlpProtobufMapper mapper;
    private final Compression compression;
    private final String target;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public FeignOtlpSpanExporter(OtlpTraceClient client, OtlpProtobufMapper mapper, Compression compression, String target) {
        this.client = client;
        this.mapper = mapper;
        this.compression = compression;
        this.target = target;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        if (shutdown.get()) {
            return CompletableResultCode.ofFailure();
        }
        if (spans.isEmpty()) {
            return CompletableResultCode.ofSuccess();
        }
        try {
            byte[] payload = mapper.toRequest(spans).toByteArray();
            // gzip compression is done by Feign itself when it sees the Content-Encoding header (OtlpTraceClientFactory)
            client.export(payload);
            return CompletableResultCode.ofSuccess();
        } catch (Exception error) {
            // telemetry must never bring the application down: just log and drop the batch
            log.warn("Falha ao exportar {} spans para {}: {}", spans.size(), target, error.getMessage());
            return CompletableResultCode.ofFailure();
        }
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        shutdown.set(true);
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public String toString() {
        return "FeignOtlpSpanExporter{target=" + target + ", compression=" + compression + "}";
    }
}
