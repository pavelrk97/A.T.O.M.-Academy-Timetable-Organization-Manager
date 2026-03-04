package ru.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayDto {

    private UUID id;
    private String date;
    private List<LessonDto> lessons;
}