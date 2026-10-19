package com.bribeiro.telemetry.messaging.infrastructure.carrier.sqs;

import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrier;
import com.bribeiro.telemetry.messaging.domain.carrier.MessageCarrierFactory;
import software.amazon.awssdk.services.sqs.model.Message;

public class SqsMessageCarrierFactory implements MessageCarrierFactory {

    @Override
    public boolean supports(Object candidate) {
        return candidate instanceof Message;
    }

    @Override
    public MessageCarrier create(Object candidate) {
        return new SqsMessageCarrier((Message) candidate);
    }
}
