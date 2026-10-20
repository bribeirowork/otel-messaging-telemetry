package com.telemetry.messaging.domain.model;

/**
 * Supported messaging systems. {@link #semanticName()} follows the OpenTelemetry semantic convention
 * for the {@code messaging.system} attribute.
 */
public enum MessagingSystem {

    KAFKA("kafka"),
    AWS_SQS("aws_sqs"),
    RABBITMQ("rabbitmq"),
    GCP_PUBSUB("gcp_pubsub"),
    GENERIC("generic"),
    /** Indicates that the system must be detected from the type of the annotated argument. */
    AUTO("auto");

    private final String semanticName;

    MessagingSystem(String semanticName) {
        this.semanticName = semanticName;
    }

    public String semanticName() {
        return semanticName;
    }

    public boolean isAutoDetect() {
        return this == AUTO;
    }
}
