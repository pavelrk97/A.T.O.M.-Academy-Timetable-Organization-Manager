package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkloadCalendarDto {

    private UUID instructorId;
    private String instructorName;
    private LocalDate from;
    private LocalDate to;
    private int totalHours;
    private List<WorkloadCalendarDayDto> days;
}
