package com.telemetry.messaging.spring.config;

import com.telemetry.messaging.application.MessagingTelemetry;
import com.telemetry.messaging.application.usecase.TraceMessageConsumptionUseCase;
import com.telemetry.messaging.application.usecase.TraceMessagePublicationUseCase;
import com.telemetry.messaging.domain.backend.BackendConnection;
import com.telemetry.messaging.domain.backend.BackendProfile;
import com.telemetry.messaging.domain.backend.TelemetryBackend;
import com.telemetry.messaging.domain.carrier.MessageCarrierFactory;
import com.telemetry.messaging.domain.carrier.MessageCarrierResolver;
import com.telemetry.messaging.domain.tracing.MessagingTracer;
import com.telemetry.messaging.infrastructure.exporter.SpanExporterFactory;
import com.telemetry.messaging.infrastructure.exporter.backend.BackendProfileRegistry;
import com.telemetry.messaging.infrastructure.opentelemetry.OpenTelemetryMessagingTracer;
import com.telemetry.messaging.spring.aop.MessagingTelemetryAspect;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Wires the library: OpenTelemetry SDK, Feign exporter, broker adapters, use cases, the aspect and HTTP tracing.
 * Enabled by {@code @EnableMessagingTelemetry} and can be turned off with {@code messaging.telemetry.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnBean(MessagingTelemetryMarkerConfiguration.Marker.class)
@ConditionalOnProperty(prefix = "messaging.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MessagingTelemetryProperties.class)
@Import({MessageCarrierFactoriesConfiguration.class, HttpServerTracingConfiguration.class})
public class MessagingTelemetryAutoConfiguration {

    private static final String DEFAULT_VERSION = "1.0.0";

    @Bean
    @ConditionalOnMissingBean
    public BackendProfileRegistry backendProfileRegistry(ObjectProvider<BackendProfile> profileBeans,
                                                         ObjectProvider<MessagingTelemetryConfigurer> configurers) {
        List<BackendProfile> profiles = new ArrayList<>(profileBeans.orderedStream().toList());
        configurers.orderedStream().forEach(configurer -> configurer.addBackendProfiles(profiles));
        return new BackendProfileRegistry(profiles);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpanExporterFactory spanExporterFactory(BackendProfileRegistry registry) {
        return new SpanExporterFactory(registry);
    }

    /**
     * Creates the SDK only if the application does not have an {@link OpenTelemetry} yet (e.g. from the Java agent or the
     * official starter). In that case the library reuses the existing one and only creates the messaging spans.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public OpenTelemetrySdk openTelemetry(MessagingTelemetryProperties properties,
                                          SpanExporterFactory exporterFactory,
                                          ObjectProvider<MessagingTelemetryConfigurer> configurers,
                                          Environment environment) {
        List<MessagingTelemetryConfigurer> customizers = configurers.orderedStream().toList();

        SdkTracerProviderBuilder tracerProvider = SdkTracerProvider.builder()
                .setResource(resource(properties, environment, customizers))
                .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(properties.getSamplingRatio())));

        BackendConnection connection = properties.toBackendConnection();
        if (connection.backend() != TelemetryBackend.NONE) {
            MessagingTelemetryProperties.Batch batch = properties.getBatch();
            tracerProvider.addSpanProcessor(BatchSpanProcessor.builder(exporterFactory.create(connection))
                    .setScheduleDelay(batch.getScheduleDelay())
                    .setMaxQueueSize(batch.getMaxQueueSize())
                    .setMaxExportBatchSize(batch.getMaxExportBatchSize())
                    .setExporterTimeout(batch.getExportTimeout())
                    .build());
        }
        customizers.forEach(configurer -> configurer.configureTracerProvider(tracerProvider));

        TextMapPropagator propagator = TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance());
        for (MessagingTelemetryConfigurer configurer : customizers) {
            propagator = configurer.configurePropagator(propagator);
        }

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider.build())
                .setPropagators(ContextPropagators.create(propagator))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessagingTracer messagingTracer(OpenTelemetry openTelemetry) {
        return new OpenTelemetryMessagingTracer(openTelemetry, libraryVersion());
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageCarrierResolver messageCarrierResolver(ObjectProvider<MessageCarrierFactory> factoryBeans,
                                                         ObjectProvider<MessagingTelemetryConfigurer> configurers) {
        List<MessageCarrierFactory> factories = new ArrayList<>(factoryBeans.orderedStream().toList());
        configurers.orderedStream().forEach(configurer -> configurer.addCarrierFactories(factories));
        return new MessageCarrierResolver(factories);
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceMessagePublicationUseCase traceMessagePublicationUseCase(MessagingTracer tracer) {
        return new TraceMessagePublicationUseCase(tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceMessageConsumptionUseCase traceMessageConsumptionUseCase(MessagingTracer tracer) {
        return new TraceMessageConsumptionUseCase(tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessagingTelemetry messagingTelemetry(TraceMessagePublicationUseCase publication,
                                                 TraceMessageConsumptionUseCase consumption,
                                                 MessageCarrierResolver resolver,
                                                 MessagingTracer tracer) {
        return new MessagingTelemetry(publication, consumption, resolver, tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessagingTelemetryAspect messagingTelemetryAspect(TraceMessagePublicationUseCase publication,
                                                             TraceMessageConsumptionUseCase consumption,
                                                             MessageCarrierResolver resolver,
                                                             Environment environment) {
        return new MessagingTelemetryAspect(publication, consumption, resolver, environment);
    }

    private static Resource resource(MessagingTelemetryProperties properties,
                                     Environment environment,
                                     List<MessagingTelemetryConfigurer> configurers) {
        String serviceName = properties.getServiceName() != null
                ? properties.getServiceName()
                : environment.getProperty("spring.application.name", "unknown-service");

        ResourceBuilder resource = Resource.getDefault().toBuilder().put("service.name", serviceName);
        if (properties.getServiceVersion() != null) {
            resource.put("service.version", properties.getServiceVersion());
        }
        if (properties.getEnvironment() != null) {
            resource.put("deployment.environment.name", properties.getEnvironment());
            resource.put("deployment.environment", properties.getEnvironment()); // legacy key, still used by APMs
        }
        properties.getResourceAttributes().forEach(resource::put);
        configurers.forEach(configurer -> configurer.configureResource(resource));
        return resource.build();
    }

    static String libraryVersion() {
        String version = MessagingTelemetryAutoConfiguration.class.getPackage().getImplementationVersion();
        return version != null ? version : DEFAULT_VERSION;
    }
}
