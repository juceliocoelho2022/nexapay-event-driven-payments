package br.com.nexapay.payment.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

@Testcontainers
class KafkaTestcontainersIntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.8.0")
    );

    @Test
    void shouldPublishAndConsumeEventUsingRealKafkaBroker() {
        String topic = "nexapay.integration.payment-created";
        String key = UUID.randomUUID().toString();
        String payload = "{\"paymentId\":\"" + key + "\",\"status\":\"CREATED\"}";

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put("bootstrap.servers", KAFKA.getBootstrapServers());
        producerProps.put("key.serializer", StringSerializer.class);
        producerProps.put("value.serializer", StringSerializer.class);

        ProducerFactory<String, String> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory);

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "nexapay-it-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        var consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        consumer.subscribe(java.util.List.of(topic));

        try {
            kafkaTemplate.send(topic, key, payload).join();
            ConsumerRecord<String, String> record =
                    getSingleRecord(consumer, topic, Duration.ofSeconds(10));

            assertThat(record.key()).isEqualTo(key);
            assertThat(record.value()).isEqualTo(payload);
        } finally {
            consumer.close();
            ((DefaultKafkaProducerFactory<String, String>) producerFactory).destroy();
        }
    }
}
