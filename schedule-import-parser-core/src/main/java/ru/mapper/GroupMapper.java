package ru.mapper;

import ru.dto.GroupDto;
import ru.model.Day;
import ru.model.Group;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GroupMapper {

    public static GroupDto toDto(Group group) {

        List<Day> days = group.getDays() != null
                ? group.getDays()
                : new ArrayList<>();

        return GroupDto.builder()
                .id(group.getId())
                .code(group.getCode())
                .location(group.getLocation())
                .days(
                        days.stream()
                                .map(DayMapper::toDto)
                                .collect(Collectors.toList())
                )
                .build();
    }

    public static Group toEntity(GroupDto dto) {

        Group group = new Group();

        group.setId(dto.getId());
        group.setCode(dto.getCode());
        group.setLocation(dto.getLocation());

        if (dto.getDays() != null) {

            group.setDays(
                    dto.getDays()
                            .stream()
                            .map(DayMapper::toEntity)
                            .collect(Collectors.toList())
            );

        } else {

            group.setDays(new ArrayList<>());
        }

        return group;
    }
}