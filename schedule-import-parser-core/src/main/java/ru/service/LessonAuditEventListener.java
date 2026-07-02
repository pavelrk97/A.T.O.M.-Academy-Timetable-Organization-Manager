package ru.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.dto.LessonAuditEvent;

@Component
@ConditionalOnProperty(name = "atom.kafka.enabled", havingValue = "true")
public class LessonAuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(LessonAuditEventListener.class);

    private final AuditService auditService;

    public LessonAuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @KafkaListener(topics = "${atom.kafka.lesson-audit-topic}")
    public void onLessonAuditEvent(LessonAuditEvent event) {
        auditService.saveChangeLog(event);
        log.info("Lesson audit event consumed: entityId={}, action={}, changedBy={}, occurredAt={}",
                event.getEntityId(), event.getAction(), event.getChangedBy(), event.getOccurredAt());
    }
}
