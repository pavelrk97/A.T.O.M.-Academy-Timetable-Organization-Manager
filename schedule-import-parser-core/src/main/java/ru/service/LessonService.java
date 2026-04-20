package ru.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import ru.dto.ChangeLogDto;
import ru.dto.DaySyncRequestDto;
import ru.dto.GroupDto;
import ru.dto.LessonDto;
import ru.dto.ScheduleEntryDto;
import ru.dto.WorkloadCalendarDayDto;
import ru.dto.WorkloadCalendarDto;
import ru.dto.WorkloadCalendarLessonDto;
import ru.dto.WorkloadDto;
import ru.exception.ConflictException;
import ru.exception.ForbiddenEditException;
import ru.exception.ResourceNotFoundException;
import ru.mapper.LessonMapper;
import ru.mapper.GroupMapper;
import ru.model.ChangeAction;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.Role;
import ru.model.User;
import ru.repository.DayRepository;
import ru.repository.GroupRepository;
import ru.repository.LessonRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LessonService {

    private static final Logger log = LoggerFactory.getLogger(LessonService.class);
    private static final LocalDate MIN_FILTER_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_FILTER_DATE = LocalDate.of(3000, 12, 31);
    private static final int MAX_LESSONS_PER_DAY = 8;

    private final LessonRepository lessonRepository;
    private final DayRepository dayRepository;
    private final GroupRepository groupRepository;
    private final UserService userService;
    private final AuditService auditService;
    private final WorkloadExcelExportService workloadExcelExportService;

    public LessonService(LessonRepository lessonRepository,
                         DayRepository dayRepository,
                         GroupRepository groupRepository,
                         UserService userService,
                         AuditService auditService,
                         WorkloadExcelExportService workloadExcelExportService) {
        this.lessonRepository = lessonRepository;
        this.dayRepository = dayRepository;
        this.groupRepository = groupRepository;
        this.userService = userService;
        this.auditService = auditService;
        this.workloadExcelExportService = workloadExcelExportService;
    }

    public LessonDto getById(UUID id) {
        return LessonMapper.toDto(findEntity(id));
    }

    public List<ScheduleEntryDto> getSchedule(String groupCode, UUID instructorId, LocalDate from, LocalDate to) {
        String normalizedGroupCode = normalizeGroupCode(groupCode);
        LocalDate effectiveFrom = normalizeFrom(from);
        LocalDate effectiveTo = normalizeTo(to);
        List<Lesson> lessons = loadLessonsForSchedule(normalizedGroupCode, instructorId, effectiveFrom, effectiveTo);
        List<ScheduleEntryDto> schedule = lessons.stream()
                .sorted(Comparator.comparing((Lesson lesson) -> lesson.getDay().getDate())
                        .thenComparing(Lesson::getOrderNumber)
                        .thenComparing(Lesson::getId))
                .map(this::toScheduleEntry)
                .toList();
        log.info("Schedule loaded: groupCode={}, instructorId={}, from={}, to={}, entries={}",
                normalizedGroupCode, instructorId, effectiveFrom, effectiveTo, schedule.size());
        return schedule;
    }

    private List<Lesson> loadLessonsForSchedule(String normalizedGroupCode,
                                                UUID instructorId,
                                                LocalDate effectiveFrom,
                                                LocalDate effectiveTo) {
        if (normalizedGroupCode == null && instructorId == null) {
            return lessonRepository.findForDateRange(effectiveFrom, effectiveTo);
        }
        if (normalizedGroupCode == null) {
            return lessonRepository.findForInstructorAndDateRange(instructorId, effectiveFrom, effectiveTo);
        }
        if (instructorId == null) {
            return lessonRepository.findForGroupCodeAndDateRange(normalizedGroupCode, effectiveFrom, effectiveTo);
        }
        return lessonRepository.findForSchedule(normalizedGroupCode, instructorId, effectiveFrom, effectiveTo);
    }

    @Transactional
    public LessonDto create(LessonDto dto, Authentication authentication) {
        User actor = userService.getCurrentUser(authentication);
        ensureLessonEditAccess(authentication);

        Day day = resolveDay(dto.getDayId());
        ensureDayCapacity(day, null);
        Lesson lesson = LessonMapper.toEntity(dto);
        lesson.setDay(day);
        applyFullEdit(lesson, dto);
        Lesson saved = lessonRepository.save(lesson);
        auditService.logLessonChange(ChangeAction.CREATED, null, snapshot(saved), actor.getUsername(), "Lesson created");
        log.info("Lesson created: lessonId={}, dayId={}, actor={}, instructors={}",
                saved.getId(), saved.getDay().getId(), actor.getUsername(), saved.getAssignedInstructors().size());
        return LessonMapper.toDto(saved);
    }

    @Transactional
    public LessonDto update(UUID id, LessonDto dto, Authentication authentication) {
        User actor = userService.getCurrentUser(authentication);
        Lesson lesson = findEntity(id);
        Lesson before = snapshot(lesson);

        if (dto.getVersion() == null || !dto.getVersion().equals(lesson.getVersion())) {
            throw new ConflictException("Lesson was changed by another user. Refresh data and retry.");
        }

        ensureLessonEditAccess(authentication);

        if (dto.getDayId() != null && !dto.getDayId().equals(lesson.getDay().getId())) {
            Day targetDay = resolveDay(dto.getDayId());
            ensureDayCapacity(targetDay, lesson);
            lesson.setDay(targetDay);
        } else {
            ensureDayCapacity(lesson.getDay(), lesson);
        }
        applyFullEdit(lesson, dto);

        Lesson saved = lessonRepository.save(lesson);
        auditService.logLessonChange(ChangeAction.UPDATED, before, snapshot(saved), actor.getUsername(), "Lesson updated");
        log.info("Lesson updated: lessonId={}, actor={}, version={}",
                saved.getId(), actor.getUsername(), saved.getVersion());
        return LessonMapper.toDto(saved);
    }

    @Transactional
    public GroupDto syncDay(DaySyncRequestDto dto, Authentication authentication) {
        User actor = userService.getCurrentUser(authentication);
        ensureLessonEditAccess(authentication);

        Group group = resolveGroup(dto.getGroupId());
        LocalDate date = resolveSyncDate(dto.getDate());
        Map<Integer, LessonDto> requestedByOrder = normalizeSyncLessons(dto.getLessons());
        boolean ensureDay = Boolean.TRUE.equals(dto.getEnsureDay())
                || requestedByOrder.values().stream().anyMatch(this::hasLessonContent);

        Day day = dayRepository.findByGroupIdAndDate(group.getId(), date).orElse(null);
        if (day == null && !ensureDay) {
            return GroupMapper.toDto(group);
        }

        if (day == null) {
            day = new Day();
            day.setDate(date);
            day.setMeta(new HashMap<>());
            day.setGroup(group);
            day.setLessons(new ArrayList<>());
            day = dayRepository.save(day);
            group.getDays().add(day);
            group.getDays().sort(Comparator.comparing(Day::getDate, Comparator.nullsLast(LocalDate::compareTo)));
        }

        Map<Integer, Lesson> existingByOrder = day.getLessons().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Lesson::getOrderNumber,
                        lesson -> lesson,
                        (left, right) -> left.getId().compareTo(right.getId()) <= 0 ? left : right,
                        LinkedHashMap::new
                ));

        for (Map.Entry<Integer, LessonDto> entry : requestedByOrder.entrySet()) {
            Lesson existing = existingByOrder.get(entry.getKey());
            LessonDto requested = entry.getValue();
            if (existing != null && !hasLessonContent(requested)) {
                validateSyncVersion(existing, requested);
                Lesson before = snapshot(existing);
                day.getLessons().remove(existing);
                lessonRepository.delete(existing);
                auditService.logLessonChange(ChangeAction.DELETED, before, null, actor.getUsername(), "Lesson deleted");
            }
        }

        for (Map.Entry<Integer, LessonDto> entry : requestedByOrder.entrySet()) {
            LessonDto requested = entry.getValue();
            if (!hasLessonContent(requested)) {
                continue;
            }

            Lesson existing = existingByOrder.get(entry.getKey());
            if (existing == null) {
                Lesson created = LessonMapper.toEntity(requested);
                created.setDay(day);
                applyFullEdit(created, requested);
                Lesson saved = lessonRepository.save(created);
                day.getLessons().add(saved);
                auditService.logLessonChange(ChangeAction.CREATED, null, snapshot(saved), actor.getUsername(), "Lesson created");
                continue;
            }

            validateSyncVersion(existing, requested);
            if (!lessonMatchesDraft(existing, requested)) {
                Lesson before = snapshot(existing);
                applyFullEdit(existing, requested);
                Lesson saved = lessonRepository.save(existing);
                auditService.logLessonChange(ChangeAction.UPDATED, before, snapshot(saved), actor.getUsername(), "Lesson updated");
            }
        }

        day.getLessons().sort(Comparator.comparing(Lesson::getOrderNumber).thenComparing(Lesson::getId));
        return GroupMapper.toDto(group);
    }

    @Transactional
    public void delete(UUID id, Long version, Authentication authentication) {
        User actor = userService.getCurrentUser(authentication);
        ensureLessonEditAccess(authentication);

        Lesson lesson = findEntity(id);
        if (version == null || !version.equals(lesson.getVersion())) {
            throw new ConflictException("Lesson was changed by another user. Refresh data and retry.");
        }

        Lesson before = snapshot(lesson);
        lessonRepository.delete(lesson);
        auditService.logLessonChange(ChangeAction.DELETED, before, null, actor.getUsername(), "Lesson deleted");
        log.info("Lesson deleted: lessonId={}, actor={}", id, actor.getUsername());
    }

    public List<ChangeLogDto> getHistory(UUID lessonId) {
        return auditService.getLessonHistory(lessonId);
    }

    public List<WorkloadDto> getWorkload(UUID instructorId, LocalDate from, LocalDate to, Authentication authentication) {
        User actor = userService.getCurrentUser(authentication);
        if (actor.getRole() == Role.INSTRUCTOR && instructorId != null && !instructorId.equals(actor.getId())) {
            throw new ForbiddenEditException("Instructor can view only own workload");
        }

        UUID effectiveInstructorId = instructorId != null ? instructorId : (actor.getRole() == Role.INSTRUCTOR ? actor.getId() : null);
        LocalDate effectiveFrom = normalizeFrom(from);
        LocalDate effectiveTo = normalizeTo(to);

        Map<UUID, WorkloadDto> totals = new LinkedHashMap<>();
        for (Lesson lesson : lessonRepository.findForSchedule(null, effectiveInstructorId, effectiveFrom, effectiveTo)) {
            for (User instructor : lesson.getAssignedInstructors()) {
                if (effectiveInstructorId != null && !effectiveInstructorId.equals(instructor.getId())) {
                    continue;
                }

                WorkloadDto workload = totals.computeIfAbsent(instructor.getId(), id -> WorkloadDto.builder()
                        .instructorId(instructor.getId())
                        .instructorName(instructor.getFullName())
                        .totalHours(0)
                        .build());
                workload.setTotalHours(workload.getTotalHours() + lesson.getDurationHours());
            }
        }

        List<WorkloadDto> workload = new ArrayList<>(totals.values());
        log.info("Workload calculated: requestedInstructorId={}, effectiveInstructorId={}, from={}, to={}, rows={}",
                instructorId, effectiveInstructorId, effectiveFrom, effectiveTo, workload.size());
        return workload;
    }

    public byte[] exportWorkloadExcel(UUID instructorId,
                                      String instructorQuery,
                                      LocalDate from,
                                      LocalDate to,
                                      Authentication authentication) {
        User actor = userService.getCurrentUser(authentication);
        if (actor.getRole() != Role.ADMIN) {
            throw new ForbiddenEditException("Only admin can export workload catalog");
        }

        LocalDate effectiveFrom = normalizeFrom(from);
        LocalDate effectiveTo = normalizeTo(to);
        String normalizedQuery = instructorQuery == null ? null : instructorQuery.trim().toLowerCase();
        List<Lesson> lessons = lessonRepository.findForDateRange(effectiveFrom, effectiveTo).stream()
                .sorted(Comparator.comparing((Lesson lesson) -> lesson.getDay().getDate())
                        .thenComparing(Lesson::getOrderNumber)
                        .thenComparing(Lesson::getId))
                .toList();

        List<WorkloadCalendarDto> calendars = buildWorkloadCalendars(lessons, instructorId, normalizedQuery);
        log.info("Workload export built: requestedInstructorId={}, instructorQuery={}, from={}, to={}, rows={}",
                instructorId, instructorQuery, effectiveFrom, effectiveTo, calendars.size());
        return workloadExcelExportService.exportCalendars(calendars, effectiveFrom, effectiveTo);
    }

    public Lesson findEntity(UUID id) {
        return lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + id));
    }

    private ScheduleEntryDto toScheduleEntry(Lesson lesson) {
        LessonDto dto = LessonMapper.toDto(lesson);
        return ScheduleEntryDto.builder()
                .lessonId(dto.getId())
                .version(dto.getVersion())
                .groupId(dto.getGroupId())
                .groupCode(lesson.getDay().getGroup().getCode())
                .location(lesson.getDay().getGroup().getLocation())
                .date(lesson.getDay().getDate())
                .orderNumber(dto.getOrderNumber())
                .title(dto.getTitle())
                .type(dto.getType())
                .durationHours(dto.getDurationHours())
                .note(dto.getNote())
                .instructorIds(dto.getInstructorIds())
                .instructorNames(dto.getInstructorNames())
                .build();
    }

    private String normalizeGroupCode(String groupCode) {
        if (groupCode == null) {
            return null;
        }

        String trimmed = groupCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate normalizeFrom(LocalDate from) {
        return from != null ? from : MIN_FILTER_DATE;
    }

    private LocalDate normalizeTo(LocalDate to) {
        return to != null ? to : MAX_FILTER_DATE;
    }

    private void applyFullEdit(Lesson lesson, LessonDto dto) {
        lesson.setOrderNumber(dto.getOrderNumber() != null ? dto.getOrderNumber() : 0);
        lesson.setTitle(dto.getTitle() != null ? dto.getTitle().trim() : null);
        lesson.setDurationHours(dto.getDurationHours() != null ? dto.getDurationHours() : 0);
        lesson.setNote(normalizeNote(dto.getNote()));
        lesson.setType(dto.getType() != null ? dto.getType() : lesson.getType());
        List<User> instructors = resolveAssignableInstructors(dto.getInstructorIds());
        List<String> lecturerNames = resolveLecturerNames(instructors);
        lesson.setAssignedInstructors(new ArrayList<>(instructors));
        lesson.setLecturers(new ArrayList<>(lecturerNames));
        lesson.setLecturer(lecturerNames.isEmpty() ? null : lecturerNames.get(0));
    }

    private void ensureLessonEditAccess(Authentication authentication) {
        boolean canEdit = authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority) || "ROLE_EDITOR".equals(authority));
        if (!canEdit) {
            throw new ForbiddenEditException("Lesson editing requires ADMIN or EDITOR access");
        }
    }

    private boolean lessonMatchesDraft(Lesson lesson, LessonDto dto) {
        return lesson.getOrderNumber() == (dto.getOrderNumber() != null ? dto.getOrderNumber() : 0)
                && lesson.getTitle().equals(dto.getTitle().trim())
                && lesson.getDurationHours() == (dto.getDurationHours() != null ? dto.getDurationHours() : 0)
                && java.util.Objects.equals(lesson.getNote(), normalizeNote(dto.getNote()))
                && java.util.Objects.equals(lesson.getType(), dto.getType())
                && areUuidListsEqual(extractAssignedInstructorIds(lesson), dto.getInstructorIds() != null ? dto.getInstructorIds() : List.of());
    }

    private boolean areUuidListsEqual(List<UUID> left, List<UUID> right) {
        if (left.size() != right.size()) {
            return false;
        }
        List<UUID> normalizedLeft = new ArrayList<>(left);
        List<UUID> normalizedRight = new ArrayList<>(right);
        normalizedLeft.sort(UUID::compareTo);
        normalizedRight.sort(UUID::compareTo);
        return normalizedLeft.equals(normalizedRight);
    }

    private List<UUID> extractAssignedInstructorIds(Lesson lesson) {
        if (lesson.getAssignedInstructors() == null) {
            return List.of();
        }
        return lesson.getAssignedInstructors().stream()
                .map(User::getId)
                .toList();
    }

    private Map<Integer, LessonDto> normalizeSyncLessons(List<LessonDto> lessons) {
        Map<Integer, LessonDto> requestedByOrder = new LinkedHashMap<>();
        if (lessons == null) {
            return requestedByOrder;
        }

        for (LessonDto lesson : lessons) {
            Integer order = lesson.getOrderNumber();
            if (order == null || order < 1 || order > MAX_LESSONS_PER_DAY) {
                throw new ConflictException("Day sync accepts slots from 1 to 8 only");
            }
            if (requestedByOrder.putIfAbsent(order, lesson) != null) {
                throw new ConflictException("Day sync contains duplicate slot numbers");
            }
        }

        return requestedByOrder;
    }

    private boolean hasLessonContent(LessonDto dto) {
        return dto != null && dto.getTitle() != null && !dto.getTitle().trim().isBlank();
    }

    private void validateSyncVersion(Lesson existing, LessonDto requested) {
        if (requested == null) {
            throw new ConflictException("Day sync requires slot payload for existing lessons");
        }
        if (requested.getId() != null && !requested.getId().equals(existing.getId())) {
            throw new ConflictException("Lesson slot points to another lesson. Refresh data and retry.");
        }
        if (requested.getVersion() == null || !requested.getVersion().equals(existing.getVersion())) {
            throw new ConflictException("Lesson was changed by another user. Refresh data and retry.");
        }
    }

    private Group resolveGroup(UUID groupId) {
        if (groupId == null) {
            throw new ResourceNotFoundException("groupId is required");
        }
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
    }

    private LocalDate resolveSyncDate(LocalDate date) {
        if (date == null) {
            throw new ResourceNotFoundException("date is required");
        }
        return date;
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private void ensureDayCapacity(Day day, Lesson currentLesson) {
        int existingLessons = day.getLessons() != null ? day.getLessons().size() : 0;
        if (currentLesson != null && day.getLessons() != null && day.getLessons().stream().anyMatch(lesson -> lesson.getId() != null && lesson.getId().equals(currentLesson.getId()))) {
            return;
        }
        if (existingLessons >= MAX_LESSONS_PER_DAY) {
            throw new ConflictException("A day can contain at most 8 lessons");
        }
    }

    private List<User> resolveAssignableInstructors(List<UUID> instructorIds) {
        if (instructorIds == null) {
            return new ArrayList<>();
        }

        List<User> instructors = instructorIds.stream()
                .distinct()
                .map(userService::findById)
                .toList();

        User invalidAssignee = instructors.stream()
                .filter(user -> !user.isCanTeach())
                .findFirst()
                .orElse(null);
        if (invalidAssignee != null) {
            throw new ForbiddenEditException("Only users with canTeach=true can be assigned to lessons");
        }

        return instructors;
    }

    private List<String> resolveLecturerNames(List<User> instructors) {
        return instructors.stream()
                .map(User::getFullName)
                .filter(fullName -> fullName != null && !fullName.isBlank())
                .map(String::trim)
                .collect(
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                ArrayList::new
                        )
                );
    }

    private List<WorkloadCalendarDto> buildWorkloadCalendars(List<Lesson> lessons,
                                                             UUID instructorId,
                                                             String instructorQuery) {
        Map<UUID, WorkloadCalendarDto> calendars = new LinkedHashMap<>();

        for (Lesson lesson : lessons) {
            for (User instructor : lesson.getAssignedInstructors()) {
                if (instructorId != null && !instructorId.equals(instructor.getId())) {
                    continue;
                }
                if (instructorQuery != null && !instructorQuery.isBlank()) {
                    String fullName = instructor.getFullName() == null ? "" : instructor.getFullName().toLowerCase();
                    if (!fullName.contains(instructorQuery)) {
                        continue;
                    }
                }

                WorkloadCalendarDto calendar = calendars.computeIfAbsent(instructor.getId(), ignored -> WorkloadCalendarDto.builder()
                        .instructorId(instructor.getId())
                        .instructorName(instructor.getFullName())
                        .from(lesson.getDay().getDate())
                        .to(lesson.getDay().getDate())
                        .totalHours(0)
                        .days(new ArrayList<>())
                        .build());

                calendar.setFrom(calendar.getFrom() == null || lesson.getDay().getDate().isBefore(calendar.getFrom())
                        ? lesson.getDay().getDate()
                        : calendar.getFrom());
                calendar.setTo(calendar.getTo() == null || lesson.getDay().getDate().isAfter(calendar.getTo())
                        ? lesson.getDay().getDate()
                        : calendar.getTo());
                calendar.setTotalHours(calendar.getTotalHours() + lesson.getDurationHours());

                WorkloadCalendarDayDto day = calendar.getDays().stream()
                        .filter(item -> item.getDate().equals(lesson.getDay().getDate()))
                        .findFirst()
                        .orElseGet(() -> {
                            WorkloadCalendarDayDto created = WorkloadCalendarDayDto.builder()
                                    .dayId(lesson.getDay().getId())
                                    .date(lesson.getDay().getDate())
                                    .totalHours(0)
                                    .lessons(new ArrayList<>())
                                    .build();
                            calendar.getDays().add(created);
                            return created;
                        });

                day.setTotalHours(day.getTotalHours() + lesson.getDurationHours());
                day.getLessons().add(WorkloadCalendarLessonDto.builder()
                        .lessonId(lesson.getId())
                        .groupCode(lesson.getDay().getGroup().getCode())
                        .title(lesson.getTitle())
                        .durationHours(lesson.getDurationHours())
                        .build());
            }
        }

        return calendars.values().stream()
                .peek(calendar -> calendar.getDays().sort(Comparator.comparing(WorkloadCalendarDayDto::getDate)))
                .toList();
    }

    private Day resolveDay(UUID dayId) {
        if (dayId == null) {
            throw new ResourceNotFoundException("dayId is required");
        }

        return dayRepository.findById(dayId)
                .orElseThrow(() -> new ResourceNotFoundException("Day not found: " + dayId));
    }

    private Lesson snapshot(Lesson source) {
        Lesson copy = new Lesson();
        copy.setId(source.getId());
        copy.setVersion(source.getVersion());
        copy.setOrderNumber(source.getOrderNumber());
        copy.setTitle(source.getTitle());
        copy.setLecturer(source.getLecturer());
        copy.setLecturers(source.getLecturers() != null ? new ArrayList<>(source.getLecturers()) : new ArrayList<>());
        copy.setDurationHours(source.getDurationHours());
        copy.setNote(source.getNote());
        copy.setType(source.getType());
        copy.setAssignedInstructors(source.getAssignedInstructors() != null ? new ArrayList<>(source.getAssignedInstructors()) : new ArrayList<>());
        copy.setDay(source.getDay());
        return copy;
    }
}
