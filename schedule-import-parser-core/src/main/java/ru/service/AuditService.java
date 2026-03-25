package ru.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.dto.ChangeLogDto;
import ru.mapper.LessonMapper;
import ru.model.ChangeAction;
import ru.model.ChangeLog;
import ru.model.Lesson;
import ru.repository.ChangeLogRepository;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final ChangeLogRepository changeLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(ChangeLogRepository changeLogRepository, ObjectMapper objectMapper) {
        this.changeLogRepository = changeLogRepository;
        this.objectMapper = objectMapper;
    }

    public void logLessonChange(ChangeAction action, Lesson before, Lesson after, String changedBy, String comment) {
        ChangeLog log = new ChangeLog();
        Lesson snapshot = after != null ? after : before;
        log.setEntityType("LESSON");
        log.setEntityId(snapshot.getId());
        log.setAction(action);
        log.setChangedBy(changedBy);
        log.setBeforeJson(toJson(before));
        log.setAfterJson(toJson(after));
        log.setComment(comment);
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
