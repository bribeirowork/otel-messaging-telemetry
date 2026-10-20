package com.telemetry.messaging.spring.annotation;

import com.telemetry.messaging.domain.model.MessagingSystem;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that processes a received message. Extracts the trace context from the headers of the first
 * recognized argument (ConsumerRecord, Spring Message, AMQP Message, SQS Message, PubsubMessage, Map...)
 * and creates a CONSUMER span as a child of the producer span, linking both services in the same trace.
 * <p>
 * Text attributes accept placeholders ({@code ${app.queues.payments}}) and SpEL expressions starting with {@code #}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TracedConsumer {

    /** Topic, queue or subscription. When empty, it is inferred from the argument. */
    String destination() default "";

    MessagingSystem system() default MessagingSystem.AUTO;

    /** Span name. Default: {@code "process <destination>"}. */
    String spanName() default "";

    String messageId() default "";

    String conversationId() default "";

    /** Extra attributes in the {@code "key=value"} format; the value accepts SpEL. */
    String[] attributes() default {};
}
