package ru.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.dto.GroupDto;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "schedule-core-service",
        url = "${core.service.url}"
)
public interface ScheduleClient {

    @GetMapping("/api/groups")
    List<GroupDto> getAllGroups();

    @GetMapping("/api/groups/{id}")
    GroupDto getGroupById(@PathVariable UUID id);

    @PostMapping("/api/groups")
    GroupDto createGroup(@RequestBody GroupDto dto);

    @DeleteMapping("/api/groups/{id}")
    void deleteGroup(@PathVariable UUID id);
}