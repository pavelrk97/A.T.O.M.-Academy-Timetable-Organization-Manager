package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleGridGroupRowDto {

    private UUID groupId;
    private String groupCode;
    private String location;
    private Integer course;
    private List<ScheduleGridDayCellDto> days;
}
