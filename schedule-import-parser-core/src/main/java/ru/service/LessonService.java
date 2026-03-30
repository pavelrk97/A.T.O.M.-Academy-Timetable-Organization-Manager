package ru.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import ru.dto.ChangeLogDto;
import ru.dto.LessonDto;
import ru.dto.ScheduleEntryDto;
import ru.dto.WorkloadDto;
import ru.exception.ConflictException;
import ru.exception.ForbiddenEditException;
import ru.exception.ResourceNotFoundException;
import ru.mapper.LessonMapper;
import ru.model.ChangeAction;
import ru.model.Day;
import ru.model.Lesson;
import ru.model.Role;
import ru.model.User;
import ru.repository.DayRepository;
import ru.repository.LessonRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LessonService {

    private static final Logger log = LoggerFactory.getLogger(LessonService.class);
    private static final LocalDate MIN_FILTER_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_FILTER_DATE = LocalDate.of(3000, 12, 31);

    private final LessonRepository lessonRepository;
    private final DayRepository dayRepository;
    private final UserService userService;
    private final AuditService auditService;

    public LessonService(LessonRepository lessonRepository,
                         DayRepository dayRepository,
                         UserService userService,
                         AuditService auditService) {
        this.lessonRepository = lessonRepository;
        this.dayRepository = dayRepository;
        this.userService = userService;
        this.auditService = auditService;
    }

    public LessonDto getById(UUID id) {
        return LessonMapper.toDto(findEntity(id));
    }

    public List<ScheduleEntryDto> getSchedule(String groupCode, UUID instructorId, LocalDate from, LocalDate to) {
        String normalizedGroupCode = normalizeGroupCode(groupCode);
        LocalDate effectiveFrom = normalizeFrom(from);
        LocalDate effectiveTo = normalizeTo(to);
        List<ScheduleEntryDto> schedule = lessonRepository.findForSchedule(normalizedGroupCode, instructorId, effectiveFrom, effectiveTo).stream()
                .sorted(Comparator.comparing((Lesson lesson) -> lesson.getDay().getDate())
                        .thenComparing(Lesson::getOrderNumber)
                        .thenComparing(Lesson::getId))
                .map(this::toScheduleEntry)
                .toList();
        log.info("Schedule loaded: groupCode={}, instructorId={}, from={}, to={}, entries={}",
                normalizedGroupCode, instructorId, effectiveFrom, effectiveTo, schedule.size());
        return schedule;
    }

    @Transactional
    public LessonDto create(LessonDto dto, Authentication authentication) {
        User actor = userService.getCurrentUser(authentication);
        if (actor.getRole() == Role.INSTRUCTOR) {
            throw new ForbiddenEditException("Instructor cannot create lessons");
        }

        Day day = resolveDay(dto.getDayId());
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

        if (actor.getRole() == Role.INSTRUCTOR) {
            throw new ForbiddenEditException("Instructor cannot edit lessons");
        }

        if (dto.getDayId() != null && !dto.getDayId().equals(lesson.getDay().getId())) {
            lesson.setDay(resolveDay(dto.getDayId()));
        }
        applyFullEdit(lesson, dto);

        Lesson saved = lessonRepository.save(lesson);
        auditService.logLessonChange(ChangeAction.UPDATED, before, snapshot(saved), actor.getUsername(), "Lesson updated");
        log.info("Lesson updated: lessonId={}, actor={}, version={}",
                saved.getId(), actor.getUsername(), saved.getVersion());
        return LessonMapper.toDto(saved);
    }

    @Transactional
    public void delete(UUID id, Long version, Authentication authentication) {
        User actor = userService.getCurrentUser(authentication);
        if (actor.getRole() == Role.INSTRUCTOR) {
            throw new ForbiddenEditException("Instructor cannot delete lessons");
        }

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
        lesson.setTitle(dto.getTitle());
        lesson.setDurationHours(dto.getDurationHours() != null ? dto.getDurationHours() : 0);
        lesson.setNote(dto.getNote());
        lesson.setType(dto.getType());
        List<User> instructors = dto.getInstructorIds() != null
                ? dto.getInstructorIds().stream().map(userService::findById).toList()
                : new ArrayList<>();
        lesson.setAssignedInstructors(new ArrayList<>(instructors));
        lesson.setLecturers(new ArrayList<>(instructors.stream().map(User::getFullName).toList()));
        lesson.setLecturer(instructors.isEmpty() ? null : instructors.get(0).getFullName());
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
