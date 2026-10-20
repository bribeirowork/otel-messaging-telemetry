package com.telemetry.messaging.infrastructure.carrier.rabbitmq;

import com.telemetry.messaging.domain.carrier.MessageCarrier;
import com.telemetry.messaging.domain.carrier.MessageCarrierFactory;
import org.springframework.amqp.core.Message;

public class AmqpMessageCarrierFactory implements MessageCarrierFactory {

    @Override
    public boolean supports(Object candidate) {
        return candidate instanceof Message;
    }

    @Override
    public MessageCarrier create(Object candidate) {
        return new AmqpMessageCarrier((Message) candidate);
    }
}
