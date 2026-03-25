package ru.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.ScheduleEntryDto;
import ru.service.LessonService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public")
public class PublicScheduleController {

    private final LessonService lessonService;

    public PublicScheduleController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GetMapping("/schedule")
    public List<ScheduleEntryDto> getSchedule(
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return lessonService.getSchedule(groupCode, instructorId, from, to);
    }
}
