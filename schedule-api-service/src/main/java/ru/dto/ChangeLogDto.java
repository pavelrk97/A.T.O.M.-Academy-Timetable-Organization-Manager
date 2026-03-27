package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.model.ChangeAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeLogDto {
    private UUID id;
    private String entityType;
    private UUID entityId;
    private ChangeAction action;
    private String changedBy;
    private LocalDateTime changedAt;
    private String beforeJson;
    private String afterJson;
    private String comment;
}
