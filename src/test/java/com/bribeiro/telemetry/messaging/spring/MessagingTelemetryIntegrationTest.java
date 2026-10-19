package com.bribeiro.telemetry.messaging.spring;

import com.bribeiro.telemetry.messaging.application.MessagingTelemetry;
import com.bribeiro.telemetry.messaging.infrastructure.opentelemetry.MessagingAttributes;
import com.bribeiro.telemetry.messaging.spring.annotation.EnableMessagingTelemetry;
import com.bribeiro.telemetry.messaging.spring.annotation.TracedConsumer;
import com.bribeiro.telemetry.messaging.spring.annotation.TracedProducer;
import com.bribeiro.telemetry.messaging.spring.config.MessagingTelemetryConfigurer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = MessagingTelemetryIntegrationTest.TestApplication.class,
        properties = {
                "spring.application.name=orders-api",
                "messaging.telemetry.exporter.backend=NONE",
                "app.topics.orders=orders-created"
        })
class MessagingTelemetryIntegrationTest {

    static final InMemorySpanExporter SPANS = InMemorySpanExporter.create();

    @Autowired
    OrderProducer producer;

    @Autowired
    OrderConsumer consumer;

    @Autowired
    MessagingTelemetry telemetry;

    @BeforeEach
    void reset() {
        SPANS.reset();
    }

    @Test
    void shouldLinkKafkaProducerAndConsumerInSameTrace() {
        ProducerRecord<String, String> sent = new ProducerRecord<>("orders-created", "42", "{\"id\":42}");
        producer.publish(sent, new Order("42", "BR"));

        ConsumerRecord<String, String> received = new ConsumerRecord<>("orders-created", 0, 10L, "42", "{\"id\":42}");
        for (Header header : sent.headers()) {
            received.headers().add(header);
        }
        consumer.process(received);

        List<SpanData> spans = SPANS.getFinishedSpanItems();
        SpanData producerSpan = spans.get(0);
        SpanData consumerSpan = spans.get(1);

        assertThat(producerSpan.getKind()).isEqualTo(SpanKind.PRODUCER);
        assertThat(producerSpan.getName()).isEqualTo("send orders-created");
        assertThat(producerSpan.getAttributes().get(MessagingAttributes.SYSTEM)).isEqualTo("kafka");
        assertThat(producerSpan.getAttributes().get(MessagingAttributes.MESSAGE_ID)).isEqualTo("42");
        assertThat(producerSpan.getAttributes().get(AttributeKey.stringKey("order.country"))).isEqualTo("BR");
        assertThat(producerSpan.getResource().getAttribute(AttributeKey.stringKey("service.name"))).isEqualTo("orders-api");
        assertThat(producerSpan.getResource().getAttribute(AttributeKey.stringKey("team"))).isEqualTo("checkout");

        assertThat(consumerSpan.getKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(consumerSpan.getTraceId()).isEqualTo(producerSpan.getTraceId());
        assertThat(consumerSpan.getParentSpanId()).isEqualTo(producerSpan.getSpanId());
        assertThat(consumerSpan.getAttributes().get(MessagingAttributes.MESSAGE_ID)).isEqualTo("orders-created-0@10");
    }

    @Test
    void shouldPropagateThroughRabbitMessageAndResolvePlaceholders() {
        Message message = new Message("{}".getBytes(), new MessageProperties());
        producer.publishRabbit(message);

        assertThat(message.getMessageProperties().getHeaders()).containsKey("traceparent");
        SpanData span = SPANS.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(MessagingAttributes.SYSTEM)).isEqualTo("rabbitmq");
        assertThat(span.getAttributes().get(MessagingAttributes.DESTINATION_NAME)).isEqualTo("orders-created");
    }

    @Test
    void shouldExposeHeadersForImmutableMessagesLikeSqsAndPubSub() {
        Map<String, String> headers = producer.publishSqs("orders.fifo");

        assertThat(headers).containsKey("traceparent");
        assertThat(headers.get("traceparent")).contains(SPANS.getFinishedSpanItems().get(0).getTraceId());
    }

    @Test
    void shouldRecordConsumerFailure() {
        Map<String, Object> headers = new HashMap<>();
        assertThatThrownBy(() -> consumer.fail(headers)).isInstanceOf(IllegalArgumentException.class);

        SpanData span = SPANS.getFinishedSpanItems().get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
        assertThat(span.getAttributes().get(MessagingAttributes.SYSTEM)).isEqualTo("generic");
    }

    @Test
    void shouldNotActivateWithoutEnableAnnotation() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AopAutoConfiguration.class,
                        com.bribeiro.telemetry.messaging.spring.config.MessagingTelemetryAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(MessagingTelemetry.class));
    }

    @Test
    void shouldBeDisabledByProperty() {
        new ApplicationContextRunner()
                .withUserConfiguration(EnabledConfig.class)
                .withConfiguration(AutoConfigurations.of(
                        AopAutoConfiguration.class,
                        com.bribeiro.telemetry.messaging.spring.config.MessagingTelemetryAutoConfiguration.class))
                .withPropertyValues("messaging.telemetry.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MessagingTelemetry.class));
    }

    @Configuration
    @EnableMessagingTelemetry
    static class EnabledConfig {
    }

    record Order(String id, String country) {
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableMessagingTelemetry
    static class TestApplication implements MessagingTelemetryConfigurer {

        @Override
        public void configureResource(ResourceBuilder resource) {
            resource.put("team", "checkout");
        }

        @Override
        public void configureTracerProvider(SdkTracerProviderBuilder tracerProvider) {
            tracerProvider.addSpanProcessor(SimpleSpanProcessor.create(SPANS));
        }

        @Bean
        OrderProducer orderProducer(MessagingTelemetry telemetry) {
            return new OrderProducer(telemetry);
        }

        @Bean
        OrderConsumer orderConsumer() {
            return new OrderConsumer();
        }
    }

    static class OrderProducer {

        private final MessagingTelemetry telemetry;

        OrderProducer(MessagingTelemetry telemetry) {
            this.telemetry = telemetry;
        }

        @TracedProducer(messageId = "#order.id", attributes = "order.country=#order.country")
        public void publish(ProducerRecord<String, String> record, Order order) {
            // kafkaTemplate.send(record)
        }

        @TracedProducer(destination = "${app.topics.orders}")
        public void publishRabbit(Message message) {
            // rabbitTemplate.send(exchange, routingKey, message)
        }

        @TracedProducer(destination = "#queue", system = com.bribeiro.telemetry.messaging.domain.model.MessagingSystem.AWS_SQS)
        public Map<String, String> publishSqs(String queue) {
            // sqsClient.sendMessage(... messageAttributes(SqsTraceAttributes.from(headers)))
            return telemetry.currentPropagationHeaders();
        }
    }

    static class OrderConsumer {

        @TracedConsumer
        public void process(ConsumerRecord<String, String> record) {
        }

        @TracedConsumer(destination = "dlq")
        public void fail(Map<String, Object> headers) {
            throw new IllegalArgumentException("invalid payload");
        }
    }
}
