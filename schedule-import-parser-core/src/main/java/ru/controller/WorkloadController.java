package ru.controller;

import org.springframework.format.annotation.DateTimeFormat;
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

    private final LessonService lessonService;

    public WorkloadController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GetMapping
    public List<WorkloadDto> getWorkload(
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication
    ) {
        return lessonService.getWorkload(instructorId, from, to, authentication);
    }
}
