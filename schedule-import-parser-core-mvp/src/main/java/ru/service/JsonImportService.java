package ru.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.dto.ImportRequest;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.User;
import ru.parser.ScheduleCsvParser;
import ru.repository.GroupRepository;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class JsonImportService {

    private final ObjectMapper objectMapper;
    private final GroupRepository groupRepository;
    private final UserService userService;

    public JsonImportService(ObjectMapper objectMapper,
                             GroupRepository groupRepository,
                             UserService userService,
                             PasswordEncoder passwordEncoder) {
        this.objectMapper = objectMapper;
        this.groupRepository = groupRepository;
        this.userService = userService;
    }

    @Transactional
    public int importFromJson(InputStream jsonStream) throws Exception {
        ImportRequest request = objectMapper.readValue(jsonStream, ImportRequest.class);
        return importGroups(request.getGroups());
    }

    @Transactional
    public int importFromCsv(InputStream csvStream) throws Exception {
        List<Group> groups = ScheduleCsvParser.parse(csvStream);
        return importGroups(groups);
    }

    @Transactional
    public int importGroups(List<Group> groups) {
        int imported = 0;
        for (Group importedGroup : groups) {
            upsertGroup(importedGroup);
            imported++;
        }
        return imported;
    }

    private void upsertGroup(Group importedGroup) {
        Optional<Group> existingGroupOpt = groupRepository.findByCode(importedGroup.getCode());
        Group group = existingGroupOpt.orElseGet(Group::new);

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
                lesson.setLecturers(importedLesson.getLecturers() != null ? new ArrayList<>(importedLesson.getLecturers()) : new ArrayList<>());
                lesson.setAssignedInstructors(resolveInstructors(importedLesson));
                lesson.setDay(day);
                day.getLessons().add(lesson);
            }

            group.getDays().add(day);
        }

        groupRepository.save(group);
    }

    private List<User> resolveInstructors(Lesson importedLesson) {
        List<String> names = new ArrayList<>();
        if (importedLesson.getLecturers() != null) {
            names.addAll(importedLesson.getLecturers());
        }
        if (importedLesson.getLecturer() != null && !importedLesson.getLecturer().isBlank() && !names.contains(importedLesson.getLecturer())) {
            names.add(importedLesson.getLecturer());
        }

        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(userService::findOrCreateInstructor)
                .toList();
    }
}
