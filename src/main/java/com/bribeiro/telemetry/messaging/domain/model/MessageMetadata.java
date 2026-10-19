package com.bribeiro.telemetry.messaging.domain.model;

import com.bribeiro.telemetry.messaging.domain.exception.MessagingTelemetryException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Value object with everything that describes a traced message, regardless of the broker in use.
 * The span is named from it and receives the messaging semantic attributes.
 */
public final class MessageMetadata {

    private static final int MAX_CUSTOM_ATTRIBUTES = 64;

    private final MessagingSystem system;
    private final MessagingOperation operation;
    private final Destination destination;
    private final String messageId;
    private final String conversationId;
    private final Long payloadSizeBytes;
    private final String spanName;
    private final Map<String, String> customAttributes;

    private MessageMetadata(Builder builder) {
        this.system = Objects.requireNonNull(builder.system, "system é obrigatório");
        this.operation = Objects.requireNonNull(builder.operation, "operation é obrigatório");
        this.destination = builder.destination == null ? Destination.unknown() : builder.destination;
        this.messageId = blankToNull(builder.messageId);
        this.conversationId = blankToNull(builder.conversationId);
        this.payloadSizeBytes = builder.payloadSizeBytes;
        this.spanName = blankToNull(builder.spanName);
        this.customAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.customAttributes));
        validate();
    }

    private void validate() {
        if (system.isAutoDetect()) {
            throw new MessagingTelemetryException(
                    "MessagingSystem.AUTO precisa ser resolvido para um sistema concreto antes de criar o metadata");
        }
        if (payloadSizeBytes != null && payloadSizeBytes < 0) {
            throw new MessagingTelemetryException("O tamanho do payload não pode ser negativo");
        }
        if (customAttributes.size() > MAX_CUSTOM_ATTRIBUTES) {
            throw new MessagingTelemetryException(
                    "Limite de " + MAX_CUSTOM_ATTRIBUTES + " atributos customizados por mensagem excedido");
        }
    }

    public static Builder builder(MessagingSystem system, MessagingOperation operation) {
        return new Builder(system, operation);
    }

    /**
     * Span name. When not set explicitly, it follows the {@code "<operation> <destination>"} convention,
     * for example {@code "send orders-created"} or {@code "process orders-created"}.
     */
    public String resolveSpanName() {
        return spanName != null ? spanName : operation.spanVerb() + " " + destination.name();
    }

    public MessagingSystem system() {
        return system;
    }

    public MessagingOperation operation() {
        return operation;
    }

    public Destination destination() {
        return destination;
    }

    public Optional<String> messageId() {
        return Optional.ofNullable(messageId);
    }

    public Optional<String> conversationId() {
        return Optional.ofNullable(conversationId);
    }

    public Optional<Long> payloadSizeBytes() {
        return Optional.ofNullable(payloadSizeBytes);
    }

    public Map<String, String> customAttributes() {
        return customAttributes;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static final class Builder {

        private final MessagingSystem system;
        private final MessagingOperation operation;
        private Destination destination;
        private String messageId;
        private String conversationId;
        private Long payloadSizeBytes;
        private String spanName;
        private final Map<String, String> customAttributes = new LinkedHashMap<>();

        private Builder(MessagingSystem system, MessagingOperation operation) {
            this.system = system;
            this.operation = operation;
        }

        public Builder destination(Destination destination) {
            this.destination = destination;
            return this;
        }

        public Builder destination(String destination) {
            this.destination = Destination.ofNullable(destination);
            return this;
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder payloadSizeBytes(Long payloadSizeBytes) {
            this.payloadSizeBytes = payloadSizeBytes;
            return this;
        }

        public Builder spanName(String spanName) {
            this.spanName = spanName;
            return this;
        }

        public Builder attribute(String key, String value) {
            if (key != null && !key.isBlank() && value != null) {
                this.customAttributes.put(key, value);
            }
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            if (attributes != null) {
                attributes.forEach(this::attribute);
            }
            return this;
        }

        public MessageMetadata build() {
            return new MessageMetadata(this);
        }
    }
}
