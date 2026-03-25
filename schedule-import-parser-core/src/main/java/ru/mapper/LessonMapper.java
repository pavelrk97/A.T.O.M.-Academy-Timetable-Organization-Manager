package ru.mapper;

import ru.dto.LessonDto;
import ru.model.Lesson;

import java.util.ArrayList;

public class LessonMapper {

    public static LessonDto toDto(Lesson lesson) {

        return LessonDto.builder()
                .id(lesson.getId())
                .orderNumber(lesson.getOrderNumber())
                .title(lesson.getTitle())
                .lecturer(lesson.getLecturer())
                .lecturers(
                        lesson.getLecturers() != null
                                ? lesson.getLecturers()
                                : new ArrayList<>()
                )
                .durationHours(lesson.getDurationHours())
                .type(lesson.getType())
                .build();
    }

    public static Lesson toEntity(LessonDto dto) {

        Lesson lesson = new Lesson();

        lesson.setId(dto.getId());
        lesson.setOrderNumber(dto.getOrderNumber());
        lesson.setTitle(dto.getTitle());
        lesson.setLecturer(dto.getLecturer());
        lesson.setLecturers(
                dto.getLecturers() != null
                        ? dto.getLecturers()
                        : new ArrayList<>()
        );
        lesson.setDurationHours(dto.getDurationHours());
        lesson.setType(dto.getType());

        return lesson;
    }
}