package ru.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import ru.dto.ChangeLogDto;
import ru.dto.LessonAuditEvent;
import ru.model.ChangeAction;
import ru.model.ChangeLog;
import ru.model.Lesson;
import ru.model.LessonType;
import ru.repository.ChangeLogRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private ChangeLogRepository changeLogRepository;

    @Mock
    private ObjectProvider<LessonAuditEventProducer> auditEventProducer;

    @Mock
    private LessonAuditEventProducer producer;

    @Test
    void logLessonChange_savesSerializedSnapshots() {
        AuditService service = new AuditService(changeLogRepository, new ObjectMapper(), auditEventProducer);
        Lesson before = lesson("Signals before");
        Lesson after = lesson("Signals after");

        service.logLessonChange(ChangeAction.UPDATED, before, after, "editor", "Lesson updated");

        ArgumentCaptor<ChangeLog> captor = ArgumentCaptor.forClass(ChangeLog.class);
        verify(changeLogRepository).save(captor.capture());

        ChangeLog saved = captor.getValue();
        assertThat(saved.getEntityType()).isEqualTo("LESSON");
        assertThat(saved.getEntityId()).isEqualTo(after.getId());
        assertThat(saved.getAction()).isEqualTo(ChangeAction.UPDATED);
        assertThat(saved.getChangedBy()).isEqualTo("editor");
        assertThat(saved.getComment()).isEqualTo("Lesson updated");
        assertThat(saved.getBeforeJson()).contains("Signals before");
        assertThat(saved.getAfterJson()).contains("Signals after");
    }

    @Test
    void logLessonChange_publishesEventWhenKafkaEnabled() {
        when(auditEventProducer.getIfAvailable()).thenReturn(producer);
        AuditService service = new AuditService(changeLogRepository, new ObjectMapper(), auditEventProducer);
        Lesson before = lesson("Signals before");
        Lesson after = lesson("Signals after");

        service.logLessonChange(ChangeAction.UPDATED, before, after, "editor", "Lesson updated");

        ArgumentCaptor<LessonAuditEvent> captor = ArgumentCaptor.forClass(LessonAuditEvent.class);
        verify(producer).publish(captor.capture());
        verifyNoInteractions(changeLogRepository);

        LessonAuditEvent event = captor.getValue();
        assertThat(event.getEntityId()).isEqualTo(after.getId());
        assertThat(event.getAction()).isEqualTo(ChangeAction.UPDATED);
        assertThat(event.getChangedBy()).isEqualTo("editor");
        assertThat(event.getComment()).isEqualTo("Lesson updated");
        assertThat(event.getBeforeJson()).contains("Signals before");
        assertThat(event.getAfterJson()).contains("Signals after");
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void saveChangeLog_persistsEventAsChangeLogRow() {
        AuditService service = new AuditService(changeLogRepository, new ObjectMapper(), auditEventProducer);
        UUID lessonId = UUID.randomUUID();
        LessonAuditEvent event = LessonAuditEvent.builder()
                .entityId(lessonId)
                .action(ChangeAction.DELETED)
                .changedBy("admin")
                .beforeJson("{\"title\":\"Signals\"}")
                .comment("Lesson deleted")
                .build();

        service.saveChangeLog(event);

        ArgumentCaptor<ChangeLog> captor = ArgumentCaptor.forClass(ChangeLog.class);
        verify(changeLogRepository).save(captor.capture());

        ChangeLog saved = captor.getValue();
        assertThat(saved.getEntityType()).isEqualTo("LESSON");
        assertThat(saved.getEntityId()).isEqualTo(lessonId);
        assertThat(saved.getAction()).isEqualTo(ChangeAction.DELETED);
        assertThat(saved.getChangedBy()).isEqualTo("admin");
        assertThat(saved.getBeforeJson()).isEqualTo("{\"title\":\"Signals\"}");
        assertThat(saved.getAfterJson()).isNull();
        assertThat(saved.getComment()).isEqualTo("Lesson deleted");
    }

    @Test
    void getLessonHistory_mapsRepositoryRowsToDto() {
        AuditService service = new AuditService(changeLogRepository, new ObjectMapper(), auditEventProducer);
        UUID lessonId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();

        ChangeLog log = new ChangeLog();
        log.setId(logId);
        log.setEntityType("LESSON");
        log.setEntityId(lessonId);
        log.setAction(ChangeAction.DELETED);
        log.setChangedBy("admin");
        log.setBeforeJson("{\"title\":\"Signals\"}");
        log.setAfterJson(null);
        log.setComment("Lesson deleted");
        log.setVersion(2L);

        when(changeLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("LESSON", lessonId))
                .thenReturn(List.of(log));

        List<ChangeLogDto> result = service.getLessonHistory(lessonId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(logId);
        assertThat(result.get(0).getAction()).isEqualTo(ChangeAction.DELETED);
        assertThat(result.get(0).getChangedBy()).isEqualTo("admin");
        assertThat(result.get(0).getComment()).isEqualTo("Lesson deleted");
    }

    @Test
    void logLessonChange_throwsWhenSnapshotSerializationFails() throws JsonProcessingException {
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new JsonProcessingException("boom") {});
        AuditService service = new AuditService(changeLogRepository, objectMapper, auditEventProducer);

        assertThatThrownBy(() -> service.logLessonChange(ChangeAction.CREATED, null, lesson("Signals"), "admin", "create"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize lesson audit snapshot");
    }

    private Lesson lesson(String title) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setVersion(1L);
        lesson.setOrderNumber(1);
        lesson.setTitle(title);
        lesson.setDurationHours(2);
        lesson.setType(LessonType.LECTURE);
        return lesson;
    }
}
