package com.bribeiro.telemetry.messaging.infrastructure.carrier.sqs;

import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrier;
import com.bribeiro.telemetry.messaging.domain.model.MessagingSystem;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.Map;
import java.util.Optional;

/**
 * Read-only carrier over the {@link Message} received through AWS SDK v2 ({@code ReceiveMessageResponse}).
 * The context comes from {@code messageAttributes}; remember to request {@code messageAttributeNames("All")} on receive.
 */
public class SqsMessageCarrier implements MessageCarrier {

    private final Message message;
    private final Map<String, MessageAttributeValue> attributes;

    public SqsMessageCarrier(Message message) {
        this.message = message;
        this.attributes = message.messageAttributes();
    }

    @Override
    public Iterable<String> keys() {
        return attributes.keySet();
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(attributes.get(key)).map(MessageAttributeValue::stringValue);
    }

    @Override
    public void put(String key, String value) {
        // AWS SDK v2 objects are immutable; use SqsTraceAttributes when sending
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public MessagingSystem system() {
        return MessagingSystem.AWS_SQS;
    }

    @Override
    public Optional<String> messageId() {
        return Optional.ofNullable(message.messageId());
    }
}
