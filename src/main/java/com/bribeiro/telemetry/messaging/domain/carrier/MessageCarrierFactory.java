package com.bribeiro.telemetry.messaging.domain.carrier;

/**
 * Factory that knows how to adapt a concrete message type (ProducerRecord, AMQP Message, PubsubMessage...)
 * into a {@link MessageCarrier}. New broker integrations are added by implementing this interface
 * and registering it as a bean.
 */
public interface MessageCarrierFactory {

    boolean supports(Object candidate);

    MessageCarrier create(Object candidate);

    /** Lower value = higher priority. Lets specific adapters win over generic ones. */
    default int order() {
        return 0;
    }
}
