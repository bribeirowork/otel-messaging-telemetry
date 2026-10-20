package com.telemetry.messaging.infrastructure.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Attribute keys from the OpenTelemetry messaging semantic conventions.
 * Declared here to avoid depending on the {@code semconv} artifact, which is still unstable.
 */
public final class MessagingAttributes {

    public static final AttributeKey<String> SYSTEM = AttributeKey.stringKey("messaging.system");
    public static final AttributeKey<String> OPERATION_TYPE = AttributeKey.stringKey("messaging.operation.type");
    public static final AttributeKey<String> OPERATION_NAME = AttributeKey.stringKey("messaging.operation.name");
    public static final AttributeKey<String> DESTINATION_NAME = AttributeKey.stringKey("messaging.destination.name");
    public static final AttributeKey<String> MESSAGE_ID = AttributeKey.stringKey("messaging.message.id");
    public static final AttributeKey<String> CONVERSATION_ID = AttributeKey.stringKey("messaging.message.conversation_id");
    public static final AttributeKey<Long> BODY_SIZE = AttributeKey.longKey("messaging.message.body.size");
    public static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

    private MessagingAttributes() {
    }
}
