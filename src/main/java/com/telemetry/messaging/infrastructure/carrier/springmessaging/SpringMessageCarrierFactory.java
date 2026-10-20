package com.telemetry.messaging.infrastructure.carrier.springmessaging;

import com.telemetry.messaging.domain.carrier.MessageCarrier;
import com.telemetry.messaging.domain.carrier.MessageCarrierFactory;
import org.springframework.messaging.Message;

public class SpringMessageCarrierFactory implements MessageCarrierFactory {

    @Override
    public boolean supports(Object candidate) {
        return candidate instanceof Message<?>;
    }

    @Override
    public MessageCarrier create(Object candidate) {
        return new SpringMessageCarrier((Message<?>) candidate);
    }

    /** After the native adapters (ProducerRecord, AMQP Message...). */
    @Override
    public int order() {
        return 100;
    }
}
