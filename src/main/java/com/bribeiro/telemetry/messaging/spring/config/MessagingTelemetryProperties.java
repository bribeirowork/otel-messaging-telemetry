package com.bribeiro.telemetry.messaging.spring.config;

import com.bribeiro.telemetry.messaging.domain.backend.BackendConnection;
import com.bribeiro.telemetry.messaging.domain.backend.Compression;
import com.bribeiro.telemetry.messaging.domain.backend.TelemetryBackend;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code messaging.telemetry.*} properties.
 */
@ConfigurationProperties(prefix = "messaging.telemetry")
public class MessagingTelemetryProperties {

    /** Turns the library on/off without removing the annotation. */
    private boolean enabled = true;

    /** Service name in the backends. Default: spring.application.name. */
    private String serviceName;

    private String serviceVersion;

    /** Environment (deployment.environment.name), e.g. dev, staging, prod. */
    private String environment;

    /** Extra resource attributes. */
    private Map<String, String> resourceAttributes = new LinkedHashMap<>();

    /** Fraction of sampled traces (0.0 to 1.0). Honors the producer's decision when there is a parent. */
    private double samplingRatio = 1.0;

    private Exporter exporter = new Exporter();

    private Batch batch = new Batch();

    private Http http = new Http();

    public BackendConnection toBackendConnection() {
        return new BackendConnection(exporter.backend, exporter.endpoint, exporter.apiKey, exporter.region,
                exporter.headers, exporter.compression, exporter.connectTimeout, exporter.readTimeout);
    }

    public static class Exporter {

        /** Target backend: NEW_RELIC, DYNATRACE, ELASTIC, OTLP, LOGGING or NONE. */
        private TelemetryBackend backend = TelemetryBackend.LOGGING;

        /** Base URL of the OTLP/HTTP endpoint (without /v1/traces). */
        private String endpoint;

        /** License key (New Relic), API token (Dynatrace), API key/secret token (Elastic) or bearer token (OTLP). */
        private String apiKey;

        /** Vendor region. New Relic: US (default), EU or FEDRAMP. */
        private String region;

        /** Extra headers sent on every export. */
        private Map<String, String> headers = new LinkedHashMap<>();

        private Compression compression = Compression.GZIP;

        private Duration connectTimeout = Duration.ofSeconds(5);

        private Duration readTimeout = Duration.ofSeconds(10);

        public TelemetryBackend getBackend() {
            return backend;
        }

        public void setBackend(TelemetryBackend backend) {
            this.backend = backend;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public Compression getCompression() {
            return compression;
        }

        public void setCompression(Compression compression) {
            this.compression = compression;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Batch {

        /** Interval between exports. */
        private Duration scheduleDelay = Duration.ofSeconds(5);

        /** Spans held in memory awaiting export; beyond that, new spans are dropped. */
        private int maxQueueSize = 2048;

        private int maxExportBatchSize = 512;

        private Duration exportTimeout = Duration.ofSeconds(30);

        public Duration getScheduleDelay() {
            return scheduleDelay;
        }

        public void setScheduleDelay(Duration scheduleDelay) {
            this.scheduleDelay = scheduleDelay;
        }

        public int getMaxQueueSize() {
            return maxQueueSize;
        }

        public void setMaxQueueSize(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize;
        }

        public int getMaxExportBatchSize() {
            return maxExportBatchSize;
        }

        public void setMaxExportBatchSize(int maxExportBatchSize) {
            this.maxExportBatchSize = maxExportBatchSize;
        }

        public Duration getExportTimeout() {
            return exportTimeout;
        }

        public void setExportTimeout(Duration exportTimeout) {
            this.exportTimeout = exportTimeout;
        }
    }

    public static class Http {

        /** Creates a SERVER span for every request handled by the controllers (Spring MVC). */
        private boolean enabled = true;

        /** Ant-style paths that are never traced, relative to the context path. */
        private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**", "/favicon.ico"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Map<String, String> getResourceAttributes() {
        return resourceAttributes;
    }

    public void setResourceAttributes(Map<String, String> resourceAttributes) {
        this.resourceAttributes = resourceAttributes;
    }

    public double getSamplingRatio() {
        return samplingRatio;
    }

    public void setSamplingRatio(double samplingRatio) {
        this.samplingRatio = samplingRatio;
    }

    public Exporter getExporter() {
        return exporter;
    }

    public void setExporter(Exporter exporter) {
        this.exporter = exporter;
    }

    public Batch getBatch() {
        return batch;
    }

    public void setBatch(Batch batch) {
        this.batch = batch;
    }
}
