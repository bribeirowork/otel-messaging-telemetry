package com.telemetry.messaging.spring.annotation;

import com.telemetry.messaging.spring.config.MessagingTelemetryMarkerConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables message tracing. Put it on a {@code @Configuration} class (or on the main class).
 *
 * <pre>{@code
 * @Configuration
 * @EnableMessagingTelemetry
 * public class TelemetryConfig implements MessagingTelemetryConfigurer { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(MessagingTelemetryMarkerConfiguration.class)
public @interface EnableMessagingTelemetry {
}
