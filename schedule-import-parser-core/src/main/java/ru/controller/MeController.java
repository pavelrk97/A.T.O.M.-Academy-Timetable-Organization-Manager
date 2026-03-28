package ru.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.MyNotificationDto;
import ru.dto.ScheduleGridDto;
import ru.dto.WorkloadCalendarDto;
import ru.service.MyCabinetService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MyCabinetService myCabinetService;

    public MeController(MyCabinetService myCabinetService) {
        this.myCabinetService = myCabinetService;
    }

    @GetMapping("/schedule/grid")
    public ScheduleGridDto getFullScheduleGrid(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return myCabinetService.getFullScheduleGrid(from, to);
    }

    @GetMapping("/schedule/instructor-grid")
    public ScheduleGridDto getInstructorScheduleGrid(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return myCabinetService.getInstructorScheduleGrid(authentication, from, to);
    }

    @GetMapping("/workload/calendar")
    public WorkloadCalendarDto getMyWorkloadCalendar(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return myCabinetService.getMyWorkloadCalendar(authentication, from, to);
    }

    @GetMapping("/notifications")
    public List<MyNotificationDto> getMyNotifications(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return myCabinetService.getMyNotifications(authentication, from, to);
    }
}
