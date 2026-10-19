package com.bribeiro.telemetry.messaging.infrastructure.carrier.springmessaging;

import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrier;
import com.bribeiro.telemetry.messaging.domain.model.Destination;
import com.bribeiro.telemetry.messaging.domain.model.MessagingSystem;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Read-only carrier over the spring-messaging {@link Message}. It is the type delivered to listeners of
 * Spring Kafka, Spring AMQP, Spring Cloud AWS (SQS) and Spring Cloud GCP (Pub/Sub), so it covers the consumer side
 * of every broker. The system and destination are inferred from the headers each Spring integration adds.
 */
public class SpringMessageCarrier implements MessageCarrier {

    static final String KAFKA_TOPIC = "kafka_receivedTopic";
    static final String KAFKA_OFFSET = "kafka_offset";
    static final String AMQP_QUEUE = "amqp_consumerQueue";
    static final String AMQP_MESSAGE_ID = "amqp_messageId";
    static final String SQS_QUEUE = "Sqs_QueueName";
    static final String SQS_MESSAGE_ID = "Sqs_MessageId";
    static final String PUBSUB_PREFIX = "gcp_pubsub_";

    private final MessageHeaders headers;

    public SpringMessageCarrier(Message<?> message) {
        this.headers = message.getHeaders();
    }

    @Override
    public Iterable<String> keys() {
        return headers.keySet();
    }

    @Override
    public Optional<String> get(String key) {
        Object value = headers.get(key);
        if (value instanceof byte[] bytes) {
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        }
        return Optional.ofNullable(value).map(String::valueOf);
    }

    @Override
    public void put(String key, String value) {
        // MessageHeaders is immutable
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public MessagingSystem system() {
        if (headers.containsKey(KAFKA_TOPIC)) {
            return MessagingSystem.KAFKA;
        }
        if (headers.containsKey(AMQP_QUEUE)) {
            return MessagingSystem.RABBITMQ;
        }
        if (headers.containsKey(SQS_QUEUE) || headers.containsKey(SQS_MESSAGE_ID)) {
            return MessagingSystem.AWS_SQS;
        }
        if (headers.keySet().stream().anyMatch(key -> key.startsWith(PUBSUB_PREFIX))) {
            return MessagingSystem.GCP_PUBSUB;
        }
        return MessagingSystem.GENERIC;
    }

    @Override
    public Optional<Destination> destination() {
        return get(KAFKA_TOPIC).or(() -> get(AMQP_QUEUE)).or(() -> get(SQS_QUEUE)).map(Destination::of);
    }

    @Override
    public Optional<String> messageId() {
        return get(AMQP_MESSAGE_ID)
                .or(() -> get(SQS_MESSAGE_ID))
                .or(() -> get(KAFKA_OFFSET).map(offset -> get(KAFKA_TOPIC).orElse("") + "@" + offset))
                .or(() -> Optional.ofNullable(headers.getId()).map(Object::toString));
    }
}
