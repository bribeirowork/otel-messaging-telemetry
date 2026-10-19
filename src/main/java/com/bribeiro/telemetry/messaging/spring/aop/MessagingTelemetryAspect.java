package com.bribeiro.telemetry.messaging.spring.aop;

import com.bribeiro.telemetry.messaging.application.usecase.TraceMessageConsumptionUseCase;
import com.bribeiro.telemetry.messaging.application.usecase.TraceMessagePublicationUseCase;
import com.bribeiro.telemetry.messaging.domain.carrier.EmptyMessageCarrier;
import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrier;
import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrierResolver;
import com.bribeiro.telemetry.messaging.domain.model.Destination;
import com.bribeiro.telemetry.messaging.domain.model.MessageMetadata;
import com.bribeiro.telemetry.messaging.domain.model.MessagingOperation;
import com.bribeiro.telemetry.messaging.domain.model.MessagingSystem;
import com.bribeiro.telemetry.messaging.spring.annotation.TracedConsumer;
import com.bribeiro.telemetry.messaging.spring.annotation.TracedProducer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Inbound adapter: intercepts {@link TracedProducer} and {@link TracedConsumer}, builds the
 * {@link MessageMetadata} and delegates to the use cases.
 */
@Aspect
public class MessagingTelemetryAspect {

    private static final Logger log = LoggerFactory.getLogger(MessagingTelemetryAspect.class);

    private final TraceMessagePublicationUseCase publication;
    private final TraceMessageConsumptionUseCase consumption;
    private final MessageCarrierResolver carrierResolver;
    private final AnnotationValueResolver valueResolver;

    public MessagingTelemetryAspect(TraceMessagePublicationUseCase publication,
                                    TraceMessageConsumptionUseCase consumption,
                                    MessageCarrierResolver carrierResolver,
                                    Environment environment) {
        this.publication = publication;
        this.consumption = consumption;
        this.carrierResolver = carrierResolver;
        this.valueResolver = new AnnotationValueResolver(environment);
    }

    @Around("@annotation(tracedProducer)")
    public Object aroundProducer(ProceedingJoinPoint joinPoint, TracedProducer tracedProducer) throws Throwable {
        return trace(joinPoint, TracedMethodDefinition.of(tracedProducer));
    }

    @Around("@annotation(tracedConsumer)")
    public Object aroundConsumer(ProceedingJoinPoint joinPoint, TracedConsumer tracedConsumer) throws Throwable {
        return trace(joinPoint, TracedMethodDefinition.of(tracedConsumer));
    }

    private Object trace(ProceedingJoinPoint joinPoint, TracedMethodDefinition definition) throws Throwable {
        MessageCarrier carrier = carrierResolver.resolveFirst(joinPoint.getArgs()).orElse(EmptyMessageCarrier.INSTANCE);

        MessageMetadata metadata;
        try {
            metadata = buildMetadata(joinPoint, definition, carrier);
        } catch (RuntimeException invalidDefinition) {
            // an annotation configuration error must not prevent sending/processing the message
            log.warn("Não foi possível montar a telemetria de {}: {}",
                    joinPoint.getSignature().toShortString(), invalidDefinition.getMessage());
            return joinPoint.proceed();
        }

        return definition.operation() == MessagingOperation.PUBLISH
                ? publication.execute(metadata, carrier, joinPoint::proceed)
                : consumption.execute(metadata, carrier, joinPoint::proceed);
    }

    private MessageMetadata buildMetadata(ProceedingJoinPoint joinPoint,
                                          TracedMethodDefinition definition,
                                          MessageCarrier carrier) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();

        MessagingSystem system = definition.system().isAutoDetect() ? carrier.system() : definition.system();

        Destination destination = Optional.ofNullable(valueResolver.resolve(definition.destination(), target, method, args))
                .map(Destination::ofNullable)
                .or(carrier::destination)
                .orElseGet(Destination::unknown);

        String messageId = Optional.ofNullable(valueResolver.resolve(definition.messageId(), target, method, args))
                .or(carrier::messageId)
                .orElse(null);

        MessageMetadata.Builder builder = MessageMetadata.builder(system, definition.operation())
                .destination(destination)
                .messageId(messageId)
                .conversationId(valueResolver.resolve(definition.conversationId(), target, method, args))
                .spanName(valueResolver.resolve(definition.spanName(), target, method, args))
                .attribute("code.function", method.getName())
                .attribute("code.namespace", method.getDeclaringClass().getName());

        for (String attribute : definition.attributes()) {
            int separator = attribute.indexOf('=');
            if (separator <= 0) {
                log.warn("Atributo '{}' ignorado: use o formato chave=valor", attribute);
                continue;
            }
            String key = attribute.substring(0, separator).strip();
            String value = valueResolver.resolve(attribute.substring(separator + 1), target, method, args);
            builder.attribute(key, value);
        }
        return builder.build();
    }
}
