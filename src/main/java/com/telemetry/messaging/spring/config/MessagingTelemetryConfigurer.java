package com.telemetry.messaging.spring.config;

import com.telemetry.messaging.domain.backend.BackendProfile;
import com.telemetry.messaging.domain.carrier.MessageCarrierFactory;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;

import java.util.List;

/**
 * Programmatic configuration class of the library. Implement it in a {@code @Configuration} bean to
 * complement (or override) what was defined in {@code application.yml}.
 * All methods are optional.
 */
public interface MessagingTelemetryConfigurer {

    /** Resource attributes (e.g. {@code team}, {@code cloud.region}) added to every span. */
    default void configureResource(ResourceBuilder resource) {
    }

    /** Access to the SDK builder: samplers, span processors and additional exporters. */
    default void configureTracerProvider(SdkTracerProviderBuilder tracerProvider) {
    }

    /** Allows replacing or composing the propagator (default: W3C TraceContext + W3C Baggage). */
    default TextMapPropagator configurePropagator(TextMapPropagator defaultPropagator) {
        return defaultPropagator;
    }

    /** Adds adapters for custom message types (e.g. a company envelope, NATS, ActiveMQ...). */
    default void addCarrierFactories(List<MessageCarrierFactory> factories) {
    }

    /** Adds or replaces backend profiles (e.g. a corporate OTLP backend with its own authentication). */
    default void addBackendProfiles(List<BackendProfile> profiles) {
    }
}
