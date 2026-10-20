package com.telemetry.messaging.spring.annotation;

import com.telemetry.messaging.domain.model.MessagingSystem;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that publishes a message. Creates a PRODUCER span and injects the trace context
 * ({@code traceparent}) into the headers of the first argument that is a recognized message
 * (ProducerRecord, AMQP Message, header Map...).
 * <p>
 * Text attributes accept placeholders ({@code ${app.topics.orders}}) and SpEL expressions starting with {@code #}
 * over the method parameters ({@code #order.id}).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TracedProducer {

    /** Topic, queue or exchange. When empty, it is inferred from the argument (e.g. {@code ProducerRecord#topic()}). */
    String destination() default "";

    /** Messaging system. Detected from the argument type by default. */
    MessagingSystem system() default MessagingSystem.AUTO;

    /** Span name. Default: {@code "send <destination>"}. */
    String spanName() default "";

    /** Message id (SpEL), e.g. {@code "#event.id"}. */
    String messageId() default "";

    /** Correlation/conversation id (SpEL), e.g. {@code "#order.number"}. */
    String conversationId() default "";

    /** Extra attributes in the {@code "key=value"} format; the value accepts SpEL. */
    String[] attributes() default {};
}
