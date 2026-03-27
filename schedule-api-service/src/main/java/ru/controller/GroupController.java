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
import ru.client.CoreClient;
import ru.dto.GroupDto;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final CoreClient coreClient;

    public GroupController(CoreClient coreClient) {
        this.coreClient = coreClient;
    }

    @GetMapping
    public List<GroupDto> getAll(@RequestHeader("Authorization") String authorization) {
        return coreClient.getGroups(authorization);
    }

    @GetMapping("/{id}")
    public GroupDto getById(@RequestHeader("Authorization") String authorization, @PathVariable UUID id) {
        return coreClient.getGroupById(authorization, id);
    }

    @PostMapping
    public GroupDto create(@RequestHeader("Authorization") String authorization, @RequestBody GroupDto dto) {
        return coreClient.createGroup(authorization, dto);
    }

    @PutMapping("/{id}")
    public GroupDto update(@RequestHeader("Authorization") String authorization, @PathVariable UUID id, @RequestBody GroupDto dto) {
        return coreClient.updateGroup(authorization, id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader("Authorization") String authorization, @PathVariable UUID id) {
        coreClient.deleteGroup(authorization, id);
    }
}
