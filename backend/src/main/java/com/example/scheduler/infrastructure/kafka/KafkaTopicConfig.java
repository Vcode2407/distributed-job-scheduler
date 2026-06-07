package com.example.scheduler.infrastructure.kafka;

import com.example.scheduler.infrastructure.config.AppProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic jobEventsTopic(AppProperties properties) {
        return TopicBuilder.name(properties.kafka().jobEventsTopic())
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic jobEventsRetryTopic(AppProperties properties) {
        return TopicBuilder.name(properties.kafka().jobEventsRetryTopic())
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic jobEventsDlqTopic(AppProperties properties) {
        return TopicBuilder.name(properties.kafka().jobEventsDlqTopic())
                .partitions(6)
                .replicas(1)
                .build();
    }
}
