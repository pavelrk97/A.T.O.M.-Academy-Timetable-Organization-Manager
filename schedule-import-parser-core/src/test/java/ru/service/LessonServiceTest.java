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
import ru.repository.GroupRepository;
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
    private GroupRepository groupRepository;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    @Mock
    private WorkloadExcelExportService workloadExcelExportService;

    private LessonService lessonService() {
        return new LessonService(
                lessonRepository,
                dayRepository,
                groupRepository,
                userService,
                auditService,
                workloadExcelExportService
        );
    }

    @Test
    void getWorkload_givesFullHoursToEveryAssignedInstructor() {
        LessonService lessonService = lessonService();
        Authentication authentication = mock(Authentication.class);

        User admin = user("admin", "Admin", Role.ADMIN);
        User firstInstructor = user("inst-1", "Меняйло", Role.INSTRUCTOR);
        User secondInstructor = user("inst-2", "Бунда", Role.INSTRUCTOR);

        Lesson lesson = lesson("гр.6 ()", LocalDate.of(2026, 1, 5), 4, firstInstructor, secondInstructor);

        when(userService.getCurrentUser(authentication)).thenReturn(admin);
        when(lessonRepository.findForDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
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
        LessonService lessonService = lessonService();
        Lesson lesson = lesson("group-1", LocalDate.of(2026, 1, 5), 2, user("inst-1", "Mentor", Role.INSTRUCTOR));

        when(lessonRepository.findForDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(lesson));

        var schedule = lessonService.getSchedule(null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(schedule).hasSize(1);
        verify(lessonRepository).findForDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    }

    @Test
    void create_rejectsInstructorUser() {
        LessonService lessonService = lessonService();
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
        LessonService lessonService = lessonService();
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
        LessonService lessonService = lessonService();
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
        LessonService lessonService = lessonService();
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
    void getWorkload_allowsInstructorToViewAnotherInstructor() {
        LessonService lessonService = lessonService();
        Authentication authentication = mock(Authentication.class);

        User actor = user("instructor", "Reader", Role.INSTRUCTOR);
        actor.setId(UUID.randomUUID());
        User anotherInstructor = user("mentor", "Mentor QA", Role.INSTRUCTOR);
        Lesson lesson = lesson("group-1", LocalDate.of(2026, 1, 5), 4, anotherInstructor);

        when(userService.getCurrentUser(authentication)).thenReturn(actor);
        when(lessonRepository.findForInstructorAndDateRange(anotherInstructor.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(lesson));

        List<WorkloadDto> workload = lessonService.getWorkload(
                anotherInstructor.getId(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                authentication
        );

        assertThat(workload).singleElement().satisfies(row -> {
            assertThat(row.getInstructorId()).isEqualTo(anotherInstructor.getId());
            assertThat(row.getInstructorName()).isEqualTo("Mentor QA");
            assertThat(row.getTotalHours()).isEqualTo(4);
        });
    }

    @Test
    void create_rejectsDayWithMoreThanEightLessons() {
        LessonService lessonService = lessonService();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        User actor = user("admin", "Administrator", Role.ADMIN);
        UUID dayId = UUID.randomUUID();
        Day day = new Day();
        day.setId(dayId);
        day.setLessons(java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> {
                    Lesson existing = new Lesson();
                    existing.setId(UUID.randomUUID());
                    return existing;
                })
                .toList());

        LessonDto dto = LessonDto.builder()
                .dayId(dayId)
                .title("Overflow lesson")
                .durationHours(2)
                .type(LessonType.LECTURE)
                .build();

        when(userService.getCurrentUser(authentication)).thenReturn(actor);
        when(dayRepository.findById(dayId)).thenReturn(java.util.Optional.of(day));

        assertThatThrownBy(() -> lessonService.create(dto, authentication))
                .isInstanceOf(ru.exception.ConflictException.class)
                .hasMessage("A day can contain at most 8 lessons");
    }

    @Test
    void exportWorkloadExcel_rejectsPlainInstructor() {
        LessonService lessonService = lessonService();
        Authentication authentication = mock(Authentication.class);
        User actor = user("instructor", "Course Instructor", Role.INSTRUCTOR);

        when(userService.getCurrentUser(authentication)).thenReturn(actor);
        when(lessonRepository.findForDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(lesson));
        when(workloadExcelExportService.exportCalendars(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 1, 1)),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 1, 31))
        )).thenReturn(expected);

        byte[] exported = lessonService.exportWorkloadExcel(
                null,
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                authentication
        );

        assertThatThrownBy(() -> lessonService.exportWorkloadExcel(null, null, null, null, authentication))
                .isInstanceOf(ForbiddenEditException.class)
                .hasMessage("Only admin or editor can export workload catalog");
    }

    @Test
    void syncDay_updatesExistingLessonsDeletesMissingOnesAndReturnsUpdatedGroup() {
        LessonService lessonService = lessonService();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "editor",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_EDITOR"))
        );

        User actor = user("editor", "Schedule Editor", Role.EDITOR);
        User instructor = user("mentor", "Mentor QA", Role.INSTRUCTOR);
        Group group = new Group();
        UUID groupId = UUID.randomUUID();
        group.setId(groupId);
        group.setCode("QA-101");

        Day day = new Day();
        UUID dayId = UUID.randomUUID();
        day.setId(dayId);
        day.setDate(LocalDate.of(2026, 4, 20));
        day.setGroup(group);
        group.setDays(new java.util.ArrayList<>(List.of(day)));

        Lesson existing = new Lesson();
        UUID existingLessonId = UUID.randomUUID();
        existing.setId(existingLessonId);
        existing.setVersion(3L);
        existing.setOrderNumber(1);
        existing.setTitle("Old title");
        existing.setDurationHours(2);
        existing.setType(LessonType.LECTURE);
        existing.setAssignedInstructors(new java.util.ArrayList<>(List.of(instructor)));
        existing.setDay(day);

        Lesson removed = new Lesson();
        removed.setId(UUID.randomUUID());
        removed.setVersion(4L);
        removed.setOrderNumber(2);
        removed.setTitle("Delete me");
        removed.setDurationHours(2);
        removed.setType(LessonType.LECTURE);
        removed.setAssignedInstructors(new java.util.ArrayList<>(List.of(instructor)));
        removed.setDay(day);

        day.setLessons(new java.util.ArrayList<>(List.of(existing, removed)));

        when(userService.getCurrentUser(authentication)).thenReturn(actor);
        when(groupRepository.findById(groupId)).thenReturn(java.util.Optional.of(group));
        when(dayRepository.findByGroupIdAndDate(groupId, LocalDate.of(2026, 4, 20))).thenReturn(java.util.Optional.of(day));
        when(userService.findById(instructor.getId())).thenReturn(instructor);
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson lesson = invocation.getArgument(0);
            if (lesson.getId() == null) {
                lesson.setId(UUID.randomUUID());
                lesson.setVersion(0L);
            }
            return lesson;
        });

        ru.dto.DaySyncRequestDto request = ru.dto.DaySyncRequestDto.builder()
                .groupId(groupId)
                .date(LocalDate.of(2026, 4, 20))
                .ensureDay(true)
                .lessons(List.of(
                        LessonDto.builder()
                                .id(existingLessonId)
                                .version(3L)
                                .orderNumber(1)
                                .title("Updated title")
                                .durationHours(4)
                                .type(LessonType.SELF_STUDY)
                                .instructorIds(List.of(instructor.getId()))
                                .build(),
                        LessonDto.builder()
                                .id(removed.getId())
                                .version(4L)
                                .orderNumber(2)
                                .title("")
                                .durationHours(2)
                                .type(LessonType.LECTURE)
                                .instructorIds(List.of())
                                .build(),
                        LessonDto.builder()
                                .orderNumber(3)
                                .title("Brand new")
                                .durationHours(3)
                                .type(LessonType.ASSESSMENT)
                                .instructorIds(List.of(instructor.getId()))
                                .build()
                ))
                .build();

        var updatedGroup = lessonService.syncDay(request, authentication);

        assertThat(updatedGroup.getId()).isEqualTo(groupId);
        assertThat(updatedGroup.getDays()).singleElement().satisfies(updatedDay -> {
            assertThat(updatedDay.getLessons()).hasSize(2);
            assertThat(updatedDay.getLessons())
                    .extracting(LessonDto::getOrderNumber, LessonDto::getTitle)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple(1, "Updated title"),
                            org.assertj.core.groups.Tuple.tuple(3, "Brand new")
                    );
        });

        verify(lessonRepository).delete(removed);
        verify(lessonRepository, org.mockito.Mockito.times(2)).save(any(Lesson.class));
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
