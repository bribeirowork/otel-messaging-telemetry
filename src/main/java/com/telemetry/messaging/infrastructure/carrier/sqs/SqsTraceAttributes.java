package com.telemetry.messaging.infrastructure.carrier.sqs;

import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts the propagation headers into {@code MessageAttributeValue} for sending through AWS SDK v2.
 *
 * <pre>{@code
 * SendMessageRequest.builder()
 *     .queueUrl(url)
 *     .messageBody(body)
 *     .messageAttributes(SqsTraceAttributes.from(telemetry.currentPropagationHeaders()))
 *     .build();
 * }</pre>
 * Note: SQS accepts at most 10 message attributes per message.
 */
public final class SqsTraceAttributes {

    private SqsTraceAttributes() {
    }

    public static Map<String, MessageAttributeValue> from(Map<String, String> propagationHeaders) {
        Map<String, MessageAttributeValue> attributes = new LinkedHashMap<>();
        propagationHeaders.forEach((key, value) -> attributes.put(key,
                MessageAttributeValue.builder().dataType("String").stringValue(value).build()));
        return attributes;
    }

    /** Merges the propagation headers with the message's existing attributes. */
    public static Map<String, MessageAttributeValue> merge(Map<String, MessageAttributeValue> existing,
                                                           Map<String, String> propagationHeaders) {
        Map<String, MessageAttributeValue> attributes = new LinkedHashMap<>(existing);
        attributes.putAll(from(propagationHeaders));
        return attributes;
    }
}
