package com.bribeiro.telemetry.messaging.domain;

import com.bribeiro.telemetry.messaging.domain.exception.MessagingTelemetryException;
import com.bribeiro.telemetry.messaging.domain.http.HttpOutcome;
import com.bribeiro.telemetry.messaging.domain.http.HttpRequestMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRequestMetadataTest {

    @Test
    void shouldNameSpanWithMethodAndRoute() {
        HttpRequestMetadata metadata = HttpRequestMetadata.builder("get", "/orders/42").build();

        assertThat(metadata.spanName("/orders/{id}")).isEqualTo("GET /orders/{id}");
        assertThat(metadata.spanName(null)).isEqualTo("GET");
    }

    @Test
    void shouldMapUnknownMethodToOther() {
        HttpRequestMetadata metadata = HttpRequestMetadata.builder("PURGE", "/cache").build();

        assertThat(metadata.method()).isEqualTo("_OTHER");
        assertThat(metadata.originalMethod()).contains("PURGE");
    }

    @Test
    void shouldRejectRelativePath() {
        assertThatThrownBy(() -> HttpRequestMetadata.builder("GET", "orders").build())
                .isInstanceOf(MessagingTelemetryException.class);
    }

    @Test
    void onlyServerErrorsShouldBeFailures() {
        assertThat(new HttpOutcome(404, null).isServerError()).isFalse();
        assertThat(new HttpOutcome(503, "/orders").isServerError()).isTrue();
        assertThatThrownBy(() -> new HttpOutcome(42, null)).isInstanceOf(MessagingTelemetryException.class);
    }
}
