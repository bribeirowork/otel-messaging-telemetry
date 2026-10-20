package com.telemetry.messaging.infrastructure.carrier.rabbitmq;

import com.telemetry.messaging.domain.carrier.MessageCarrier;
import com.telemetry.messaging.domain.model.Destination;
import com.telemetry.messaging.domain.model.MessagingSystem;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Carrier over the Spring AMQP {@link Message} (RabbitMQ). Headers live in {@link MessageProperties}.
 */
public class AmqpMessageCarrier implements MessageCarrier {

    private final MessageProperties properties;

    public AmqpMessageCarrier(Message message) {
        this.properties = message.getMessageProperties();
    }

    @Override
    public Iterable<String> keys() {
        return properties.getHeaders().keySet();
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(properties.getHeader(key)).map(String::valueOf);
    }

    @Override
    public void put(String key, String value) {
        properties.setHeader(key, value);
    }

    @Override
    public MessagingSystem system() {
        return MessagingSystem.RABBITMQ;
    }

    @Override
    public Optional<Destination> destination() {
        return Stream.of(properties.getConsumerQueue(), properties.getReceivedExchange(), properties.getReceivedRoutingKey())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(Destination::of);
    }

    @Override
    public Optional<String> messageId() {
        return Optional.ofNullable(properties.getMessageId());
    }
}
