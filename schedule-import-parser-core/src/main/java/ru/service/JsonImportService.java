package ru.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import ru.dto.ImportRequest;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.repository.GroupRepository;

import java.io.InputStream;
import java.util.Optional;

@Service
public class JsonImportService {

    private final ObjectMapper objectMapper;
    private final GroupRepository groupRepository;

    public JsonImportService(ObjectMapper objectMapper,
                             GroupRepository groupRepository) {
        this.objectMapper = objectMapper;
        this.groupRepository = groupRepository;
    }

    @Transactional
    public void importFromJson(InputStream jsonStream) throws Exception {

        ImportRequest request =
                objectMapper.readValue(jsonStream, ImportRequest.class);

        for (Group importedGroup : request.getGroups()) {

            // 1️⃣ Проверяем существует ли группа по коду
            Optional<Group> existingGroupOpt =
                    groupRepository.findByCode(importedGroup.getCode());

            Group group;

            if (existingGroupOpt.isPresent()) {
                group = existingGroupOpt.get();

                // очищаем старые дни
                group.getDays().clear();
            } else {
                group = new Group();
                group.setCode(importedGroup.getCode());
            }

            group.setLocation(importedGroup.getLocation());
            group.setCourse(importedGroup.getCourse());

            // 2️⃣ Обрабатываем дни
            for (Day importedDay : importedGroup.getDays()) {

                Day day = new Day();
                day.setDate(importedDay.getDate());
                day.setMeta(importedDay.getMeta());
                day.setGroup(group);

                // 3️⃣ Обрабатываем уроки
                for (Lesson importedLesson : importedDay.getLessons()) {

                    Lesson lesson = new Lesson();
                    lesson.setOrderNumber(importedLesson.getOrderNumber());
                    lesson.setTitle(importedLesson.getTitle());
                    lesson.setLecturer(importedLesson.getLecturer());
                    lesson.setDurationHours(importedLesson.getDurationHours());
                    lesson.setNote(importedLesson.getNote());
                    lesson.setType(importedLesson.getType());

                    // 🔵 Важно: поддержка нескольких инструкторов
                    if (importedLesson.getLecturers() != null) {
                        lesson.getLecturers().addAll(importedLesson.getLecturers());
                    }

                    lesson.setDay(day);
                    day.getLessons().add(lesson);
                }

                group.getDays().add(day);
            }

            groupRepository.save(group);
        }
    }
}
