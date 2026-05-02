package ru.service;

import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.User;
import ru.parser.ScheduleCsvParser;
import ru.repository.GroupRepository;
import ru.repository.UserRepository;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private final GroupRepository groupRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final CsvImportArchiveService csvImportArchiveService;
    private final InstructorIdentitySyncService instructorIdentitySyncService;
    private final EntityManager entityManager;

    public CsvImportService(GroupRepository groupRepository,
                            UserService userService,
                            UserRepository userRepository,
                            CsvImportArchiveService csvImportArchiveService,
                            InstructorIdentitySyncService instructorIdentitySyncService,
                            EntityManager entityManager) {
        this.groupRepository = groupRepository;
        this.userService = userService;
        this.userRepository = userRepository;
        this.csvImportArchiveService = csvImportArchiveService;
        this.instructorIdentitySyncService = instructorIdentitySyncService;
        this.entityManager = entityManager;
    }

    @Transactional
    public int importFromCsv(InputStream csvStream) throws Exception {
        Path stagedFile = null;
        try {
            stagedFile = csvImportArchiveService.stageUpload(csvStream);
            csvImportArchiveService.backupCurrentSource();
            clearScheduleData();

            try (InputStream stagedInputStream = Files.newInputStream(stagedFile)) {
                Set<String> instructors = collectInstructorNames();
                List<Group> groups = ScheduleCsvParser.parse(stagedInputStream, instructors);
                log.info("CSV parsed successfully: groups={}, knownInstructors={}",
                        groups.size(),
                        instructors.size());
                int imported = importGroups(groups);
                csvImportArchiveService.promoteToCurrent(stagedFile);
                instructorIdentitySyncService.syncCurrentInstructors();
                log.info("CSV import committed: importedGroups={}, currentFile={}, previousFile={}",
                        imported,
                        csvImportArchiveService.getCurrentFile(),
                        csvImportArchiveService.getPreviousFile());
                return imported;
            }
        } catch (Exception ex) {
            log.error("CSV import failed", ex);
            csvImportArchiveService.cleanupStaged(stagedFile);
            throw ex;
        }
    }

    @Transactional
    public int importGroups(List<Group> groups) {
        int imported = 0;
        // Pre-load всех потенциальных инструкторов одним SELECT'ом и складываем в кеш
        // по lower(fullName). Раньше для каждого имени делался отдельный запрос
        // через findOrCreateInstructor → findAllByFullNameIgnoreCase, и при ~100
        // инструкторах это было ~100 round-trip'ов. Теперь — один.
        Map<String, User> instructorCache = preloadInstructorCache();
        log.info("Importing group batch: groups={}, preloadedInstructors={}",
                groups.size(), instructorCache.size());
        for (Group importedGroup : groups) {
            upsertGroup(importedGroup, instructorCache);
            imported++;
        }
        log.info("Group batch imported: groups={}, instructorCacheSize={}", imported, instructorCache.size());
        return imported;
    }

    /**
     * Возвращает map &lt;lower-case fullName&gt; → лучший кандидат на инструктора.
     * Один SELECT по всем canTeach=true пользователям заменяет N+1 запрос
     * findAllByFullNameIgnoreCase для каждого имени из CSV. При множественных
     * совпадениях по ФИО берётся тот же «лучший» кандидат, как и в
     * UserService.pickBestInstructorMatch — активный, INSTRUCTOR, не imported-*.
     */
    private Map<String, User> preloadInstructorCache() {
        Map<String, User> cache = new HashMap<>();
        for (User user : userRepository.findAllByCanTeachTrueOrderByFullNameAsc()) {
            String fullName = user.getFullName();
            if (!userService.isMeaningfulInstructorName(fullName)) {
                continue;
            }
            String key = fullName.trim().toLowerCase(Locale.ROOT);
            // Если по этому ФИО уже есть запись, оставляем «лучшего»: активный >
            // INSTRUCTOR > canTeach > не imported-*. Чтобы не зависеть от UserService
            // (его pickBestInstructorMatch private), повторим логику простым
            // компаратором.
            User existing = cache.get(key);
            if (existing == null || compareInstructors(user, existing) > 0) {
                cache.put(key, user);
            }
        }
        return cache;
    }

    private int compareInstructors(User a, User b) {
        int byActive = Boolean.compare(a.isActive(), b.isActive());
        if (byActive != 0) return byActive;
        int byCanTeach = Boolean.compare(a.isCanTeach(), b.isCanTeach());
        if (byCanTeach != 0) return byCanTeach;
        boolean aImported = a.getUsername() != null && a.getUsername().startsWith("imported-");
        boolean bImported = b.getUsername() != null && b.getUsername().startsWith("imported-");
        // не imported лучше imported
        return Boolean.compare(!aImported, !bImported);
    }

    private void clearScheduleData() {
        int deletedNotifications = entityManager.createNativeQuery("delete from notifications").executeUpdate();
        int deletedUserGroups = entityManager.createNativeQuery("delete from user_groups").executeUpdate();
        int deletedLessonInstructors = entityManager.createNativeQuery("delete from lesson_instructors").executeUpdate();
        int deletedLessonLecturers = entityManager.createNativeQuery("delete from lesson_lecturers").executeUpdate();
        int deletedDayMeta = entityManager.createNativeQuery("delete from day_meta").executeUpdate();
        int deletedLessons = entityManager.createNativeQuery("delete from lessons").executeUpdate();
        int deletedDays = entityManager.createNativeQuery("delete from days").executeUpdate();
        int deletedGroups = entityManager.createNativeQuery("delete from groups").executeUpdate();
        int deletedChangeLogs = entityManager.createNativeQuery("delete from change_logs").executeUpdate();

        entityManager.clear();

        log.info("Schedule data cleared before CSV import: groups={}, days={}, lessons={}, dayMeta={}, lessonLecturers={}, lessonInstructors={}, userGroups={}, notifications={}, changeLogs={}",
                deletedGroups,
                deletedDays,
                deletedLessons,
                deletedDayMeta,
                deletedLessonLecturers,
                deletedLessonInstructors,
                deletedUserGroups,
                deletedNotifications,
                deletedChangeLogs);
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
                lesson.setLecturers(resolveLecturerNames(importedLesson));
                lesson.setAssignedInstructors(resolveInstructors(importedLesson, instructorCache));
                lesson.setDay(day);
                day.getLessons().add(lesson);
            }

            group.getDays().add(day);
        }

        groupRepository.save(group);
        // На крупных импортах (50+ групп) этот INFO-лог замедлял работу и забивал
        // вывод. Переводим в DEBUG — суммарную статистику оставляем в importGroups.
        if (log.isDebugEnabled()) {
            log.debug("Group {}: code={}, days={}, lessons={}",
                    existed ? "updated" : "created",
                    group.getCode(),
                    group.getDays().size(),
                    group.getDays().stream().mapToInt(day -> day.getLessons().size()).sum());
        }
    }

    private List<String> resolveLecturerNames(Lesson importedLesson) {
        Map<String, String> uniqueNames = new LinkedHashMap<>();
        if (importedLesson.getLecturers() != null) {
            for (String name : importedLesson.getLecturers()) {
                addUniqueName(uniqueNames, name);
            }
        }
        addUniqueName(uniqueNames, importedLesson.getLecturer());
        return new ArrayList<>(uniqueNames.values());
    }

    private List<User> resolveInstructors(Lesson importedLesson, Map<String, User> instructorCache) {
        Map<String, User> uniqueInstructors = new LinkedHashMap<>();
        for (String name : resolveLecturerNames(importedLesson)) {
            String key = name.trim().toLowerCase(Locale.ROOT);
            // Сначала смотрим в пред-загруженный кеш (preloadInstructorCache).
            // Если нет — это новый инструктор: создаём через findOrCreateInstructor
            // (он сам сохранит и вернёт persisted entity), и кладём в кеш, чтобы
            // повторные встречи того же имени в других занятиях брались отсюда же.
            User instructor = instructorCache.get(key);
            if (instructor == null) {
                instructor = userService.findOrCreateInstructor(name);
                instructorCache.put(key, instructor);
            }
            uniqueInstructors.putIfAbsent(key, instructor);
        }
        return new ArrayList<>(uniqueInstructors.values());
    }

    private void addUniqueName(Map<String, String> uniqueNames, String name) {
        if (!userService.isMeaningfulInstructorName(name)) {
            return;
        }
        uniqueNames.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), name.trim());
    }

    /**
     * Объединяет встроенный fallback-список ScheduleCsvParser с актуальными ФИО из БД
     * (всех users с canTeach=true). Дубликаты схлопываются по equals.
     * Так парсер видит и преподавателей, которые ещё не успели попасть в users
     * (legacy), и свежих, добавленных через UI.
     */
    private Set<String> collectInstructorNames() {
        Set<String> names = new LinkedHashSet<>(ScheduleCsvParser.defaultInstructors());
        names.addAll(userService.collectKnownInstructorNames());
        return names;
    }
}
