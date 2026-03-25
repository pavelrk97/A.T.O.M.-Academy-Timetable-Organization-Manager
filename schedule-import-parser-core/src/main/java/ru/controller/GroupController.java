package ru.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.GroupDto;
import ru.service.GroupService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public List<GroupDto> getAll() {
        return groupService.getAll();
    }

    @GetMapping("/{id}")
    public GroupDto getById(@PathVariable UUID id) {
        return groupService.getById(id);
    }

    @PostMapping
    public GroupDto create(@RequestBody GroupDto dto) {
        return groupService.create(dto);
    }

    @PutMapping("/{id}")
    public GroupDto update(@PathVariable UUID id, @RequestBody GroupDto dto) {
        return groupService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        groupService.delete(id);
    }
}
