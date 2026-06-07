package com.example.scheduler.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class KafkaEventBusTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @Test
    void publishesAndConsumesJobRetryAndDeadLetterEvents() throws Exception {
        String topic = "job-events-test-" + UUID.randomUUID();
        String retryTopic = topic + ".retry";
        String dlqTopic = topic + ".dlq";
        String groupId = "test-" + UUID.randomUUID();
        createTopics(topic, retryTopic, dlqTopic);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()
        ));
             KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                     ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                     ConsumerConfig.GROUP_ID_CONFIG, groupId,
                     ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                     ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                     ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
             ))) {
            consumer.subscribe(List.of(topic, retryTopic, dlqTopic));
            producer.send(new ProducerRecord<>(topic, "job-1", "{\"eventType\":\"JobSubmitted\"}")).get();
            producer.send(new ProducerRecord<>(retryTopic, "job-1", "{\"eventType\":\"JobRetrying\"}")).get();
            producer.send(new ProducerRecord<>(dlqTopic, "job-1", "{\"eventType\":\"JobDeadLettered\"}")).get();
            producer.flush();

            var records = consumer.poll(Duration.ofSeconds(10));

            assertThat(records.records(topic)).anyMatch(record -> record.value().contains("JobSubmitted"));
            assertThat(records.records(retryTopic)).anyMatch(record -> record.value().contains("JobRetrying"));
            assertThat(records.records(dlqTopic)).anyMatch(record -> record.value().contains("JobDeadLettered"));
        }
    }

    private void createTopics(String... topics) throws Exception {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()
        ))) {
            adminClient.createTopics(List.of(topics).stream()
                    .map(topic -> new NewTopic(topic, 1, (short) 1))
                    .toList()).all().get(30, TimeUnit.SECONDS);
            assertThat(adminClient.listTopics().names().get(30, TimeUnit.SECONDS)).contains(topics);
        }
    }
}
