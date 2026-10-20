package com.telemetry.messaging.spring.config;

import com.telemetry.messaging.infrastructure.carrier.generic.MapMessageCarrierFactory;
import com.telemetry.messaging.infrastructure.carrier.kafka.KafkaMessageCarrierFactory;
import com.telemetry.messaging.infrastructure.carrier.pubsub.PubSubMessageCarrierFactory;
import com.telemetry.messaging.infrastructure.carrier.rabbitmq.AmqpMessageCarrierFactory;
import com.telemetry.messaging.infrastructure.carrier.springmessaging.SpringMessageCarrierFactory;
import com.telemetry.messaging.infrastructure.carrier.sqs.SqsMessageCarrierFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the broker adapters only when the matching client is on the application classpath.
 * This way the library does not force anyone to depend on Kafka, the AWS SDK or Google Cloud.
 */
@Configuration(proxyBeanMethods = false)
class MessageCarrierFactoriesConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MapMessageCarrierFactory mapMessageCarrierFactory() {
        return new MapMessageCarrierFactory();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.apache.kafka.clients.producer.ProducerRecord")
    static class KafkaCarrierConfiguration {

        @Bean
        @ConditionalOnMissingBean
        KafkaMessageCarrierFactory kafkaMessageCarrierFactory() {
            return new KafkaMessageCarrierFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.amqp.core.Message")
    static class RabbitCarrierConfiguration {

        @Bean
        @ConditionalOnMissingBean
        AmqpMessageCarrierFactory amqpMessageCarrierFactory() {
            return new AmqpMessageCarrierFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "software.amazon.awssdk.services.sqs.model.Message")
    static class SqsCarrierConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SqsMessageCarrierFactory sqsMessageCarrierFactory() {
            return new SqsMessageCarrierFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.google.pubsub.v1.PubsubMessage")
    static class PubSubCarrierConfiguration {

        @Bean
        @ConditionalOnMissingBean
        PubSubMessageCarrierFactory pubSubMessageCarrierFactory() {
            return new PubSubMessageCarrierFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.messaging.Message")
    static class SpringMessagingCarrierConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SpringMessageCarrierFactory springMessageCarrierFactory() {
            return new SpringMessageCarrierFactory();
        }
    }
}
