package com.telemetry.messaging.infrastructure.carrier.pubsub;

import com.telemetry.messaging.domain.carrier.MessageCarrier;
import com.telemetry.messaging.domain.carrier.MessageCarrierFactory;
import com.google.pubsub.v1.PubsubMessage;

public class PubSubMessageCarrierFactory implements MessageCarrierFactory {

    @Override
    public boolean supports(Object candidate) {
        return candidate instanceof PubsubMessage;
    }

    @Override
    public MessageCarrier create(Object candidate) {
        return new PubSubMessageCarrier((PubsubMessage) candidate);
    }
}
