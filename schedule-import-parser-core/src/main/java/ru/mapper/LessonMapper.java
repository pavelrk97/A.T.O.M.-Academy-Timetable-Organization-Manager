package ru.mapper;

import ru.dto.LessonDto;
import ru.model.Lesson;
import ru.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class LessonMapper {

    public static LessonDto toDto(Lesson lesson) {
        return LessonDto.builder()
                .id(lesson.getId())
                .version(lesson.getVersion())
                .orderNumber(lesson.getOrderNumber())
                .title(lesson.getTitle())
                .lecturer(lesson.getLecturer())
                .lecturers(lesson.getLecturers() != null ? lesson.getLecturers() : new ArrayList<>())
                .durationHours(lesson.getDurationHours())
                .note(lesson.getNote())
                .type(lesson.getType())
                .businessTrip(lesson.isBusinessTrip())
                .dayId(lesson.getDay() != null ? lesson.getDay().getId() : null)
                .groupId(lesson.getDay() != null && lesson.getDay().getGroup() != null ? lesson.getDay().getGroup().getId() : null)
                .instructorIds(extractInstructorIds(lesson))
                .instructorNames(extractInstructorNames(lesson))
                .build();
    }

    public static Lesson toEntity(LessonDto dto) {
        Lesson lesson = new Lesson();
        lesson.setId(dto.getId());
        lesson.setVersion(dto.getVersion());
        lesson.setOrderNumber(dto.getOrderNumber() != null ? dto.getOrderNumber() : 0);
        lesson.setTitle(dto.getTitle());
        lesson.setLecturer(dto.getLecturer());
        lesson.setLecturers(dto.getLecturers() != null ? dto.getLecturers() : new ArrayList<>());
        lesson.setDurationHours(dto.getDurationHours() != null ? dto.getDurationHours() : 0);
        lesson.setNote(dto.getNote());
        lesson.setType(dto.getType());
        lesson.setBusinessTrip(Boolean.TRUE.equals(dto.getBusinessTrip()));
        return lesson;
    }

    private static List<UUID> extractInstructorIds(Lesson lesson) {
        if (lesson.getAssignedInstructors() == null) {
            return new ArrayList<>();
        }

        return lesson.getAssignedInstructors().stream().map(User::getId).collect(Collectors.toList());
    }

    private static List<String> extractInstructorNames(Lesson lesson) {
        if (lesson.getAssignedInstructors() == null || lesson.getAssignedInstructors().isEmpty()) {
            return lesson.getLecturers() != null ? lesson.getLecturers() : new ArrayList<>();
        }

        return lesson.getAssignedInstructors().stream().map(User::getFullName).collect(Collectors.toList());
    }
}
