package ru.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.dto.LessonAuditEvent;

@Service
@ConditionalOnProperty(name = "atom.kafka.enabled", havingValue = "true")
public class LessonAuditEventProducer {

    private static final Logger log = LoggerFactory.getLogger(LessonAuditEventProducer.class);

    private final KafkaTemplate<String, LessonAuditEvent> kafkaTemplate;
    private final String topic;

    public LessonAuditEventProducer(KafkaTemplate<String, LessonAuditEvent> kafkaTemplate,
                                    @Value("${atom.kafka.lesson-audit-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(LessonAuditEvent event) {
        // Публикуем только после commit'а: иначе при откате транзакции в топик
        // улетит аудит изменения, которого в БД не произошло.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(LessonAuditEvent event) {
        kafkaTemplate.send(topic, event.getEntityId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Lesson audit event publish failed: entityId={}, action={}",
                                event.getEntityId(), event.getAction(), ex);
                    } else {
                        log.info("Lesson audit event published: entityId={}, action={}, partition={}, offset={}",
                                event.getEntityId(), event.getAction(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
