package com.telemetry.messaging.domain.model;

/**
 * Identity of the current trace/span. Useful to log the traceId and correlate logs with the APM backend.
 */
public record TraceIdentity(String traceId, String spanId) {
}
