package ru.dto;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonDto {

    private UUID id;
    private String subject;
    private String teacher;
    private String timeStart;
    private String timeEnd;
    private String location;
}