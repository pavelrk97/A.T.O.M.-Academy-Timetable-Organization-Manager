package ru.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.dto.ChangeLogDto;
import ru.dto.LessonDto;
import ru.dto.UserDto;
import ru.dto.UserUpsertRequest;
import ru.dto.WorkloadDto;
import ru.exception.ConflictException;
import ru.exception.ForbiddenEditException;
import ru.model.ChangeAction;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.LessonType;
import ru.model.Role;
import ru.model.User;
import ru.repository.DayRepository;
import ru.repository.GroupRepository;
import ru.repository.LessonRepository;
import ru.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        LessonService.class,
        UserService.class,
        AuditService.class,
        LessonCrudScenarioIntegrationTest.TestConfig.class
})
class LessonCrudScenarioIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }

    @Autowired
    private LessonService lessonService;

    @Autowired
    private UserService userService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private DayRepository dayRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void lessonCrudFlow_checksRolesWorkloadAuditAndVersioning() {
        Day day = persistDay("QA-42", LocalDate.of(2026, 1, 12));

        UserDto instructor = userService.create(userRequest("mentor", Role.INSTRUCTOR));
        assertThat(instructor.getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(userRepository.findByUsername("mentor")).isPresent();

        Authentication plainActorAuth = auth("mentor", "ROLE_INSTRUCTOR");
        LessonDto draft = lessonDraft(day, instructor.getId(), "APCS intro", 4, "first pass");

        // Пока это обычный instructor, в CRUD ему нельзя.
        assertThatThrownBy(() -> lessonService.create(draft, plainActorAuth))
                .isInstanceOf(ForbiddenEditException.class)
                .hasMessage("Lesson editing requires ADMIN or EDITOR access");

        UserDto promoted = userService.update(instructor.getId(), userRequest("mentor", Role.EDITOR));
        assertThat(promoted.getRole()).isEqualTo(Role.EDITOR);
        Authentication editorActorAuth = auth("mentor", "ROLE_EDITOR");

        LessonDto createdLesson = lessonService.create(draft, editorActorAuth);
        assertThat(createdLesson.getId()).isNotNull();
        assertThat(createdLesson.getInstructorNames()).containsExactly("Mentor QA");

        entityManager.flush();
        entityManager.clear();

        Lesson savedLesson = lessonRepository.findById(createdLesson.getId()).orElseThrow();
        assertThat(savedLesson.getAssignedInstructors())
                .extracting(User::getUsername)
                .containsExactly("mentor");

        List<WorkloadDto> januaryWorkload = lessonService.getWorkload(
                instructor.getId(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                editorActorAuth
        );
        assertThat(januaryWorkload).singleElement().satisfies(workload -> {
            assertThat(workload.getInstructorName()).isEqualTo("Mentor QA");
            assertThat(workload.getTotalHours()).isEqualTo(4);
        });

        LessonDto update = lessonDraft(day, instructor.getId(), "APCS deep dive", 6, "hours bumped a bit");
        update.setVersion(createdLesson.getVersion());
        update.setOrderNumber(2);

        LessonDto updatedLesson = lessonService.update(createdLesson.getId(), update, editorActorAuth);
        assertThat(updatedLesson.getTitle()).isEqualTo("APCS deep dive");
        assertThat(updatedLesson.getDurationHours()).isEqualTo(6);
        assertThat(updatedLesson.getOrderNumber()).isEqualTo(2);

        entityManager.flush();
        entityManager.clear();

        Lesson reloadedLesson = lessonRepository.findById(createdLesson.getId()).orElseThrow();
        assertThat(reloadedLesson.getVersion()).isGreaterThan(createdLesson.getVersion());

        List<ChangeLogDto> history = lessonService.getHistory(createdLesson.getId());
        assertThat(history).hasSize(2);
        assertThat(history)
                .extracting(ChangeLogDto::getAction)
                .containsExactly(ChangeAction.UPDATED, ChangeAction.CREATED);
        assertThat(history.get(0).getChangedBy()).isEqualTo("mentor");
        assertThat(history.get(0).getBeforeJson()).contains("APCS intro");
        assertThat(history.get(0).getAfterJson()).contains("APCS deep dive");
        assertThat(history.get(1).getComment()).isEqualTo("Lesson created");

        LessonDto staleUpdate = lessonDraft(day, instructor.getId(), "stale write", 8, "should explode");
        staleUpdate.setVersion(createdLesson.getVersion());
        staleUpdate.setOrderNumber(3);

        // Тут просто проверяем, что старую версию молча не перетираем.
        assertThatThrownBy(() -> lessonService.update(createdLesson.getId(), staleUpdate, editorActorAuth))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Lesson was changed by another user. Refresh data and retry.");
    }

    private Day persistDay(String groupCode, LocalDate date) {
        Group group = new Group();
        group.setCode(groupCode);
        group.setLocation("B201");
        group.setCourse(4);

        Day day = new Day();
        day.setDate(date);
        day.setMeta(new java.util.HashMap<>());
        day.setGroup(group);

        group.setDays(new ArrayList<>(List.of(day)));
        Group savedGroup = groupRepository.saveAndFlush(group);
        entityManager.clear();
        return dayRepository.findByGroupIdAndDate(savedGroup.getId(), date).orElseThrow();
    }

    private UserUpsertRequest userRequest(String username, Role role) {
        UserUpsertRequest request = new UserUpsertRequest();
        request.setUsername(username);
        request.setPassword("pass123");
        request.setFullName("Mentor QA");
        request.setEmail("mentor@example.com");
        request.setRole(role);
        request.setActive(true);
        request.setCanTeach(true);
        return request;
    }

    private LessonDto lessonDraft(Day day, java.util.UUID instructorId, String title, int hours, String note) {
        return LessonDto.builder()
                .dayId(day.getId())
                .orderNumber(1)
                .title(title)
                .durationHours(hours)
                .note(note)
                .type(LessonType.LECTURE)
                .instructorIds(List.of(instructorId))
                .build();
    }

    private Authentication auth(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "pass123",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }
}
