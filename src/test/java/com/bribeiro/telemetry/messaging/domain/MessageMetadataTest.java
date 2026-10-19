package com.bribeiro.telemetry.messaging.domain;

import com.bribeiro.telemetry.messaging.domain.exception.MessagingTelemetryException;
import com.bribeiro.telemetry.messaging.domain.model.Destination;
import com.bribeiro.telemetry.messaging.domain.model.MessageMetadata;
import com.bribeiro.telemetry.messaging.domain.model.MessagingOperation;
import com.bribeiro.telemetry.messaging.domain.model.MessagingSystem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageMetadataTest {

    @Test
    void shouldNameSpanFollowingSemanticConventions() {
        MessageMetadata publish = MessageMetadata.builder(MessagingSystem.KAFKA, MessagingOperation.PUBLISH)
                .destination("orders-created")
                .build();
        MessageMetadata process = MessageMetadata.builder(MessagingSystem.AWS_SQS, MessagingOperation.PROCESS)
                .destination("payments")
                .build();

        assertThat(publish.resolveSpanName()).isEqualTo("send orders-created");
        assertThat(process.resolveSpanName()).isEqualTo("process payments");
    }

    @Test
    void shouldPreferExplicitSpanName() {
        MessageMetadata metadata = MessageMetadata.builder(MessagingSystem.RABBITMQ, MessagingOperation.PUBLISH)
                .destination("x")
                .spanName("publish order")
                .build();

        assertThat(metadata.resolveSpanName()).isEqualTo("publish order");
    }

    @Test
    void shouldUseUnknownDestinationWhenNotInformed() {
        MessageMetadata metadata = MessageMetadata.builder(MessagingSystem.GENERIC, MessagingOperation.PROCESS).build();

        assertThat(metadata.destination().isUnknown()).isTrue();
    }

    @Test
    void shouldRejectUnresolvedAutoSystem() {
        assertThatThrownBy(() -> MessageMetadata.builder(MessagingSystem.AUTO, MessagingOperation.PUBLISH).build())
                .isInstanceOf(MessagingTelemetryException.class);
    }

    @Test
    void shouldRejectNegativePayloadSize() {
        assertThatThrownBy(() -> MessageMetadata.builder(MessagingSystem.KAFKA, MessagingOperation.PUBLISH)
                .payloadSizeBytes(-1L)
                .build())
                .isInstanceOf(MessagingTelemetryException.class);
    }

    @Test
    void shouldRejectBlankDestination() {
        assertThatThrownBy(() -> Destination.of("  ")).isInstanceOf(MessagingTelemetryException.class);
    }
}
