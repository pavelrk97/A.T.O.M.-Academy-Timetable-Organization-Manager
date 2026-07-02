package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.model.ChangeAction;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonAuditEvent {

    private UUID entityId;
    private ChangeAction action;
    private String changedBy;
    private String beforeJson;
    private String afterJson;
    private String comment;
    private Instant occurredAt;
}
