package ru.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.client.ScheduleClient;
import ru.dto.GroupDto;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleClient scheduleClient;

    @GetMapping
    public List<GroupDto> getAll() {
        return scheduleClient.getAllGroups();
    }

    @GetMapping("/{id}")
    public GroupDto getById(@PathVariable UUID id) {
        return scheduleClient.getGroupById(id);
    }

    @PostMapping
    public GroupDto create(@RequestBody GroupDto dto) {
        return scheduleClient.createGroup(dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        scheduleClient.deleteGroup(id);
    }
}