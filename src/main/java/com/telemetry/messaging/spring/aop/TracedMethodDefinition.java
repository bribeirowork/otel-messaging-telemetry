package com.telemetry.messaging.spring.aop;

import com.telemetry.messaging.domain.model.MessagingOperation;
import com.telemetry.messaging.domain.model.MessagingSystem;
import com.telemetry.messaging.spring.annotation.TracedConsumer;
import com.telemetry.messaging.spring.annotation.TracedProducer;

/**
 * Unified view of {@link TracedProducer} and {@link TracedConsumer}, so the aspect handles both the same way.
 */
record TracedMethodDefinition(
        MessagingOperation operation,
        MessagingSystem system,
        String destination,
        String spanName,
        String messageId,
        String conversationId,
        String[] attributes) {

    static TracedMethodDefinition of(TracedProducer annotation) {
        return new TracedMethodDefinition(MessagingOperation.PUBLISH, annotation.system(), annotation.destination(),
                annotation.spanName(), annotation.messageId(), annotation.conversationId(), annotation.attributes());
    }

    static TracedMethodDefinition of(TracedConsumer annotation) {
        return new TracedMethodDefinition(MessagingOperation.PROCESS, annotation.system(), annotation.destination(),
                annotation.spanName(), annotation.messageId(), annotation.conversationId(), annotation.attributes());
    }
}
