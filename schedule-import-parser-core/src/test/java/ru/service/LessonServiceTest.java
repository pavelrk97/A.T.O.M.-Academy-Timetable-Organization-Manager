package ru.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        when(lessonRepository.findForSchedule(null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(lesson));

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
    void getSchedule_usesDateRangeQueryWhenFiltersAreEmpty() {
        LessonService lessonService = new LessonService(lessonRepository, dayRepository, userService, auditService);
        Lesson lesson = lesson("group-1", LocalDate.of(2026, 1, 5), 2, user("inst-1", "Mentor", Role.INSTRUCTOR));

        when(lessonRepository.findForDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(lesson));

        var schedule = lessonService.getSchedule(null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(schedule).hasSize(1);
        verify(lessonRepository).findForDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    }

    @Test
    void create_rejectsInstructorUser() {
        LessonService lessonService = new LessonService(lessonRepository, dayRepository, userService, auditService);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "instructor",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))
        );

        when(userService.getCurrentUser(authentication)).thenReturn(user("instructor", "Reader", Role.INSTRUCTOR));

        assertThatThrownBy(() -> lessonService.create(new LessonDto(), authentication))
                .isInstanceOf(ForbiddenEditException.class)
                .hasMessage("Lesson editing requires ADMIN or EDITOR access");
    }

    @Test
    void create_allowsInstructorWithEditorRoleAndAssignsOtherTeachingUsers() {
        LessonService lessonService = new LessonService(lessonRepository, dayRepository, userService, auditService);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "instructor",
                "n/a",
                List.of(
                        new SimpleGrantedAuthority("ROLE_INSTRUCTOR"),
                        new SimpleGrantedAuthority("ROLE_EDITOR")
                )
        );

        User actor = user("instructor", "Main Instructor", Role.INSTRUCTOR);
        User admin = user("admin", "Administrator", Role.ADMIN);
        User editor = user("editor", "Schedule Editor", Role.EDITOR);
        UUID dayId = UUID.randomUUID();

        Day day = new Day();
        day.setId(dayId);
        Group group = new Group();
        group.setCode("SMOKE");
        day.setGroup(group);

        LessonDto dto = LessonDto.builder()
                .dayId(dayId)
                .orderNumber(2)
                .title("JWT lesson")
                .durationHours(2)
                .type(LessonType.ASSESSMENT)
                .instructorIds(List.of(admin.getId(), editor.getId(), admin.getId()))
                .build();

        when(userService.getCurrentUser(authentication)).thenReturn(actor);
        when(dayRepository.findById(dayId)).thenReturn(java.util.Optional.of(day));
        when(userService.findById(admin.getId())).thenReturn(admin);
        when(userService.findById(editor.getId())).thenReturn(editor);
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LessonDto saved = lessonService.create(dto, authentication);

        assertThat(saved.getInstructorIds()).containsExactly(admin.getId(), editor.getId());
        assertThat(saved.getInstructorNames()).containsExactly("Administrator", "Schedule Editor");
        verify(lessonRepository).save(any(Lesson.class));
    }

    @Test
    void create_deduplicatesLecturerNamesForDifferentUsersWithSameFullName() {
        LessonService lessonService = new LessonService(lessonRepository, dayRepository, userService, auditService);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        User actor = user("admin", "Administrator", Role.ADMIN);
        User adminAccount = user("admin", "Administrator", Role.ADMIN);
        User demoInstructor = user("instructor", "Administrator", Role.INSTRUCTOR);
        UUID dayId = UUID.randomUUID();

        Day day = new Day();
        day.setId(dayId);

        LessonDto dto = LessonDto.builder()
                .dayId(dayId)
                .title("Duplicate lecturer names")
                .durationHours(2)
                .type(LessonType.LECTURE)
                .instructorIds(List.of(adminAccount.getId(), demoInstructor.getId()))
                .build();

        when(userService.getCurrentUser(authentication)).thenReturn(actor);
        when(dayRepository.findById(dayId)).thenReturn(java.util.Optional.of(day));
        when(userService.findById(adminAccount.getId())).thenReturn(adminAccount);
        when(userService.findById(demoInstructor.getId())).thenReturn(demoInstructor);
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LessonDto saved = lessonService.create(dto, authentication);

        assertThat(saved.getInstructorIds()).containsExactly(adminAccount.getId(), demoInstructor.getId());
        assertThat(saved.getInstructorNames()).containsExactly("Administrator", "Administrator");
        org.mockito.ArgumentCaptor<Lesson> lessonCaptor = org.mockito.ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository).save(lessonCaptor.capture());
        assertThat(lessonCaptor.getValue().getLecturers()).containsExactly("Administrator");
    }

    @Test
    void create_rejectsUsersWithoutTeachingFlagInInstructorAssignments() {
        LessonService lessonService = new LessonService(lessonRepository, dayRepository, userService, auditService);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "editor",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_EDITOR"))
        );

        User actor = user("editor", "Schedule Editor", Role.EDITOR);
        User disabledTeacher = user("admin", "Administrator", Role.ADMIN);
        disabledTeacher.setCanTeach(false);
        UUID dayId = UUID.randomUUID();

        Day day = new Day();
        day.setId(dayId);

        LessonDto dto = LessonDto.builder()
                .dayId(dayId)
                .title("Restricted lesson")
                .durationHours(2)
                .type(LessonType.LECTURE)
                .instructorIds(List.of(disabledTeacher.getId()))
                .build();

        when(userService.getCurrentUser(authentication)).thenReturn(actor);
        when(dayRepository.findById(dayId)).thenReturn(java.util.Optional.of(day));
        when(userService.findById(disabledTeacher.getId())).thenReturn(disabledTeacher);

        assertThatThrownBy(() -> lessonService.create(dto, authentication))
                .isInstanceOf(ForbiddenEditException.class)
                .hasMessage("Only users with canTeach=true can be assigned to lessons");
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
