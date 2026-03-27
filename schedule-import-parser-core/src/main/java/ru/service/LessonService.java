package ru.service;

import jakarta.transaction.Transactional;
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
        return lessonRepository.findAll().stream()
                .filter(lesson -> matches(lesson, groupCode, instructorId, from, to))
                .sorted(Comparator.comparing((Lesson lesson) -> lesson.getDay().getDate())
                        .thenComparing(Lesson::getOrderNumber))
                .map(this::toScheduleEntry)
                .toList();
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

        Map<UUID, WorkloadDto> totals = new LinkedHashMap<>();
        for (Lesson lesson : lessonRepository.findAll()) {
            if (!matches(lesson, null, effectiveInstructorId, from, to)) {
                continue;
            }

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

        return new ArrayList<>(totals.values());
    }

    public Lesson findEntity(UUID id) {
        return lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + id));
    }

    private boolean matches(Lesson lesson, String groupCode, UUID instructorId, LocalDate from, LocalDate to) {
        boolean groupMatches = groupCode == null || groupCode.isBlank() || groupCode.equalsIgnoreCase(lesson.getDay().getGroup().getCode());
        boolean dateFromMatches = from == null || !lesson.getDay().getDate().isBefore(from);
        boolean dateToMatches = to == null || !lesson.getDay().getDate().isAfter(to);
        boolean instructorMatches = instructorId == null || lesson.getAssignedInstructors().stream().anyMatch(user -> user.getId().equals(instructorId));
        return groupMatches && dateFromMatches && dateToMatches && instructorMatches;
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
        lesson.setLecturers(instructors.stream().map(User::getFullName).toList());
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
