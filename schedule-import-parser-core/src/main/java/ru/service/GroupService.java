package ru.service;

import jakarta.transaction.Transactional;
import org.hibernate.collection.spi.PersistentCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.dto.DayDto;
import ru.dto.GroupDto;
import ru.exception.ResourceNotFoundException;
import ru.mapper.DayMapper;
import ru.mapper.GroupMapper;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.repository.GroupRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            mergeDays(group, dto.getDays());
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

    private void mergeDays(Group group, List<DayDto> incomingDays) {
        if (!(group.getDays() instanceof PersistentCollection<?>) && !(group.getDays() instanceof java.util.ArrayList<?>)) {
            group.setDays(new java.util.ArrayList<>(group.getDays()));
        }

        List<Day> groupDays = group.getDays();
        Map<UUID, Day> existingById = new HashMap<>();
        Map<LocalDate, Day> existingByDate = new HashMap<>();

        for (Day existing : groupDays) {
            if (existing.getId() != null) {
                existingById.put(existing.getId(), existing);
            }
            if (existing.getDate() != null) {
                existingByDate.put(existing.getDate(), existing);
            }
        }

        for (DayDto dayDto : incomingDays) {
            Day target = null;
            if (dayDto.getId() != null) {
                target = existingById.get(dayDto.getId());
            }
            if (target == null && dayDto.getDate() != null) {
                target = existingByDate.get(dayDto.getDate());
            }

            if (target == null) {
                Day created = DayMapper.toEntity(dayDto);
                created.setGroup(group);
                created.getLessons().forEach(lesson -> lesson.setDay(created));
                groupDays.add(created);
                if (created.getId() != null) {
                    existingById.put(created.getId(), created);
                }
                if (created.getDate() != null) {
                    existingByDate.put(created.getDate(), created);
                }
                continue;
            }

            target.setDate(dayDto.getDate());
            target.setMeta(dayDto.getMeta());
            if (target.getDate() != null) {
                existingByDate.put(target.getDate(), target);
            }
        }

        groupDays.sort(Comparator.comparing(Day::getDate, Comparator.nullsLast(LocalDate::compareTo)));
    }
}
