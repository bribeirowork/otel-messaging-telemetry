package com.bribeiro.telemetry.messaging.domain.backend;

/**
 * Observability backends the spans can be exported to.
 */
public enum TelemetryBackend {

    /** New Relic through its native OTLP endpoint (otlp.nr-data.net). */
    NEW_RELIC,
    /** Dynatrace through the environment OTLP API (/api/v2/otlp). */
    DYNATRACE,
    /** Elastic Observability (APM Server / Elastic Cloud), visualized in Kibana. */
    ELASTIC,
    /** Any OTLP/HTTP endpoint: OpenTelemetry Collector, Jaeger, Grafana Tempo, SigNoz, Uptrace... */
    OTLP,
    /** Writes the spans to the application log. Useful during development. */
    LOGGING,
    /** Exports nothing. Spans are still created and propagated. */
    NONE
}
