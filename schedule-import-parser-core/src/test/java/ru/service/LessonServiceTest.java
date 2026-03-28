package ru.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import ru.dto.LessonDto;
import ru.dto.WorkloadDto;
import ru.exception.ForbiddenEditException;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.LessonType;
import ru.model.Role;
import ru.model.User;
import ru.repository.DayRepository;
import ru.repository.LessonRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private DayRepository dayRepository;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    @Test
    void getWorkload_givesFullHoursToEveryAssignedInstructor() {
        LessonService lessonService = new LessonService(lessonRepository, dayRepository, userService, auditService);
        Authentication authentication = mock(Authentication.class);

        User admin = user("admin", "Admin", Role.ADMIN);
        User firstInstructor = user("inst-1", "Меняйло", Role.INSTRUCTOR);
        User secondInstructor = user("inst-2", "Бунда", Role.INSTRUCTOR);

        Lesson lesson = lesson("гр.6 ()", LocalDate.of(2026, 1, 5), 4, firstInstructor, secondInstructor);

        when(userService.getCurrentUser(authentication)).thenReturn(admin);
        when(lessonRepository.findAll()).thenReturn(List.of(lesson));

        // тут важное правило: часы не делим между инструкторами
        List<WorkloadDto> workload = lessonService.getWorkload(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), authentication);

        assertThat(workload).hasSize(2);
        assertThat(workload)
                .extracting(WorkloadDto::getInstructorName, WorkloadDto::getTotalHours)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Меняйло", 4),
                        org.assertj.core.groups.Tuple.tuple("Бунда", 4)
                );
    }

    @Test
    void create_rejectsInstructorUser() {
        LessonService lessonService = new LessonService(lessonRepository, dayRepository, userService, auditService);
        Authentication authentication = mock(Authentication.class);

        when(userService.getCurrentUser(authentication)).thenReturn(user("instructor", "Reader", Role.INSTRUCTOR));

        assertThatThrownBy(() -> lessonService.create(new LessonDto(), authentication))
                .isInstanceOf(ForbiddenEditException.class)
                .hasMessage("Instructor cannot create lessons");
    }

    @Test
    void getWorkload_doesNotLetInstructorPeekAtAnotherInstructor() {
        LessonService lessonService = new LessonService(lessonRepository, dayRepository, userService, auditService);
        Authentication authentication = mock(Authentication.class);

        User actor = user("instructor", "Reader", Role.INSTRUCTOR);
        actor.setId(UUID.randomUUID());

        when(userService.getCurrentUser(authentication)).thenReturn(actor);

        assertThatThrownBy(() -> lessonService.getWorkload(UUID.randomUUID(), null, null, authentication))
                .isInstanceOf(ForbiddenEditException.class)
                .hasMessage("Instructor can view only own workload");
    }

    private Lesson lesson(String groupCode, LocalDate date, int hours, User... instructors) {
        Group group = new Group();
        group.setCode(groupCode);
        group.setLocation("Б201");

        Day day = new Day();
        day.setDate(date);
        day.setGroup(group);

        Lesson lesson = new Lesson();
        lesson.setDay(day);
        lesson.setOrderNumber(1);
        lesson.setTitle("Lesson");
        lesson.setType(LessonType.LECTURE);
        lesson.setDurationHours(hours);
        lesson.setAssignedInstructors(List.of(instructors));
        lesson.setLecturers(List.of(instructors[0].getFullName()));
        lesson.setLecturer(instructors[0].getFullName());
        return lesson;
    }

    private User user(String username, String fullName, Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(true);
        user.setCanTeach(true);
        return user;
    }
}
