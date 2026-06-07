package com.example.scheduler.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class JobEventsConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobEventsConsumer.class);

    @KafkaListener(topics = "${app.kafka.job-events-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload, Acknowledgment acknowledgment) {
        LOGGER.debug("Consumed job event: {}", payload);
        acknowledgment.acknowledge();
    }
}
