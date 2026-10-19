package com.bribeiro.telemetry.messaging.domain.backend;

/**
 * Strategy that knows the connection rules of an observability vendor
 * (default URL, authentication header, credential format).
 */
public interface BackendProfile {

    TelemetryBackend backend();

    ExporterEndpoint resolve(BackendConnection connection);
}
