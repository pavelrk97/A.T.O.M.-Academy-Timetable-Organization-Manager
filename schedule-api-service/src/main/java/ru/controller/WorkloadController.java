package ru.controller;

import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import ru.client.ScheduleClient;
import ru.dto.WorkloadCalendarDto;
import ru.dto.WorkloadDto;
import ru.security.DownstreamAuthHeaderFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workload")
public class WorkloadController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final ScheduleClient scheduleClient;
    private final DownstreamAuthHeaderFactory authHeaderFactory;

    public WorkloadController(ScheduleClient scheduleClient,
                              DownstreamAuthHeaderFactory authHeaderFactory) {
        this.scheduleClient = scheduleClient;
        this.authHeaderFactory = authHeaderFactory;
    }

    @GetMapping
    public List<WorkloadDto> getWorkload(Authentication authentication,
                                         @RequestParam(required = false) UUID instructorId,
                                         @RequestParam(name = "instructorIds", required = false) List<UUID> instructorIds,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleClient.getWorkload(authHeaderFactory.bearerHeader(authentication),
                instructorId, instructorIds, from, to);
    }

    @GetMapping("/calendar")
    public WorkloadCalendarDto getInstructorCalendar(Authentication authentication,
                                                     @RequestParam UUID instructorId,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleClient.getWorkloadCalendar(authHeaderFactory.bearerHeader(authentication), instructorId, from, to);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWorkload(Authentication authentication,
                                                 @RequestParam(required = false) UUID instructorId,
                                                 @RequestParam(name = "instructorIds", required = false) List<UUID> instructorIds,
                                                 @RequestParam(required = false) String instructorQuery,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(required = false, defaultValue = "true") boolean includeBusinessTrips) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1900, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.of(3000, 12, 31);
        byte[] workbook = scheduleClient.exportWorkload(authHeaderFactory.bearerHeader(authentication),
                instructorId, instructorIds, instructorQuery, from, to, includeBusinessTrips);
        String scope;
        if (instructorIds != null && !instructorIds.isEmpty()) {
            scope = "selected-" + instructorIds.size();
        } else if (instructorId != null) {
            scope = "single";
        } else if (instructorQuery != null && !instructorQuery.isBlank()) {
            scope = "filtered";
        } else {
            scope = "all";
        }
        if (!includeBusinessTrips) {
            scope += "-no-trips";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"workload-%s-%s-%s.xlsx\"".formatted(scope, effectiveFrom, effectiveTo))
                .contentType(XLSX_MEDIA_TYPE)
                .body(workbook);
    }
}
