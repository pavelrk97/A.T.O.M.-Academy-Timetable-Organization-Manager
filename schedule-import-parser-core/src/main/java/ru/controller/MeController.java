package ru.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.MyDashboardDataDto;
import ru.dto.MyNotificationDto;
import ru.dto.ScheduleGridDto;
import ru.dto.WorkloadCalendarDto;
import ru.service.MyCabinetService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

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

    @GetMapping("/workload/export")
    public ResponseEntity<byte[]> exportMyWorkload(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1900, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.of(3000, 12, 31);
        byte[] workbook = myCabinetService.exportMyWorkloadExcel(authentication, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"my-workload-%s-%s.xlsx\"".formatted(effectiveFrom, effectiveTo))
                .contentType(XLSX_MEDIA_TYPE)
                .body(workbook);
    }

    @GetMapping("/notifications")
    public List<MyNotificationDto> getMyNotifications(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return myCabinetService.getMyNotifications(authentication, from, to);
    }

    @GetMapping("/dashboard")
    public MyDashboardDataDto getMyDashboard(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return myCabinetService.getDashboard(authentication, from, to);
    }
}
