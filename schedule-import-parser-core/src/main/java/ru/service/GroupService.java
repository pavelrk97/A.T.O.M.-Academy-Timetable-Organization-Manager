package ru.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import ru.dto.GroupDto;
import ru.exception.ResourceNotFoundException;
import ru.mapper.GroupMapper;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.repository.GroupRepository;

import java.util.List;
import java.util.UUID;

@Service
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<GroupDto> getAll() {
        return groupRepository.findAllByOrderByCodeAsc().stream()
                .map(GroupMapper::toDto)
                .toList();
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public GroupDto getById(UUID id) {
        return GroupMapper.toDto(groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + id)));
    }

    @Transactional
    public GroupDto create(GroupDto dto) {
        Group group = GroupMapper.toEntity(dto);
        link(group);
        return GroupMapper.toDto(groupRepository.save(group));
    }

    @Transactional
    public GroupDto update(UUID id, GroupDto dto) {
        Group group = findEntity(id);
        group.setCode(dto.getCode());
        group.setLocation(dto.getLocation());
        group.setCourse(dto.getCourse());

        if (dto.getDays() != null) {
            Group replacement = GroupMapper.toEntity(dto);
            group.getDays().clear();
            replacement.getDays().forEach(day -> {
                day.setGroup(group);
                day.getLessons().forEach(lesson -> lesson.setDay(day));
                group.getDays().add(day);
            });
        }

        return GroupMapper.toDto(groupRepository.save(group));
    }

    @Transactional
    public void delete(UUID id) {
        groupRepository.delete(findEntity(id));
    }

    public Group findEntity(UUID id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + id));
    }

    private void link(Group group) {
        for (Day day : group.getDays()) {
            day.setGroup(group);
            for (Lesson lesson : day.getLessons()) {
                lesson.setDay(day);
            }
        }
    }
}
