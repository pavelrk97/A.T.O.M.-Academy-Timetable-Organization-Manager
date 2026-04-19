package ru.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.LessonType;
import ru.model.Role;
import ru.model.User;
import ru.repository.LessonRepository;
import ru.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructorIdentitySyncServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Test
    void buildSyncInstructorNames_usesImportedUsersOnlyAndPutsUpcomingInstructorFirst() {
        InstructorIdentitySyncService service = new InstructorIdentitySyncService(
                "http://identity-service:8082",
                "internal-key",
                userRepository,
                lessonRepository
        );

        User importedKharlamova = importedInstructor("Kharlamova");
        User importedVolkova = importedInstructor("Volkova");
        User importedPlaceholder = importedInstructor("Name");

        when(userRepository.findAllByRoleAndCanTeachTrueAndUsernameStartingWithOrderByFullNameAsc(
                Role.INSTRUCTOR,
                "imported-"
        )).thenReturn(List.of(importedKharlamova, importedPlaceholder, importedVolkova));
        when(lessonRepository.findForDateRange(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        lesson(LocalDate.now().plusDays(1), importedVolkova),
                        lesson(LocalDate.now().plusDays(2), importedVolkova)
                ));

        List<String> syncNames = service.buildSyncInstructorNames();

        assertThat(syncNames).containsExactly("Volkova", "Kharlamova");
    }

    private User importedInstructor(String fullName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("imported-" + fullName.toLowerCase());
        user.setFullName(fullName);
        user.setRole(Role.INSTRUCTOR);
        user.setCanTeach(true);
        user.setActive(false);
        return user;
    }

    private Lesson lesson(LocalDate date, User instructor) {
        Group group = new Group();
        group.setCode("SMOKE");

        Day day = new Day();
        day.setDate(date);
        day.setGroup(group);

        Lesson lesson = new Lesson();
        lesson.setDay(day);
        lesson.setTitle("Smoke lesson");
        lesson.setType(LessonType.LECTURE);
        lesson.setDurationHours(2);
        lesson.setAssignedInstructors(List.of(instructor));
        lesson.setLecturers(List.of(instructor.getFullName()));
        lesson.setLecturer(instructor.getFullName());
        return lesson;
    }
}
