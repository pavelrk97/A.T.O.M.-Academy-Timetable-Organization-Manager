package ru.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.User;
import ru.parser.ScheduleCsvParser;
import ru.repository.GroupRepository;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private final GroupRepository groupRepository;
    private final UserService userService;

    public CsvImportService(GroupRepository groupRepository,
                            UserService userService) {
        this.groupRepository = groupRepository;
        this.userService = userService;
    }

    @Transactional
    public int importFromCsv(InputStream csvStream) throws Exception {
        try {
            List<Group> groups = ScheduleCsvParser.parse(csvStream);
            log.info("CSV parsed successfully: groups={}", groups.size());
            int imported = importGroups(groups);
            log.info("CSV import committed: importedGroups={}", imported);
            return imported;
        } catch (Exception ex) {
            log.error("CSV import failed", ex);
            throw ex;
        }
    }

    @Transactional
    public int importGroups(List<Group> groups) {
        int imported = 0;
        Map<String, User> instructorCache = new HashMap<>();
        log.info("Importing group batch: groups={}", groups.size());
        for (Group importedGroup : groups) {
            upsertGroup(importedGroup, instructorCache);
            imported++;
        }
        log.info("Group batch imported: groups={}, instructorCacheSize={}", imported, instructorCache.size());
        return imported;
    }

    private void upsertGroup(Group importedGroup, Map<String, User> instructorCache) {
        Optional<Group> existingGroupOpt = groupRepository.findByCode(importedGroup.getCode());
        Group group = existingGroupOpt.orElseGet(Group::new);
        boolean existed = existingGroupOpt.isPresent();

        group.setCode(importedGroup.getCode());
        group.setLocation(importedGroup.getLocation());
        group.setCourse(importedGroup.getCourse());
        group.getDays().clear();

        for (Day importedDay : importedGroup.getDays()) {
            Day day = new Day();
            day.setDate(importedDay.getDate());
            day.setMeta(importedDay.getMeta());
            day.setGroup(group);

            for (Lesson importedLesson : importedDay.getLessons()) {
                Lesson lesson = new Lesson();
                lesson.setOrderNumber(importedLesson.getOrderNumber());
                lesson.setTitle(importedLesson.getTitle());
                lesson.setLecturer(importedLesson.getLecturer());
                lesson.setDurationHours(importedLesson.getDurationHours());
                lesson.setNote(importedLesson.getNote());
                lesson.setType(importedLesson.getType());
                lesson.setLecturers(importedLesson.getLecturers() != null
                        ? new ArrayList<>(importedLesson.getLecturers())
                        : new ArrayList<>());
                lesson.setAssignedInstructors(resolveInstructors(importedLesson, instructorCache));
                lesson.setDay(day);
                day.getLessons().add(lesson);
            }

            group.getDays().add(day);
        }

        groupRepository.save(group);
        log.info("Group {}: code={}, days={}, lessons={}",
                existed ? "updated" : "created",
                group.getCode(),
                group.getDays().size(),
                group.getDays().stream().mapToInt(day -> day.getLessons().size()).sum());
    }

    private List<User> resolveInstructors(Lesson importedLesson, Map<String, User> instructorCache) {
        List<String> names = new ArrayList<>();
        if (importedLesson.getLecturers() != null) {
            names.addAll(importedLesson.getLecturers());
        }
        if (importedLesson.getLecturer() != null
                && !importedLesson.getLecturer().isBlank()
                && !names.contains(importedLesson.getLecturer())) {
            names.add(importedLesson.getLecturer());
        }

        List<User> instructors = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String key = name.trim().toLowerCase(Locale.ROOT);
            instructors.add(instructorCache.computeIfAbsent(key, ignored -> userService.findOrCreateInstructor(name)));
        }
        return instructors;
    }
}
