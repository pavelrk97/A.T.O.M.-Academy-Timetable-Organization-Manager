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
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleClient.getWorkload(authHeaderFactory.bearerHeader(authentication), instructorId, from, to);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWorkload(Authentication authentication,
                                                 @RequestParam(required = false) UUID instructorId,
                                                 @RequestParam(required = false) String instructorQuery,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1900, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.of(3000, 12, 31);
        byte[] workbook = scheduleClient.exportWorkload(authHeaderFactory.bearerHeader(authentication), instructorId, instructorQuery, from, to);
        String scope = instructorId != null ? "single" : (instructorQuery != null && !instructorQuery.isBlank() ? "filtered" : "all");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"workload-%s-%s-%s.xlsx\"".formatted(scope, effectiveFrom, effectiveTo))
                .contentType(XLSX_MEDIA_TYPE)
                .body(workbook);
    }
}
