package ru.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import ru.dto.InternalUserDetailsDto;
import ru.dto.MyNotificationDto;
import ru.dto.ScheduleGridDto;
import ru.dto.WorkloadCalendarDto;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.LessonType;
import ru.model.Role;
import ru.model.User;
import ru.repository.GroupRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyCabinetServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private IdentityDirectoryService identityDirectoryService;

    @InjectMocks
    private MyCabinetService myCabinetService;

    @Test
    void myCabinetEndpoints_useCurrentInstructorNameForGridWorkloadAndNotifications() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("mentor", "pass");
        InternalUserDetailsDto currentUser = new InternalUserDetailsDto();
        currentUser.setId(UUID.randomUUID());
        currentUser.setUsername("mentor");
        currentUser.setFullName("Mentor QA");
        currentUser.setRole(Role.INSTRUCTOR);
        currentUser.setActive(true);

        when(identityDirectoryService.getByUsername("mentor")).thenReturn(currentUser);
        when(groupRepository.findAll()).thenReturn(List.of(groupWithLessons()));

        ScheduleGridDto instructorGrid = myCabinetService.getInstructorScheduleGrid(
                authentication,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        );
        WorkloadCalendarDto workload = myCabinetService.getMyWorkloadCalendar(
                authentication,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        );
        List<MyNotificationDto> notifications = myCabinetService.getMyNotifications(
                authentication,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        );

        assertThat(instructorGrid.getDates()).containsExactly(LocalDate.of(2026, 1, 12));
        assertThat(instructorGrid.getGroups()).hasSize(1);
        assertThat(instructorGrid.getGroups().get(0).getDays().get(0).getLessons()).hasSize(1);
        assertThat(instructorGrid.getGroups().get(0).getDays().get(0).getLessons().get(0).getTitle())
                .isEqualTo("APCS intro");

        assertThat(workload.getInstructorName()).isEqualTo("Mentor QA");
        assertThat(workload.getTotalHours()).isEqualTo(4);
        assertThat(workload.getDays()).hasSize(1);
        assertThat(workload.getDays().get(0).getLessons()).hasSize(1);
        assertThat(workload.getDays().get(0).getLessons().get(0).getGroupCode()).isEqualTo("QA-42");

        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getLink()).isEqualTo("/api/me/schedule/instructor-grid?from=2026-01-12&to=2026-01-12");
    }

    private Group groupWithLessons() {
        Group group = new Group();
        group.setId(UUID.randomUUID());
        group.setCode("QA-42");
        group.setLocation("B201");
        group.setCourse(4);

        Day day = new Day();
        day.setId(UUID.randomUUID());
        day.setDate(LocalDate.of(2026, 1, 12));
        day.setGroup(group);
        day.setLessons(new ArrayList<>());

        User matchingInstructor = new User();
        matchingInstructor.setId(UUID.randomUUID());
        matchingInstructor.setFullName("Mentor QA");

        User anotherInstructor = new User();
        anotherInstructor.setId(UUID.randomUUID());
        anotherInstructor.setFullName("Another Instructor");

        Lesson matchingLesson = lesson(day, "APCS intro", 4, matchingInstructor);
        Lesson otherLesson = lesson(day, "Other topic", 2, anotherInstructor);
        day.getLessons().add(matchingLesson);
        day.getLessons().add(otherLesson);

        group.setDays(List.of(day));
        return group;
    }

    private Lesson lesson(Day day, String title, int hours, User instructor) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setDay(day);
        lesson.setOrderNumber(1);
        lesson.setTitle(title);
        lesson.setDurationHours(hours);
        lesson.setType(LessonType.LECTURE);
        lesson.setAssignedInstructors(List.of(instructor));
        lesson.setLecturers(List.of(instructor.getFullName()));
        lesson.setLecturer(instructor.getFullName());
        return lesson;
    }
}
