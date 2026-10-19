package com.bribeiro.telemetry.messaging.spring.config;

import com.bribeiro.telemetry.messaging.application.usecase.TraceHttpRequestUseCase;
import com.bribeiro.telemetry.messaging.domain.http.HttpServerTracer;
import com.bribeiro.telemetry.messaging.infrastructure.opentelemetry.OpenTelemetryHttpServerTracer;
import com.bribeiro.telemetry.messaging.spring.web.HttpServerTracingFilter;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Traces the controllers of servlet web applications (Spring MVC). Turned off with
 * {@code messaging.telemetry.http.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.web.filter.OncePerRequestFilter"})
@ConditionalOnProperty(prefix = "messaging.telemetry.http", name = "enabled", havingValue = "true", matchIfMissing = true)
class HttpServerTracingConfiguration {

    /** Runs before Spring Security and the other filters, so authentication failures are traced too. */
    static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Bean
    @ConditionalOnMissingBean
    HttpServerTracer httpServerTracer(OpenTelemetry openTelemetry) {
        return new OpenTelemetryHttpServerTracer(openTelemetry, MessagingTelemetryAutoConfiguration.libraryVersion());
    }

    @Bean
    @ConditionalOnMissingBean
    TraceHttpRequestUseCase traceHttpRequestUseCase(HttpServerTracer tracer) {
        return new TraceHttpRequestUseCase(tracer);
    }

    @Bean
    @ConditionalOnMissingBean(name = "httpServerTracingFilter")
    FilterRegistrationBean<HttpServerTracingFilter> httpServerTracingFilter(TraceHttpRequestUseCase useCase,
                                                                            MessagingTelemetryProperties properties) {
        FilterRegistrationBean<HttpServerTracingFilter> registration = new FilterRegistrationBean<>(
                new HttpServerTracingFilter(useCase, properties.getHttp().getExcludePaths()));
        registration.setName("httpServerTracingFilter");
        registration.setOrder(FILTER_ORDER);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
