package ru.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import ru.dto.ChangeLogDto;
import ru.dto.GroupDto;
import ru.dto.LessonDto;
import ru.dto.ScheduleEntryDto;
import ru.dto.WorkloadDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "schedule-service", url = "${schedule.service.url}")
public interface ScheduleClient {

    @GetMapping("/api/public/schedule")
    List<ScheduleEntryDto> getPublicSchedule(@RequestParam(required = false) String groupCode,
                                             @RequestParam(required = false) UUID instructorId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);

    @GetMapping("/api/groups")
    List<GroupDto> getGroups(@RequestHeader("Authorization") String authorization);

    @GetMapping("/api/groups/{id}")
    GroupDto getGroupById(@RequestHeader("Authorization") String authorization, @PathVariable UUID id);

    @PostMapping("/api/groups")
    GroupDto createGroup(@RequestHeader("Authorization") String authorization, @RequestBody GroupDto dto);

    @PutMapping("/api/groups/{id}")
    GroupDto updateGroup(@RequestHeader("Authorization") String authorization, @PathVariable UUID id, @RequestBody GroupDto dto);

    @DeleteMapping("/api/groups/{id}")
    void deleteGroup(@RequestHeader("Authorization") String authorization, @PathVariable UUID id);

    @GetMapping("/api/lessons")
    List<ScheduleEntryDto> getLessons(@RequestHeader("Authorization") String authorization,
                                      @RequestParam(required = false) String groupCode,
                                      @RequestParam(required = false) UUID instructorId,
                                      @RequestParam(required = false) LocalDate from,
                                      @RequestParam(required = false) LocalDate to);

    @GetMapping("/api/lessons/{id}")
    LessonDto getLessonById(@RequestHeader("Authorization") String authorization, @PathVariable UUID id);

    @PostMapping("/api/lessons")
    LessonDto createLesson(@RequestHeader("Authorization") String authorization, @RequestBody LessonDto dto);

    @PutMapping("/api/lessons/{id}")
    LessonDto updateLesson(@RequestHeader("Authorization") String authorization, @PathVariable UUID id, @RequestBody LessonDto dto);

    @DeleteMapping("/api/lessons/{id}")
    void deleteLesson(@RequestHeader("Authorization") String authorization,
                      @PathVariable UUID id,
                      @RequestParam Long version);

    @GetMapping("/api/lessons/{id}/history")
    List<ChangeLogDto> getLessonHistory(@RequestHeader("Authorization") String authorization, @PathVariable UUID id);

    @GetMapping("/api/workload")
    List<WorkloadDto> getWorkload(@RequestHeader("Authorization") String authorization,
                                  @RequestParam(required = false) UUID instructorId,
                                  @RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to);
}
