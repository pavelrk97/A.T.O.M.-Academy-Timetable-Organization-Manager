package ru.mapper;

import ru.dto.DayDto;
import ru.model.Day;
import ru.model.Lesson;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DayMapper {

    public static DayDto toDto(Day day) {

        List<Lesson> lessons = day.getLessons() != null
                ? day.getLessons()
                : new ArrayList<>();

        return DayDto.builder()
                .id(day.getId())
                .date(day.getDate())
                .lessons(
                        lessons.stream()
                                .map(LessonMapper::toDto)
                                .collect(Collectors.toList())
                )
                .build();
    }

    public static Day toEntity(DayDto dto) {

        Day day = new Day();

        day.setId(dto.getId());
        day.setDate(dto.getDate());

        if (dto.getLessons() != null) {

            day.setLessons(
                    dto.getLessons()
                            .stream()
                            .map(LessonMapper::toEntity)
                            .collect(Collectors.toList())
            );

        } else {

            day.setLessons(new ArrayList<>());
        }

        return day;
    }
}