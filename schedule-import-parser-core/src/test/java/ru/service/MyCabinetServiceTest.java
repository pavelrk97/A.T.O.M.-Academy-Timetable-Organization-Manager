package ru.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import ru.dto.InternalUserDetailsDto;
import ru.dto.MyDashboardDataDto;
import ru.dto.MyNotificationDto;
import ru.dto.ScheduleGridDto;
import ru.dto.WorkloadCalendarDto;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.LessonType;
import ru.model.Role;
import ru.model.User;
import ru.repository.LessonRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class MyCabinetServiceTest {

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private IdentityDirectoryService identityDirectoryService;

    @Test
    void instructorGrid_usesRepositoryFilterAndBuildsSpreadsheetRows() {
        MyCabinetService service = new MyCabinetService(lessonRepository, identityDirectoryService);
        Authentication authentication = new UsernamePasswordAuthenticationToken("mentor", "pass");
        InternalUserDetailsDto currentUser = internalUser("mentor", "Mentor QA");

        Lesson firstLesson = lesson("QA-42", "B201", LocalDate.of(2026, 1, 12), 1, "APCS intro", 4, "Mentor QA");
        Lesson secondLesson = lesson("QA-99", "A101", LocalDate.of(2026, 1, 13), 2, "Signals", 2, "Mentor QA");

        when(identityDirectoryService.getByUsername("mentor")).thenReturn(currentUser);
        when(lessonRepository.findForInstructorNameAndDateRange("Mentor QA", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(firstLesson, secondLesson));

        ScheduleGridDto grid = service.getInstructorScheduleGrid(authentication, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(grid.getDates()).containsExactly(LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 13));
        assertThat(grid.getGroups()).hasSize(2);
        assertThat(grid.getGroups().get(0).getGroupCode()).isEqualTo("QA-42");
        assertThat(grid.getGroups().get(0).getDays().get(0).getLessons()).hasSize(1);
        assertThat(grid.getGroups().get(0).getDays().get(0).getLessons().get(0).getInstructorNames())
                .containsExactly("Mentor QA");

        verify(lessonRepository).findForInstructorNameAndDateRange("Mentor QA", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    }

    @Test
    void workloadAndNotifications_areBuiltFromFilteredLessonsInsteadOfFullGroupScan() {
        MyCabinetService service = new MyCabinetService(lessonRepository, identityDirectoryService);
        Authentication authentication = new UsernamePasswordAuthenticationToken("mentor", "pass");
        InternalUserDetailsDto currentUser = internalUser("mentor", "Mentor QA");

        UUID sharedGroupId = UUID.randomUUID();
        UUID sharedDayId = UUID.randomUUID();

        Lesson firstLesson = lesson(sharedGroupId, sharedDayId, "QA-42", "B201", LocalDate.of(2026, 1, 12), 1, "APCS intro", 4, "Mentor QA");
        Lesson secondLesson = lesson(sharedGroupId, sharedDayId, "QA-42", "B201", LocalDate.of(2026, 1, 12), 2, "Signals", 2, "Mentor QA");
        Lesson thirdLesson = lesson("QA-99", "A101", LocalDate.of(2026, 1, 13), 1, "Control systems", 3, "Mentor QA");

        when(identityDirectoryService.getByUsername("mentor")).thenReturn(currentUser);
        when(lessonRepository.findForInstructorNameAndDateRange("Mentor QA", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(firstLesson, secondLesson, thirdLesson));

        WorkloadCalendarDto workload = service.getMyWorkloadCalendar(authentication, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        List<MyNotificationDto> notifications = service.getMyNotifications(authentication, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(workload.getTotalHours()).isEqualTo(9);
        assertThat(workload.getDays()).hasSize(2);
        assertThat(workload.getDays().get(0).getTotalHours()).isEqualTo(6);
        assertThat(workload.getDays().get(0).getLessons()).extracting(item -> item.getTitle())
                .containsExactly("APCS intro", "Signals");

        assertThat(notifications).hasSize(2);
        assertThat(notifications).extracting(MyNotificationDto::getDate)
                .containsExactly(LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 13));
    }

    @Test
    void dashboard_buildsWidgetsFromSingleLessonQuery() {
        MyCabinetService service = new MyCabinetService(lessonRepository, identityDirectoryService);
        Authentication authentication = new UsernamePasswordAuthenticationToken("mentor", "pass");
        InternalUserDetailsDto currentUser = internalUser("mentor", "Mentor QA");

        UUID groupId = UUID.randomUUID();
        UUID dayId = UUID.randomUUID();
        Lesson firstLesson = lesson(groupId, dayId, "QA-42", "B201", LocalDate.of(2026, 1, 12), 1, "APCS intro", 4, "Mentor QA");
        Lesson secondLesson = lesson(groupId, dayId, "QA-42", "B201", LocalDate.of(2026, 1, 12), 2, "Signals", 2, "Mentor QA");

        when(identityDirectoryService.getByUsername("mentor")).thenReturn(currentUser);
        when(lessonRepository.findForInstructorNameAndDateRange("Mentor QA", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(firstLesson, secondLesson));

        MyDashboardDataDto dashboard = service.getDashboard(authentication, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(dashboard.getInstructorSchedule().getGroups()).hasSize(1);
        assertThat(dashboard.getWorkload().getTotalHours()).isEqualTo(6);
        assertThat(dashboard.getNotifications()).hasSize(1);

        verify(identityDirectoryService, times(1)).getByUsername("mentor");
        verify(lessonRepository, times(1))
                .findForInstructorNameAndDateRange("Mentor QA", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    }

    @Test
    void fullGridAndDashboard_normalizeNullDatesBeforeRepositoryCall() {
        MyCabinetService service = new MyCabinetService(lessonRepository, identityDirectoryService);
        Authentication authentication = new UsernamePasswordAuthenticationToken("mentor", "pass");
        InternalUserDetailsDto currentUser = internalUser("mentor", "Mentor QA");

        when(identityDirectoryService.getByUsername("mentor")).thenReturn(currentUser);
        when(lessonRepository.findForDateRange(LocalDate.of(1900, 1, 1), LocalDate.of(3000, 12, 31)))
                .thenReturn(List.of());
        when(lessonRepository.findForInstructorNameAndDateRange("Mentor QA", LocalDate.of(1900, 1, 1), LocalDate.of(3000, 12, 31)))
                .thenReturn(List.of());

        service.getFullScheduleGrid(null, null);
        service.getDashboard(authentication, null, null);

        verify(lessonRepository).findForDateRange(eq(LocalDate.of(1900, 1, 1)), eq(LocalDate.of(3000, 12, 31)));
        verify(lessonRepository).findForInstructorNameAndDateRange(eq("Mentor QA"), eq(LocalDate.of(1900, 1, 1)), eq(LocalDate.of(3000, 12, 31)));
    }

    private InternalUserDetailsDto internalUser(String username, String fullName) {
        InternalUserDetailsDto dto = new InternalUserDetailsDto();
        dto.setId(UUID.randomUUID());
        dto.setUsername(username);
        dto.setFullName(fullName);
        dto.setRole(Role.INSTRUCTOR);
        dto.setActive(true);
        return dto;
    }

    private Lesson lesson(String groupCode,
                          String location,
                          LocalDate date,
                          int orderNumber,
                          String title,
                          int hours,
                          String instructorName) {
        return lesson(UUID.randomUUID(), UUID.randomUUID(), groupCode, location, date, orderNumber, title, hours, instructorName);
    }

    private Lesson lesson(UUID groupId,
                          UUID dayId,
                          String groupCode,
                          String location,
                          LocalDate date,
                          int orderNumber,
                          String title,
                          int hours,
                          String instructorName) {
        Group group = new Group();
        group.setId(groupId);
        group.setCode(groupCode);
        group.setLocation(location);
        group.setCourse(4);

        Day day = new Day();
        day.setId(dayId);
        day.setDate(date);
        day.setGroup(group);

        User instructor = new User();
        instructor.setId(UUID.randomUUID());
        instructor.setUsername("mentor");
        instructor.setFullName(instructorName);
        instructor.setRole(Role.INSTRUCTOR);
        instructor.setActive(true);
        instructor.setCanTeach(true);

        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setVersion(0L);
        lesson.setDay(day);
        lesson.setOrderNumber(orderNumber);
        lesson.setTitle(title);
        lesson.setType(LessonType.LECTURE);
        lesson.setDurationHours(hours);
        lesson.setAssignedInstructors(List.of(instructor));
        lesson.setLecturer(instructorName);
        lesson.setLecturers(List.of(instructorName));
        return lesson;
    }
}
