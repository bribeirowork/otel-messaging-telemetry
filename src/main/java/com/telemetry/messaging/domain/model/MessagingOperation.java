package com.telemetry.messaging.domain.model;

/**
 * Messaging operation type, as defined by {@code messaging.operation.type} in the semantic conventions.
 */
public enum MessagingOperation {

    /** Sending a message to a destination (PRODUCER span). */
    PUBLISH("publish", "send"),
    /** Processing a received message (CONSUMER span). */
    PROCESS("process", "process");

    private final String semanticName;
    private final String spanVerb;

    MessagingOperation(String semanticName, String spanVerb) {
        this.semanticName = semanticName;
        this.spanVerb = spanVerb;
    }

    public String semanticName() {
        return semanticName;
    }

    /** Verb used in the default span name: {@code "<verb> <destination>"}. */
    public String spanVerb() {
        return spanVerb;
    }
}
