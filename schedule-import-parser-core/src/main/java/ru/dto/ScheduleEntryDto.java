package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.model.LessonType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleEntryDto {

    private UUID lessonId;
    private Long version;
    private UUID groupId;
    private String groupCode;
    private String location;
    private LocalDate date;
    private Integer orderNumber;
    private String title;
    private LessonType type;
    private Integer durationHours;
    private String note;
    private List<UUID> instructorIds;
    private List<String> instructorNames;
}
