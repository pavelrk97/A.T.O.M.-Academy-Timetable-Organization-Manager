package ru.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<GroupDto> getAll() {
        List<GroupDto> groups = groupRepository.findAllByOrderByCodeAsc().stream()
                .map(GroupMapper::toDto)
                .toList();
        log.info("Group catalog loaded: groups={}, days={}, lessons={}",
                groups.size(),
                groups.stream().mapToInt(group -> group.getDays() != null ? group.getDays().size() : 0).sum(),
                groups.stream().flatMap(group -> group.getDays().stream())
                        .mapToInt(day -> day.getLessons() != null ? day.getLessons().size() : 0)
                        .sum());
        return groups;
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public GroupDto getById(UUID id) {
        GroupDto group = GroupMapper.toDto(groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + id)));
        log.info("Group loaded: groupId={}, code={}, days={}",
                group.getId(), group.getCode(), group.getDays() != null ? group.getDays().size() : 0);
        return group;
    }

    @Transactional
    public GroupDto create(GroupDto dto) {
        Group group = GroupMapper.toEntity(dto);
        link(group);
        GroupDto created = GroupMapper.toDto(groupRepository.save(group));
        log.info("Group created: groupId={}, code={}", created.getId(), created.getCode());
        return created;
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

        GroupDto updated = GroupMapper.toDto(groupRepository.save(group));
        log.info("Group updated: groupId={}, code={}", updated.getId(), updated.getCode());
        return updated;
    }

    @Transactional
    public void delete(UUID id) {
        Group group = findEntity(id);
        groupRepository.delete(group);
        log.info("Group deleted: groupId={}, code={}", group.getId(), group.getCode());
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
