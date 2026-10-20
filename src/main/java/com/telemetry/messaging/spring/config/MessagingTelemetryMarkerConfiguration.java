package com.telemetry.messaging.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registered by {@code @EnableMessagingTelemetry}. The auto-configuration is only activated when this marker exists,
 * making the library an explicit opt-in.
 */
@Configuration(proxyBeanMethods = false)
public class MessagingTelemetryMarkerConfiguration {

    @Bean
    public Marker messagingTelemetryMarker() {
        return new Marker();
    }

    public static final class Marker {
    }
}
