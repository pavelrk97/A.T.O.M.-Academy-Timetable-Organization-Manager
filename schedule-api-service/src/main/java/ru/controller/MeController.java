package ru.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
import ru.security.DownstreamAuthHeaderFactory;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final IdentityClient identityClient;
    private final ScheduleClient scheduleClient;
    private final DownstreamAuthHeaderFactory authHeaderFactory;

    public MeController(IdentityClient identityClient,
                        ScheduleClient scheduleClient,
                        DownstreamAuthHeaderFactory authHeaderFactory) {
        this.identityClient = identityClient;
        this.scheduleClient = scheduleClient;
        this.authHeaderFactory = authHeaderFactory;
    }

    @GetMapping("/profile")
    public UserDto getProfile(Authentication authentication) {
        return identityClient.getMyProfile(authHeaderFactory.bearerHeader(authentication));
    }

    @PutMapping("/profile")
    public UserDto updateProfile(Authentication authentication,
                                 @Valid @RequestBody MyProfileUpdateRequest request) {
        return identityClient.updateMyProfile(authHeaderFactory.bearerHeader(authentication), request);
    }

    @PutMapping("/password")
    public void changePassword(Authentication authentication,
                               @Valid @RequestBody ChangePasswordRequest request) {
        identityClient.changeMyPassword(authHeaderFactory.bearerHeader(authentication), request);
    }

    @GetMapping("/schedule/grid")
    public ScheduleGridDto getFullScheduleGrid(Authentication authentication,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleClient.getMyFullScheduleGrid(authHeaderFactory.bearerHeader(authentication), from, to);
    }

    @GetMapping("/schedule/instructor-grid")
    public ScheduleGridDto getInstructorScheduleGrid(Authentication authentication,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleClient.getMyInstructorScheduleGrid(authHeaderFactory.bearerHeader(authentication), from, to);
    }

    @GetMapping("/workload/calendar")
    public WorkloadCalendarDto getWorkloadCalendar(Authentication authentication,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleClient.getMyWorkloadCalendar(authHeaderFactory.bearerHeader(authentication), from, to);
    }

    @GetMapping("/workload/export")
    public ResponseEntity<byte[]> exportMyWorkload(Authentication authentication,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1900, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.of(3000, 12, 31);
        byte[] workbook = scheduleClient.exportMyWorkload(authHeaderFactory.bearerHeader(authentication), from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"my-workload-%s-%s.xlsx\"".formatted(effectiveFrom, effectiveTo))
                .contentType(XLSX_MEDIA_TYPE)
                .body(workbook);
    }

    @GetMapping("/notifications")
    public List<MyNotificationDto> getNotifications(Authentication authentication,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleClient.getMyNotifications(authHeaderFactory.bearerHeader(authentication), from, to);
    }

    @GetMapping("/dashboard")
    public MyDashboardDto getDashboard(Authentication authentication,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String authorization = authHeaderFactory.bearerHeader(authentication);
        MyDashboardDataDto dashboardData = scheduleClient.getMyDashboard(authorization, from, to);
        return MyDashboardDto.builder()
                .profile(identityClient.getMyProfile(authorization))
                .instructorSchedule(dashboardData.getInstructorSchedule())
                .workload(dashboardData.getWorkload())
                .notifications(dashboardData.getNotifications())
                .build();
    }
}
