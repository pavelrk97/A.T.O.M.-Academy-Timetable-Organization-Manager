package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.model.LessonType;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleGridLessonCellDto {

    private UUID lessonId;
    private Long version;
    private Integer orderNumber;
    private String title;
    private LessonType type;
    private Integer durationHours;
    private String note;
    private List<String> instructorNames;
}
