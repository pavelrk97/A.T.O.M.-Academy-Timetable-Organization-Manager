package ru.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayDto {

    private UUID id;

    private LocalDate date;

    private List<LessonDto> lessons;
}