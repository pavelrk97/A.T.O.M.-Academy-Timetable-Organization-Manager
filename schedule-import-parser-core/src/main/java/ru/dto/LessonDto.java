package ru.dto;

import lombok.*;
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

    private Integer orderNumber;

    private String title;

    private String lecturer;

    private List<String> lecturers;

    private Integer durationHours;

    private LessonType type;
}