package ru.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dto.LessonAuditEvent;
import ru.model.ChangeAction;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LessonAuditEventListenerTest {

    @Mock
    private AuditService auditService;

    @Test
    void onLessonAuditEvent_persistsChangeLog() {
        LessonAuditEventListener listener = new LessonAuditEventListener(auditService);
        LessonAuditEvent event = LessonAuditEvent.builder()
                .entityId(UUID.randomUUID())
                .action(ChangeAction.CREATED)
                .changedBy("admin")
                .afterJson("{\"title\":\"Signals\"}")
                .comment("Lesson created")
                .occurredAt(Instant.now())
                .build();

        listener.onLessonAuditEvent(event);

        verify(auditService).saveChangeLog(event);
    }
}
