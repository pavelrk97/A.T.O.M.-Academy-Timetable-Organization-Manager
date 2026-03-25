package ru.mapper;

import ru.dto.GroupDto;
import ru.model.Day;
import ru.model.Group;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GroupMapper {

    public static GroupDto toDto(Group group) {
        List<Day> days = group.getDays() != null ? group.getDays() : new ArrayList<>();

        return GroupDto.builder()
                .id(group.getId())
                .code(group.getCode())
                .location(group.getLocation())
                .course(group.getCourse())
                .days(days.stream().map(DayMapper::toDto).collect(Collectors.toList()))
                .build();
    }

    public static Group toEntity(GroupDto dto) {
        Group group = new Group();
        group.setId(dto.getId());
        group.setCode(dto.getCode());
        group.setLocation(dto.getLocation());
        group.setCourse(dto.getCourse());

        List<Day> days = dto.getDays() != null
                ? dto.getDays().stream().map(DayMapper::toEntity).collect(Collectors.toList())
                : new ArrayList<>();

        days.forEach(day -> day.setGroup(group));
        group.setDays(days);
        return group;
    }
}
