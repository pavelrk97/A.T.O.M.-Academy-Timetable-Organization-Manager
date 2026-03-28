package ru.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.LessonType;
import ru.repository.GroupRepository;
import ru.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        CsvImportService.class,
        UserService.class,
        CsvImportServiceIntegrationTest.TestConfig.class
})
class CsvImportServiceIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired
    private CsvImportService csvImportService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void importGroups_canReimportSameGroupWithoutBlowingUp() {
        csvImportService.importGroups(List.of(buildImportedGroup()));
        entityManager.flush();
        entityManager.clear();

        Group firstPass = groupRepository.findByCode("гр.6 ()").orElseThrow();
        assertThat(firstPass.getDays()).hasSize(1);
        assertThat(firstPass.getDays().get(0).getLessons()).hasSize(1);
        assertThat(userRepository.findAll()).hasSize(1);

        // второй проход не должен ломать связи и плодить мусор
        csvImportService.importGroups(List.of(buildImportedGroup()));
        entityManager.flush();
        entityManager.clear();

        Group secondPass = groupRepository.findByCode("гр.6 ()").orElseThrow();
        assertThat(groupRepository.count()).isEqualTo(1);
        assertThat(secondPass.getDays()).hasSize(1);
        assertThat(secondPass.getDays().get(0).getLessons()).hasSize(1);
        assertThat(secondPass.getDays().get(0).getLessons().get(0).getAssignedInstructors()).hasSize(1);
        assertThat(userRepository.findAll()).hasSize(1);
    }

    private Group buildImportedGroup() {
        Group group = new Group();
        group.setCode("гр.6 ()");
        group.setLocation("Б201");
        group.setCourse(6);

        Day day = new Day();
        day.setDate(LocalDate.of(2026, 1, 5));
        day.setGroup(group);
        day.setMeta(new java.util.HashMap<>());

        Lesson lesson = new Lesson();
        lesson.setDay(day);
        lesson.setOrderNumber(1);
        lesson.setTitle("I&C02.01.01 Purpose, Functions and Structure of APCS");
        lesson.setType(LessonType.LECTURE);
        lesson.setDurationHours(3);
        lesson.setLecturer("Меняйло");
        lesson.setLecturers(new ArrayList<>(List.of("Меняйло")));

        day.setLessons(new ArrayList<>(List.of(lesson)));
        group.setDays(new ArrayList<>(List.of(day)));
        return group;
    }
}
