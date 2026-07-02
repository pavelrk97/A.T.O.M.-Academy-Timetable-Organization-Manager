package ru.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "atom.kafka.enabled", havingValue = "true")
public class KafkaAuditConfig {

    @Bean
    public NewTopic lessonAuditTopic(@Value("${atom.kafka.lesson-audit-topic}") String topic) {
        return TopicBuilder.name(topic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
