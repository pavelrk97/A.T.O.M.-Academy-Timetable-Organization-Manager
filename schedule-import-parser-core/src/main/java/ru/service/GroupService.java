package ru.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.dto.GroupDto;
import ru.mapper.GroupMapper;
import ru.model.Group;
import ru.repository.GroupRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;

    public List<GroupDto> getAll() {

        return groupRepository.findAll()
                .stream()
                .map(GroupMapper::toDto)
                .collect(Collectors.toList());
    }

    public GroupDto getById(UUID id) {

        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        return GroupMapper.toDto(group);
    }

    public GroupDto create(GroupDto dto) {

        Group group = GroupMapper.toEntity(dto);

        Group saved = groupRepository.save(group);

        return GroupMapper.toDto(saved);
    }

    public void delete(UUID id) {

        groupRepository.deleteById(id);
    }
}