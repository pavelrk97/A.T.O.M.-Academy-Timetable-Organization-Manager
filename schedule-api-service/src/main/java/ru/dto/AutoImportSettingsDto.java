package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoImportSettingsDto {

    private boolean enabled;
    private String sourceUrl;
    private LocalDateTime lastRunAt;
    private String lastStatus;
    private String lastError;
    private Integer lastImportedGroups;
    private Integer lastImportedLessons;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime nextRunAt;
}
