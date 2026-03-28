package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkloadCalendarLessonDto {

    private UUID lessonId;
    private String groupCode;
    private String title;
    private int durationHours;
}
