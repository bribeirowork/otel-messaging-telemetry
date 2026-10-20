package com.telemetry.messaging.domain.carrier;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Domain service that picks, among the registered factories, the one that adapts a message object.
 */
public final class MessageCarrierResolver {

    private final List<MessageCarrierFactory> factories;

    public MessageCarrierResolver(List<MessageCarrierFactory> factories) {
        this.factories = factories.stream()
                .sorted(Comparator.comparingInt(MessageCarrierFactory::order))
                .toList();
    }

    public Optional<MessageCarrier> resolve(Object candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        return factories.stream()
                .filter(factory -> factory.supports(candidate))
                .findFirst()
                .map(factory -> factory.create(candidate));
    }

    /** Searches several objects (e.g. method arguments) for the first one that can be adapted. */
    public Optional<MessageCarrier> resolveFirst(Object... candidates) {
        if (candidates == null) {
            return Optional.empty();
        }
        for (Object candidate : candidates) {
            Optional<MessageCarrier> carrier = resolve(candidate);
            if (carrier.isPresent()) {
                return carrier;
            }
        }
        return Optional.empty();
    }
}
