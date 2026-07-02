package ru.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.dto.ChangeLogDto;
import ru.dto.LessonAuditEvent;
import ru.mapper.LessonMapper;
import ru.model.ChangeAction;
import ru.model.ChangeLog;
import ru.model.Lesson;
import ru.repository.ChangeLogRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final ChangeLogRepository changeLogRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<LessonAuditEventProducer> auditEventProducer;

    public AuditService(ChangeLogRepository changeLogRepository,
                        ObjectMapper objectMapper,
                        ObjectProvider<LessonAuditEventProducer> auditEventProducer) {
        this.changeLogRepository = changeLogRepository;
        this.objectMapper = objectMapper;
        this.auditEventProducer = auditEventProducer;
    }

    public void logLessonChange(ChangeAction action, Lesson before, Lesson after, String changedBy, String comment) {
        Lesson snapshot = after != null ? after : before;
        LessonAuditEvent event = LessonAuditEvent.builder()
                .entityId(snapshot.getId())
                .action(action)
                .changedBy(changedBy)
                .beforeJson(toJson(before))
                .afterJson(toJson(after))
                .comment(comment)
                .occurredAt(Instant.now())
                .build();

        LessonAuditEventProducer producer = auditEventProducer.getIfAvailable();
        if (producer != null) {
            producer.publish(event);
        } else {
            saveChangeLog(event);
        }
    }

    public void saveChangeLog(LessonAuditEvent event) {
        ChangeLog log = new ChangeLog();
        log.setEntityType("LESSON");
        log.setEntityId(event.getEntityId());
        log.setAction(event.getAction());
        log.setChangedBy(event.getChangedBy());
        log.setBeforeJson(event.getBeforeJson());
        log.setAfterJson(event.getAfterJson());
        log.setComment(event.getComment());
        changeLogRepository.save(log);
    }

    public List<ChangeLogDto> getLessonHistory(UUID lessonId) {
        return changeLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("LESSON", lessonId)
                .stream()
                .map(log -> ChangeLogDto.builder()
                        .id(log.getId())
                        .entityType(log.getEntityType())
                        .entityId(log.getEntityId())
                        .action(log.getAction())
                        .changedBy(log.getChangedBy())
                        .changedAt(log.getCreatedAt())
                        .beforeJson(log.getBeforeJson())
                        .afterJson(log.getAfterJson())
                        .comment(log.getComment())
                        .build())
                .toList();
    }

    private String toJson(Lesson lesson) {
        if (lesson == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(LessonMapper.toDto(lesson));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize lesson audit snapshot", e);
        }
    }
}
