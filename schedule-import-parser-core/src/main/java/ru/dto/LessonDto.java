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
public class LessonDto {

    private UUID id;
    private Long version;
    private Integer orderNumber;
    private String title;
    private String lecturer;
    private List<String> lecturers;
    private Integer durationHours;
    private String note;
    private LessonType type;
    private Boolean businessTrip;
    private UUID dayId;
    private UUID groupId;
    private List<UUID> instructorIds;
    private List<String> instructorNames;
}
