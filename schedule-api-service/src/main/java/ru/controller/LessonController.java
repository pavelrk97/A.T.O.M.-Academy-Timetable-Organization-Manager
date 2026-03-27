package ru.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.client.ScheduleClient;
import ru.dto.ChangeLogDto;
import ru.dto.LessonDto;
import ru.dto.ScheduleEntryDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final ScheduleClient scheduleClient;

    public LessonController(ScheduleClient scheduleClient) {
        this.scheduleClient = scheduleClient;
    }

    @GetMapping
    public List<ScheduleEntryDto> getAll(@RequestHeader("Authorization") String authorization,
                                         @RequestParam(required = false) String groupCode,
                                         @RequestParam(required = false) UUID instructorId,
                                         @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to) {
        return scheduleClient.getLessons(authorization, groupCode, instructorId, from, to);
    }

    @GetMapping("/{id}")
    public LessonDto getById(@RequestHeader("Authorization") String authorization, @PathVariable UUID id) {
        return scheduleClient.getLessonById(authorization, id);
    }

    @PostMapping
    public LessonDto create(@RequestHeader("Authorization") String authorization, @RequestBody LessonDto dto) {
        return scheduleClient.createLesson(authorization, dto);
    }

    @PutMapping("/{id}")
    public LessonDto update(@RequestHeader("Authorization") String authorization, @PathVariable UUID id, @RequestBody LessonDto dto) {
        return scheduleClient.updateLesson(authorization, id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader("Authorization") String authorization,
                       @PathVariable UUID id,
                       @RequestParam Long version) {
        scheduleClient.deleteLesson(authorization, id, version);
    }

    @GetMapping("/{id}/history")
    public List<ChangeLogDto> history(@RequestHeader("Authorization") String authorization, @PathVariable UUID id) {
        return scheduleClient.getLessonHistory(authorization, id);
    }
}
