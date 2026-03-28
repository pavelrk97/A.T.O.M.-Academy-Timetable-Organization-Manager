package ru.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.client.IdentityClient;
import ru.client.ScheduleClient;
import ru.dto.ChangePasswordRequest;
import ru.dto.MyDashboardDataDto;
import ru.dto.MyDashboardDto;
import ru.dto.MyNotificationDto;
import ru.dto.MyProfileUpdateRequest;
import ru.dto.ScheduleGridDto;
import ru.dto.UserDto;
import ru.dto.WorkloadCalendarDto;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final IdentityClient identityClient;
    private final ScheduleClient scheduleClient;

    public MeController(IdentityClient identityClient, ScheduleClient scheduleClient) {
        this.identityClient = identityClient;
        this.scheduleClient = scheduleClient;
    }

    @GetMapping("/profile")
    public UserDto getProfile(@RequestHeader("Authorization") String authorization) {
        return identityClient.getMyProfile(authorization);
    }

    @PutMapping("/profile")
    public UserDto updateProfile(@RequestHeader("Authorization") String authorization,
                                 @Valid @RequestBody MyProfileUpdateRequest request) {
        return identityClient.updateMyProfile(authorization, request);
    }

    @PutMapping("/password")
    public void changePassword(@RequestHeader("Authorization") String authorization,
                               @Valid @RequestBody ChangePasswordRequest request) {
        identityClient.changeMyPassword(authorization, request);
    }

    @GetMapping("/schedule/grid")
    public ScheduleGridDto getFullScheduleGrid(@RequestHeader("Authorization") String authorization,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleClient.getMyFullScheduleGrid(authorization, from, to);
    }

    @GetMapping("/schedule/instructor-grid")
    public ScheduleGridDto getInstructorScheduleGrid(@RequestHeader("Authorization") String authorization,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleClient.getMyInstructorScheduleGrid(authorization, from, to);
    }

    @GetMapping("/workload/calendar")
    public WorkloadCalendarDto getWorkloadCalendar(@RequestHeader("Authorization") String authorization,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleClient.getMyWorkloadCalendar(authorization, from, to);
    }

    @GetMapping("/notifications")
    public List<MyNotificationDto> getNotifications(@RequestHeader("Authorization") String authorization,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleClient.getMyNotifications(authorization, from, to);
    }

    @GetMapping("/dashboard")
    public MyDashboardDto getDashboard(@RequestHeader("Authorization") String authorization,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        MyDashboardDataDto dashboardData = scheduleClient.getMyDashboard(authorization, from, to);
        return MyDashboardDto.builder()
                .profile(identityClient.getMyProfile(authorization))
                .instructorSchedule(dashboardData.getInstructorSchedule())
                .workload(dashboardData.getWorkload())
                .notifications(dashboardData.getNotifications())
                .build();
    }
}
