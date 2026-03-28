package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyDashboardDataDto {

    private ScheduleGridDto instructorSchedule;
    private WorkloadCalendarDto workload;
    private List<MyNotificationDto> notifications;
}
