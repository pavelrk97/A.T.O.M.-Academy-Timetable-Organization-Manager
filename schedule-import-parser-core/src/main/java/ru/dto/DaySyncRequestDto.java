package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DaySyncRequestDto {

    private UUID groupId;
    private LocalDate date;
    private Boolean ensureDay;
    private List<LessonDto> lessons;
}
