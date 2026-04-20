package ru.controller;

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
import ru.client.ScheduleClient;
import ru.dto.ChangeLogDto;
import ru.dto.DaySyncRequestDto;
import ru.dto.GroupDto;
import ru.dto.LessonDto;
import ru.dto.ScheduleEntryDto;
import ru.security.DownstreamAuthHeaderFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final ScheduleClient scheduleClient;
    private final DownstreamAuthHeaderFactory authHeaderFactory;

    public LessonController(ScheduleClient scheduleClient,
                            DownstreamAuthHeaderFactory authHeaderFactory) {
        this.scheduleClient = scheduleClient;
        this.authHeaderFactory = authHeaderFactory;
    }

    @GetMapping
    public List<ScheduleEntryDto> getAll(Authentication authentication,
                                         @RequestParam(required = false) String groupCode,
                                         @RequestParam(required = false) UUID instructorId,
                                         @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to) {
        return scheduleClient.getLessons(authHeaderFactory.bearerHeader(authentication), groupCode, instructorId, from, to);
    }

    @GetMapping("/{id}")
    public LessonDto getById(Authentication authentication, @PathVariable UUID id) {
        return scheduleClient.getLessonById(authHeaderFactory.bearerHeader(authentication), id);
    }

    @PostMapping
    public LessonDto create(Authentication authentication, @RequestBody LessonDto dto) {
        return scheduleClient.createLesson(authHeaderFactory.bearerHeader(authentication), dto);
    }

    @PostMapping("/day-sync")
    public GroupDto syncDay(Authentication authentication, @RequestBody DaySyncRequestDto dto) {
        return scheduleClient.syncLessonDay(authHeaderFactory.bearerHeader(authentication), dto);
    }

    @PutMapping("/{id}")
    public LessonDto update(Authentication authentication, @PathVariable UUID id, @RequestBody LessonDto dto) {
        return scheduleClient.updateLesson(authHeaderFactory.bearerHeader(authentication), id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(Authentication authentication,
                       @PathVariable UUID id,
                       @RequestParam Long version) {
        scheduleClient.deleteLesson(authHeaderFactory.bearerHeader(authentication), id, version);
    }

    @GetMapping("/{id}/history")
    public List<ChangeLogDto> history(Authentication authentication, @PathVariable UUID id) {
        return scheduleClient.getLessonHistory(authHeaderFactory.bearerHeader(authentication), id);
    }
}
