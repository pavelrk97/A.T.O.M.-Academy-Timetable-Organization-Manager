package ru.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.client.ScheduleClient;
import ru.dto.GroupDto;
import ru.security.DownstreamAuthHeaderFactory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final ScheduleClient scheduleClient;
    private final DownstreamAuthHeaderFactory authHeaderFactory;

    public GroupController(ScheduleClient scheduleClient,
                           DownstreamAuthHeaderFactory authHeaderFactory) {
        this.scheduleClient = scheduleClient;
        this.authHeaderFactory = authHeaderFactory;
    }

    @GetMapping
    public List<GroupDto> getAll(Authentication authentication) {
        return scheduleClient.getGroups(authHeaderFactory.bearerHeader(authentication));
    }

    @GetMapping("/{id}")
    public GroupDto getById(Authentication authentication, @PathVariable UUID id) {
        return scheduleClient.getGroupById(authHeaderFactory.bearerHeader(authentication), id);
    }

    @PostMapping
    public GroupDto create(Authentication authentication, @RequestBody GroupDto dto) {
        return scheduleClient.createGroup(authHeaderFactory.bearerHeader(authentication), dto);
    }

    @PutMapping("/{id}")
    public GroupDto update(Authentication authentication, @PathVariable UUID id, @RequestBody GroupDto dto) {
        return scheduleClient.updateGroup(authHeaderFactory.bearerHeader(authentication), id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(Authentication authentication, @PathVariable UUID id) {
        scheduleClient.deleteGroup(authHeaderFactory.bearerHeader(authentication), id);
    }
}
