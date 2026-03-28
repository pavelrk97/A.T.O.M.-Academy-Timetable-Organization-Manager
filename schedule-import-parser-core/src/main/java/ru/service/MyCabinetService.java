package ru.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.InternalUserDetailsDto;
import ru.dto.MyNotificationDto;
import ru.dto.ScheduleGridDayCellDto;
import ru.dto.ScheduleGridDto;
import ru.dto.ScheduleGridGroupRowDto;
import ru.dto.ScheduleGridLessonCellDto;
import ru.dto.WorkloadCalendarDayDto;
import ru.dto.WorkloadCalendarDto;
import ru.dto.WorkloadCalendarLessonDto;
import ru.exception.ResourceNotFoundException;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.NotificationType;
import ru.repository.GroupRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MyCabinetService {

    private final GroupRepository groupRepository;
    private final IdentityDirectoryService identityDirectoryService;

    public MyCabinetService(GroupRepository groupRepository, IdentityDirectoryService identityDirectoryService) {
        this.groupRepository = groupRepository;
        this.identityDirectoryService = identityDirectoryService;
    }

    public ScheduleGridDto getFullScheduleGrid(LocalDate from, LocalDate to) {
        List<Group> groups = groupRepository.findAll();
        List<LocalDate> dates = groups.stream()
                .flatMap(group -> group.getDays().stream())
                .map(Day::getDate)
                .filter(date -> matchesDate(date, from, to))
                .distinct()
                .sorted()
                .toList();

        List<ScheduleGridGroupRowDto> rows = groups.stream()
                .sorted(Comparator.comparing(Group::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(group -> toGroupRow(group, dates, null))
                .filter(row -> row.getDays().stream().anyMatch(day -> !day.getLessons().isEmpty()))
                .toList();

        return ScheduleGridDto.builder()
                .dates(dates)
                .groups(rows)
                .build();
    }

    public ScheduleGridDto getInstructorScheduleGrid(Authentication authentication, LocalDate from, LocalDate to) {
        InternalUserDetailsDto currentUser = currentUser(authentication);
        String instructorName = currentUser.getFullName();

        List<Group> groups = groupRepository.findAll();
        List<LocalDate> dates = groups.stream()
                .flatMap(group -> group.getDays().stream())
                .filter(day -> matchesDate(day.getDate(), from, to))
                .filter(day -> day.getLessons().stream().anyMatch(lesson -> matchesInstructor(lesson, instructorName)))
                .map(Day::getDate)
                .distinct()
                .sorted()
                .toList();

        List<ScheduleGridGroupRowDto> rows = groups.stream()
                .sorted(Comparator.comparing(Group::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(group -> toGroupRow(group, dates, instructorName))
                .filter(row -> row.getDays().stream().anyMatch(day -> !day.getLessons().isEmpty()))
                .toList();

        return ScheduleGridDto.builder()
                .dates(dates)
                .groups(rows)
                .build();
    }

    public WorkloadCalendarDto getMyWorkloadCalendar(Authentication authentication, LocalDate from, LocalDate to) {
        InternalUserDetailsDto currentUser = currentUser(authentication);
        String instructorName = currentUser.getFullName();

        Map<LocalDate, WorkloadCalendarDayDto> totalsByDay = new LinkedHashMap<>();
        int totalHours = 0;

        for (Group group : groupRepository.findAll()) {
            for (Day day : group.getDays()) {
                if (!matchesDate(day.getDate(), from, to)) {
                    continue;
                }

                for (Lesson lesson : day.getLessons()) {
                    if (!matchesInstructor(lesson, instructorName)) {
                        continue;
                    }

                    WorkloadCalendarDayDto dayDto = totalsByDay.computeIfAbsent(day.getDate(), ignored -> WorkloadCalendarDayDto.builder()
                            .dayId(day.getId())
                            .date(day.getDate())
                            .totalHours(0)
                            .lessons(new ArrayList<>())
                            .build());

                    dayDto.setTotalHours(dayDto.getTotalHours() + lesson.getDurationHours());
                    dayDto.getLessons().add(WorkloadCalendarLessonDto.builder()
                            .lessonId(lesson.getId())
                            .groupCode(group.getCode())
                            .title(lesson.getTitle())
                            .durationHours(lesson.getDurationHours())
                            .build());
                    totalHours += lesson.getDurationHours();
                }
            }
        }

        return WorkloadCalendarDto.builder()
                .instructorId(currentUser.getId())
                .instructorName(instructorName)
                .from(from)
                .to(to)
                .totalHours(totalHours)
                .days(totalsByDay.values().stream()
                        .sorted(Comparator.comparing(WorkloadCalendarDayDto::getDate))
                        .toList())
                .build();
    }

    public List<MyNotificationDto> getMyNotifications(Authentication authentication, LocalDate from, LocalDate to) {
        InternalUserDetailsDto currentUser = currentUser(authentication);
        String instructorName = currentUser.getFullName();

        List<MyNotificationDto> notifications = new ArrayList<>();
        for (Group group : groupRepository.findAll()) {
            for (Day day : group.getDays()) {
                if (!matchesDate(day.getDate(), from, to)) {
                    continue;
                }

                List<Lesson> lessons = day.getLessons().stream()
                        .filter(lesson -> matchesInstructor(lesson, instructorName))
                        .sorted(Comparator.comparing(Lesson::getOrderNumber))
                        .toList();

                if (lessons.isEmpty()) {
                    continue;
                }

                notifications.add(MyNotificationDto.builder()
                        .type(NotificationType.LESSON_ADDED)
                        .dayId(day.getId())
                        .date(day.getDate())
                        .message("На " + day.getDate() + " есть занятия у группы " + group.getCode())
                        .link("/api/me/schedule/instructor-grid?from=" + day.getDate() + "&to=" + day.getDate())
                        .build());
            }
        }

        return notifications.stream()
                .sorted(Comparator.comparing(MyNotificationDto::getDate))
                .toList();
    }

    private ScheduleGridGroupRowDto toGroupRow(Group group, List<LocalDate> dates, String instructorName) {
        Map<LocalDate, Day> daysByDate = group.getDays().stream()
                .collect(Collectors.toMap(Day::getDate, day -> day, (left, right) -> left, LinkedHashMap::new));

        List<ScheduleGridDayCellDto> dayCells = dates.stream()
                .map(date -> {
                    Day day = daysByDate.get(date);
                    if (day == null) {
                        return ScheduleGridDayCellDto.builder()
                                .dayId(null)
                                .date(date)
                                .lessons(new ArrayList<>())
                                .build();
                    }

                    List<ScheduleGridLessonCellDto> lessons = day.getLessons().stream()
                            .filter(lesson -> instructorName == null || matchesInstructor(lesson, instructorName))
                            .sorted(Comparator.comparing(Lesson::getOrderNumber))
                            .map(this::toLessonCell)
                            .toList();

                    return ScheduleGridDayCellDto.builder()
                            .dayId(day.getId())
                            .date(date)
                            .lessons(lessons)
                            .build();
                })
                .toList();

        return ScheduleGridGroupRowDto.builder()
                .groupId(group.getId())
                .groupCode(group.getCode())
                .location(group.getLocation())
                .course(group.getCourse())
                .days(dayCells)
                .build();
    }

    private ScheduleGridLessonCellDto toLessonCell(Lesson lesson) {
        return ScheduleGridLessonCellDto.builder()
                .lessonId(lesson.getId())
                .version(lesson.getVersion())
                .orderNumber(lesson.getOrderNumber())
                .title(lesson.getTitle())
                .type(lesson.getType())
                .durationHours(lesson.getDurationHours())
                .note(lesson.getNote())
                .instructorNames(resolveInstructorNames(lesson))
                .build();
    }

    private List<String> resolveInstructorNames(Lesson lesson) {
        if (lesson.getAssignedInstructors() != null && !lesson.getAssignedInstructors().isEmpty()) {
            return lesson.getAssignedInstructors().stream()
                    .map(user -> user.getFullName())
                    .toList();
        }

        if (lesson.getLecturers() != null && !lesson.getLecturers().isEmpty()) {
            return lesson.getLecturers();
        }

        if (lesson.getLecturer() != null && !lesson.getLecturer().isBlank()) {
            return List.of(lesson.getLecturer());
        }

        return new ArrayList<>();
    }

    private boolean matchesInstructor(Lesson lesson, String instructorName) {
        if (instructorName == null || instructorName.isBlank()) {
            return false;
        }

        return resolveInstructorNames(lesson).stream()
                .anyMatch(name -> instructorName.equalsIgnoreCase(name));
    }

    private boolean matchesDate(LocalDate date, LocalDate from, LocalDate to) {
        boolean fromOk = from == null || !date.isBefore(from);
        boolean toOk = to == null || !date.isAfter(to);
        return fromOk && toOk;
    }

    private InternalUserDetailsDto currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }

        return identityDirectoryService.getByUsername(authentication.getName());
    }
}
