package ru.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupDto {

    private UUID id;

    private String code;

    private String location;

    private List<DayDto> days;
}