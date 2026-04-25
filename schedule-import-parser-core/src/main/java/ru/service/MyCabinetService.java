package ru.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dto.InternalUserDetailsDto;
import ru.dto.MyDashboardDataDto;
import ru.dto.MyNotificationDto;
import ru.dto.ScheduleGridDayCellDto;
import ru.dto.ScheduleGridDto;
import ru.dto.ScheduleGridGroupRowDto;
import ru.dto.ScheduleGridLessonCellDto;
import ru.dto.WorkloadCalendarDayDto;
import ru.dto.WorkloadCalendarDto;
import ru.dto.WorkloadCalendarLessonDto;
import ru.exception.ResourceNotFoundException;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.NotificationType;
import ru.repository.LessonRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MyCabinetService {

    private static final Logger log = LoggerFactory.getLogger(MyCabinetService.class);
    private static final LocalDate MIN_FILTER_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_FILTER_DATE = LocalDate.of(3000, 12, 31);

    private final LessonRepository lessonRepository;
    private final IdentityDirectoryService identityDirectoryService;
    private final WorkloadExcelExportService workloadExcelExportService;

    public MyCabinetService(LessonRepository lessonRepository,
                            IdentityDirectoryService identityDirectoryService,
                            WorkloadExcelExportService workloadExcelExportService) {
        this.lessonRepository = lessonRepository;
        this.identityDirectoryService = identityDirectoryService;
        this.workloadExcelExportService = workloadExcelExportService;
    }

    public ScheduleGridDto getFullScheduleGrid(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = normalizeFrom(from);
        LocalDate effectiveTo = normalizeTo(to);
        ScheduleGridDto grid = buildGrid(lessonRepository.findForDateRange(effectiveFrom, effectiveTo));
        log.info("Full schedule grid built: from={}, to={}, groups={}, dates={}",
                effectiveFrom, effectiveTo, grid.getGroups().size(), grid.getDates().size());
        return grid;
    }

    public ScheduleGridDto getInstructorScheduleGrid(Authentication authentication, LocalDate from, LocalDate to) {
        DashboardSeed seed = buildDashboardSeed(authentication, from, to);
        ScheduleGridDto grid = buildGrid(seed.lessons());
        log.info("Instructor schedule grid built: user={}, from={}, to={}, lessons={}, groups={}",
                seed.currentUser().getUsername(), seed.from(), seed.to(), seed.lessons().size(), grid.getGroups().size());
        return grid;
    }

    public WorkloadCalendarDto getMyWorkloadCalendar(Authentication authentication, LocalDate from, LocalDate to) {
        DashboardSeed seed = buildDashboardSeed(authentication, from, to);
        WorkloadCalendarDto workload = buildWorkload(seed.currentUser(), seed.lessons(), seed.from(), seed.to());
        log.info("Workload calendar built: user={}, from={}, to={}, totalHours={}, days={}",
                seed.currentUser().getUsername(), seed.from(), seed.to(), workload.getTotalHours(), workload.getDays().size());
        return workload;
    }

    public byte[] exportMyWorkloadExcel(Authentication authentication, LocalDate from, LocalDate to) {
        DashboardSeed seed = buildDashboardSeed(authentication, from, to);
        WorkloadCalendarDto workload = buildWorkload(seed.currentUser(), seed.lessons(), seed.from(), seed.to());
        log.info("Own workload export built: user={}, from={}, to={}, totalHours={}, days={}",
                seed.currentUser().getUsername(), seed.from(), seed.to(), workload.getTotalHours(), workload.getDays().size());
        return workloadExcelExportService.exportCalendars(List.of(workload), seed.from(), seed.to());
    }

    public List<MyNotificationDto> getMyNotifications(Authentication authentication, LocalDate from, LocalDate to) {
        DashboardSeed seed = buildDashboardSeed(authentication, from, to);
        List<MyNotificationDto> notifications = buildNotifications(seed.lessons());
        log.info("Notifications built: user={}, from={}, to={}, notifications={}",
                seed.currentUser().getUsername(), seed.from(), seed.to(), notifications.size());
        return notifications;
    }

    public MyDashboardDataDto getDashboard(Authentication authentication, LocalDate from, LocalDate to) {
        DashboardSeed seed = buildDashboardSeed(authentication, from, to);
        MyDashboardDataDto dashboard = MyDashboardDataDto.builder()
                .instructorSchedule(buildGrid(seed.lessons()))
                .workload(buildWorkload(seed.currentUser(), seed.lessons(), seed.from(), seed.to()))
                .notifications(buildNotifications(seed.lessons()))
                .build();
        log.info("Dashboard built: user={}, from={}, to={}, lessons={}, notifications={}, workloadHours={}",
                seed.currentUser().getUsername(),
                seed.from(),
                seed.to(),
                seed.lessons().size(),
                dashboard.getNotifications().size(),
                dashboard.getWorkload().getTotalHours());
        return dashboard;
    }

    private List<MyNotificationDto> buildNotifications(List<Lesson> lessons) {
        Map<UUID, NotificationSeed> notifications = new LinkedHashMap<>();
        for (Lesson lesson : lessons) {
            UUID dayId = lesson.getDay().getId();
            Group group = lesson.getDay().getGroup();
            LocalDate date = lesson.getDay().getDate();

            notifications.computeIfAbsent(dayId, ignored -> new NotificationSeed(dayId, date, group.getCode()));
        }

        return notifications.values().stream()
                .map(seed -> MyNotificationDto.builder()
                        .type(NotificationType.LESSON_ADDED)
                        .dayId(seed.dayId())
                        .date(seed.date())
                        .message("На " + seed.date() + " есть занятия у группы " + seed.groupCode())
                        .link("/cabinet?tab=schedule&from=" + seed.date() + "&to=" + seed.date() + "&focusDate=" + seed.date())
                        .build())
                .sorted(Comparator.comparing(MyNotificationDto::getDate))
                .toList();
    }

    private WorkloadCalendarDto buildWorkload(InternalUserDetailsDto currentUser, List<Lesson> lessons, LocalDate from, LocalDate to) {
        Map<LocalDate, WorkloadCalendarDayDto> totalsByDay = new LinkedHashMap<>();
        int totalHours = 0;

        for (Lesson lesson : lessons) {
            WorkloadCalendarDayDto dayDto = totalsByDay.computeIfAbsent(lesson.getDay().getDate(), ignored -> WorkloadCalendarDayDto.builder()
                    .dayId(lesson.getDay().getId())
                    .date(lesson.getDay().getDate())
                    .totalHours(0)
                    .lessons(new ArrayList<>())
                    .build());

            dayDto.setTotalHours(dayDto.getTotalHours() + lesson.getDurationHours());
            dayDto.getLessons().add(WorkloadCalendarLessonDto.builder()
                    .lessonId(lesson.getId())
                    .groupCode(lesson.getDay().getGroup().getCode())
                    .title(lesson.getTitle())
                    .durationHours(lesson.getDurationHours())
                    .build());
            totalHours += lesson.getDurationHours();
        }

        return WorkloadCalendarDto.builder()
                .instructorId(currentUser.getId())
                .instructorName(currentUser.getFullName())
                .from(from)
                .to(to)
                .totalHours(totalHours)
                .days(totalsByDay.values().stream()
                        .sorted(Comparator.comparing(WorkloadCalendarDayDto::getDate))
                        .toList())
                .build();
    }

    private ScheduleGridDto buildGrid(List<Lesson> lessons) {
        List<Lesson> sortedLessons = lessons.stream()
                .sorted(Comparator.comparing((Lesson lesson) -> lesson.getDay().getGroup().getCode(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(lesson -> lesson.getDay().getDate())
                        .thenComparing(Lesson::getOrderNumber)
                        .thenComparing(Lesson::getId))
                .toList();

        List<LocalDate> dates = sortedLessons.stream()
                .map(lesson -> lesson.getDay().getDate())
                .distinct()
                .sorted()
                .toList();

        Map<UUID, Group> groupsById = new LinkedHashMap<>();
        Map<UUID, Map<LocalDate, ScheduleGridDayCellDto>> cellsByGroup = new LinkedHashMap<>();

        for (Lesson lesson : sortedLessons) {
            Group group = lesson.getDay().getGroup();
            groupsById.putIfAbsent(group.getId(), group);

            Map<LocalDate, ScheduleGridDayCellDto> dayCells = cellsByGroup.computeIfAbsent(group.getId(), ignored -> new LinkedHashMap<>());
            ScheduleGridDayCellDto dayCell = dayCells.computeIfAbsent(lesson.getDay().getDate(), ignored -> ScheduleGridDayCellDto.builder()
                    .dayId(lesson.getDay().getId())
                    .date(lesson.getDay().getDate())
                    .lessons(new ArrayList<>())
                    .build());
            dayCell.getLessons().add(toLessonCell(lesson));
        }

        List<ScheduleGridGroupRowDto> rows = groupsById.values().stream()
                .sorted(Comparator.comparing(Group::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(group -> {
                    Map<LocalDate, ScheduleGridDayCellDto> dayCells = cellsByGroup.getOrDefault(group.getId(), Map.of());
                    List<ScheduleGridDayCellDto> orderedDays = dates.stream()
                            .map(date -> {
                                ScheduleGridDayCellDto existing = dayCells.get(date);
                                if (existing != null) {
                                    existing.getLessons().sort(Comparator.comparing(ScheduleGridLessonCellDto::getOrderNumber, Comparator.nullsLast(Integer::compareTo)));
                                    return existing;
                                }

                                return ScheduleGridDayCellDto.builder()
                                        .dayId(null)
                                        .date(date)
                                        .lessons(new ArrayList<>())
                                        .build();
                            })
                            .toList();

                    return ScheduleGridGroupRowDto.builder()
                            .groupId(group.getId())
                            .groupCode(group.getCode())
                            .location(group.getLocation())
                            .course(group.getCourse())
                            .days(orderedDays)
                            .build();
                })
                .toList();

        return ScheduleGridDto.builder()
                .dates(dates)
                .groups(rows)
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

        if (lesson.getLecturer() != null && !lesson.getLecturer().isBlank()) {
            return List.of(lesson.getLecturer());
        }

        if (lesson.getLecturers() != null && !lesson.getLecturers().isEmpty()) {
            return lesson.getLecturers();
        }

        return new ArrayList<>();
    }

    private InternalUserDetailsDto currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }

        return identityDirectoryService.getByUsername(authentication.getName());
    }

    private DashboardSeed buildDashboardSeed(Authentication authentication, LocalDate from, LocalDate to) {
        InternalUserDetailsDto currentUser = currentUser(authentication);
        LocalDate effectiveFrom = normalizeFrom(from);
        LocalDate effectiveTo = normalizeTo(to);
        List<Lesson> lessons = lessonRepository.findForInstructorNameAndDateRange(currentUser.getFullName(), effectiveFrom, effectiveTo);
        return new DashboardSeed(currentUser, lessons, effectiveFrom, effectiveTo);
    }

    private LocalDate normalizeFrom(LocalDate from) {
        return from != null ? from : MIN_FILTER_DATE;
    }

    private LocalDate normalizeTo(LocalDate to) {
        return to != null ? to : MAX_FILTER_DATE;
    }

    private record NotificationSeed(UUID dayId, LocalDate date, String groupCode) {
    }

    private record DashboardSeed(InternalUserDetailsDto currentUser, List<Lesson> lessons, LocalDate from, LocalDate to) {
    }
}
