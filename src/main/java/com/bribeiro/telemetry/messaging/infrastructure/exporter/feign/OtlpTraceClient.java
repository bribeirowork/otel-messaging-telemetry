package com.bribeiro.telemetry.messaging.infrastructure.exporter.feign;

import feign.Headers;
import feign.RequestLine;

/**
 * Feign client for the OTLP/HTTP traces endpoint. The body is an {@code ExportTraceServiceRequest} serialized as
 * binary protobuf, the format accepted by New Relic, Dynatrace, Elastic and the OpenTelemetry Collector.
 */
public interface OtlpTraceClient {

    @RequestLine("POST /v1/traces")
    @Headers("Content-Type: application/x-protobuf")
    void export(byte[] body);
}
