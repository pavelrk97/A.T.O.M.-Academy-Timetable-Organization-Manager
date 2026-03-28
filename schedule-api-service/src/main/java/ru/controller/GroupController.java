package ru.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.client.ScheduleClient;
import ru.dto.GroupDto;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final ScheduleClient scheduleClient;

    public GroupController(ScheduleClient scheduleClient) {
        this.scheduleClient = scheduleClient;
    }

    @GetMapping
    public List<GroupDto> getAll(@RequestHeader("Authorization") String authorization) {
        return scheduleClient.getGroups(authorization);
    }

    @GetMapping("/{id}")
    public GroupDto getById(@RequestHeader("Authorization") String authorization, @PathVariable UUID id) {
        return scheduleClient.getGroupById(authorization, id);
    }

    @PostMapping
    public GroupDto create(@RequestHeader("Authorization") String authorization, @RequestBody GroupDto dto) {
        return scheduleClient.createGroup(authorization, dto);
    }

    @PutMapping("/{id}")
    public GroupDto update(@RequestHeader("Authorization") String authorization, @PathVariable UUID id, @RequestBody GroupDto dto) {
        return scheduleClient.updateGroup(authorization, id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader("Authorization") String authorization, @PathVariable UUID id) {
        scheduleClient.deleteGroup(authorization, id);
    }
}
