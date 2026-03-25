package ru.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.ChangeLogDto;
import ru.dto.LessonDto;
import ru.dto.ScheduleEntryDto;
import ru.service.LessonService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GetMapping
    public List<ScheduleEntryDto> getSchedule(
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return lessonService.getSchedule(groupCode, instructorId, from, to);
    }

    @GetMapping("/{id}")
    public LessonDto getById(@PathVariable UUID id) {
        return lessonService.getById(id);
    }

    @PostMapping
    public LessonDto create(@RequestBody LessonDto dto, Authentication authentication) {
        return lessonService.create(dto, authentication);
    }

    @PutMapping("/{id}")
    public LessonDto update(@PathVariable UUID id, @RequestBody LessonDto dto, Authentication authentication) {
        return lessonService.update(id, dto, authentication);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id,
                       @RequestParam Long version,
                       Authentication authentication) {
        lessonService.delete(id, version, authentication);
    }

    @GetMapping("/{id}/history")
    public List<ChangeLogDto> history(@PathVariable UUID id) {
        return lessonService.getHistory(id);
    }
}
