package com.telemetry.messaging.infrastructure.exporter.feign;

import com.telemetry.messaging.domain.backend.Compression;
import com.telemetry.messaging.domain.backend.ExporterEndpoint;
import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.slf4j.Slf4jLogger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Builds the {@link OtlpTraceClient} through Feign with authentication headers, compression, timeouts and retry.
 */
public final class OtlpTraceClientFactory {

    private OtlpTraceClientFactory() {
    }

    public static OtlpTraceClient create(ExporterEndpoint endpoint,
                                         Compression compression,
                                         Duration connectTimeout,
                                         Duration readTimeout) {
        return Feign.builder()
                .requestInterceptor(headersInterceptor(endpoint, compression))
                .errorDecoder(new OtlpErrorDecoder())
                .retryer(new Retryer.Default(200, TimeUnit.SECONDS.toMillis(2), 3))
                .options(new Request.Options(
                        connectTimeout.toMillis(), TimeUnit.MILLISECONDS,
                        readTimeout.toMillis(), TimeUnit.MILLISECONDS,
                        true))
                .logger(new Slf4jLogger(OtlpTraceClient.class))
                .logLevel(Logger.Level.BASIC)
                .target(OtlpTraceClient.class, endpoint.baseUrl());
    }

    private static RequestInterceptor headersInterceptor(ExporterEndpoint endpoint, Compression compression) {
        return template -> {
            endpoint.headers().forEach(template::header);
            // Feign's Client.Default gzips the body automatically when this header is present
            if (compression == Compression.GZIP) {
                template.header("Content-Encoding", "gzip");
            }
        };
    }
}
