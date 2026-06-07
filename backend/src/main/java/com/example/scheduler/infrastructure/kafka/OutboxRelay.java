package com.example.scheduler.infrastructure.kafka;

import com.example.scheduler.application.port.out.OutboxRepositoryPort;
import com.example.scheduler.infrastructure.config.AppProperties;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepositoryPort outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AppProperties properties;
    private final Clock clock;

    public OutboxRelay(OutboxRepositoryPort outbox, KafkaTemplate<String, String> kafkaTemplate, AppProperties properties, Clock clock) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${APP_OUTBOX_RELAY_DELAY_MS:1000}")
    public void publishPendingEvents() {
        for (OutboxRepositoryPort.OutboxMessage message : outbox.findPending(properties.scheduler().outboxBatchSize())) {
            try {
                kafkaTemplate.send(properties.kafka().jobEventsTopic(), message.aggregateId().toString(), message.payload())
                        .get(10, TimeUnit.SECONDS);
                outbox.markPublished(message.id(), clock.instant());
            } catch (Exception exception) {
                LOGGER.warn("Failed to publish outbox event {}", message.id(), exception);
                outbox.markFailed(message.id(), exception.getMessage());
            }
        }
    }
}
