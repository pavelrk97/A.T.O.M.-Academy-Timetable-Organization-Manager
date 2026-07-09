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
import ru.dto.WorkloadDto;
import ru.service.LessonService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workload")
public class WorkloadController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final LessonService lessonService;

    public WorkloadController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GetMapping
    public List<WorkloadDto> getWorkload(
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(name = "instructorIds", required = false) List<UUID> instructorIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication
    ) {
        return lessonService.getWorkload(instructorId, instructorIds, from, to, authentication);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWorkload(
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(name = "instructorIds", required = false) List<UUID> instructorIds,
            @RequestParam(required = false) String instructorQuery,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "true") boolean includeBusinessTrips,
            Authentication authentication
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1900, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.of(3000, 12, 31);
        byte[] workbook = lessonService.exportWorkloadExcel(
                instructorId, instructorIds, instructorQuery, from, to, includeBusinessTrips, authentication);
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
