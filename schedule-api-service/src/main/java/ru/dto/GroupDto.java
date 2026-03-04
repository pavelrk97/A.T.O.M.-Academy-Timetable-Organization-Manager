package ru.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupDto {

    private UUID id;
    private String name;
    private List<DayDto> days;
}