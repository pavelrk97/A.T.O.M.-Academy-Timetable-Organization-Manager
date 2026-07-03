package ru.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.dto.LessonAuditEvent;
import ru.model.ChangeAction;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonAuditEventProducerTest {

    private static final String TOPIC = "atom.lesson-audit";

    @Mock
    private KafkaTemplate<String, LessonAuditEvent> kafkaTemplate;

    @AfterEach
    void cleanUpTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publish_sendsImmediatelyWithoutActiveTransaction() {
        when(kafkaTemplate.send(anyString(), anyString(), any(LessonAuditEvent.class)))
                .thenReturn(new CompletableFuture<>());
        LessonAuditEventProducer producer = new LessonAuditEventProducer(kafkaTemplate, TOPIC);
        LessonAuditEvent event = event();

        producer.publish(event);

        verify(kafkaTemplate).send(TOPIC, event.getEntityId().toString(), event);
    }

    @Test
    void publish_waitsForCommitInsideActiveTransaction() {
        LessonAuditEventProducer producer = new LessonAuditEventProducer(kafkaTemplate, TOPIC);
        LessonAuditEvent event = event();

        TransactionSynchronizationManager.initSynchronization();
        producer.publish(event);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(LessonAuditEvent.class));

        when(kafkaTemplate.send(anyString(), anyString(), any(LessonAuditEvent.class)))
                .thenReturn(new CompletableFuture<>());
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(kafkaTemplate).send(TOPIC, event.getEntityId().toString(), event);
    }

    @Test
    void publish_doesNotSendWhenTransactionNeverCommits() {
        LessonAuditEventProducer producer = new LessonAuditEventProducer(kafkaTemplate, TOPIC);

        TransactionSynchronizationManager.initSynchronization();
        producer.publish(event());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(LessonAuditEvent.class));
    }

    private LessonAuditEvent event() {
        return LessonAuditEvent.builder()
                .entityId(UUID.randomUUID())
                .action(ChangeAction.UPDATED)
                .changedBy("editor")
                .beforeJson("{\"title\":\"before\"}")
                .afterJson("{\"title\":\"after\"}")
                .comment("Lesson updated")
                .occurredAt(Instant.now())
                .build();
    }
}
