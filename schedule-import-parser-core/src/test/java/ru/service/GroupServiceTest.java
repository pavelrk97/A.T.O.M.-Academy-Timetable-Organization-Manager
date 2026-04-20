package ru.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dto.GroupDto;
import ru.exception.ResourceNotFoundException;
import ru.mapper.GroupMapper;
import ru.model.Day;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.LessonType;
import ru.model.Role;
import ru.model.User;
import ru.repository.GroupRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Test
    void getAll_usesOrderedRepositoryMethodAndMapsNestedSchedule() {
        GroupService service = new GroupService(groupRepository);
        Group group = buildGroup();

        when(groupRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(group));

        List<GroupDto> result = service.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("QA-42");
        assertThat(result.get(0).getDays()).hasSize(1);
        assertThat(result.get(0).getDays().get(0).getLessons()).hasSize(1);
        assertThat(result.get(0).getDays().get(0).getLessons().get(0).getInstructorNames())
                .containsExactly("Mentor QA");

        verify(groupRepository).findAllByOrderByCodeAsc();
    }

    @Test
    void getById_usesDefaultRepositoryMethod() {
        GroupService service = new GroupService(groupRepository);
        Group group = buildGroup();

        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));

        GroupDto result = service.getById(group.getId());

        assertThat(result.getId()).isEqualTo(group.getId());
        assertThat(result.getDays()).hasSize(1);

        verify(groupRepository).findById(group.getId());
    }

    @Test
    void getById_throwsWhenGroupIsMissing() {
        GroupService service = new GroupService(groupRepository);
        UUID missingId = UUID.randomUUID();

        when(groupRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(missingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    @Test
    void update_mergesIncomingDaysWithoutReplacingManagedExistingDay() {
        GroupService service = new GroupService(groupRepository);
        Group group = buildGroup();
        Day existingDay = group.getDays().get(0);

        GroupDto dto = GroupMapper.toDto(group);
        dto.getDays().add(ru.dto.DayDto.builder()
                .date(LocalDate.of(2026, 1, 13))
                .meta(java.util.Map.of("source", "ui"))
                .lessons(List.of())
                .build());

        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupDto updated = service.update(group.getId(), dto);

        assertThat(group.getDays()).hasSize(2);
        assertThat(group.getDays().get(0)).isSameAs(existingDay);
        assertThat(group.getDays().get(0).getLessons()).hasSize(1);
        assertThat(group.getDays().get(1).getDate()).isEqualTo(LocalDate.of(2026, 1, 13));
        assertThat(updated.getDays()).hasSize(2);
    }

    @Test
    void update_reusesExistingDayWhenIncomingPayloadKnowsDateButNotDayId() {
        GroupService service = new GroupService(groupRepository);
        Group group = buildGroup();
        Day existingDay = group.getDays().get(0);

        GroupDto dto = GroupDto.builder()
                .id(group.getId())
                .code(group.getCode())
                .location(group.getLocation())
                .course(group.getCourse())
                .days(List.of(ru.dto.DayDto.builder()
                        .date(existingDay.getDate())
                        .meta(java.util.Map.of("source", "stale-client"))
                        .lessons(List.of())
                        .build()))
                .build();

        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(group.getId(), dto);

        assertThat(group.getDays()).hasSize(1);
        assertThat(group.getDays().get(0)).isSameAs(existingDay);
        assertThat(group.getDays().get(0).getLessons()).hasSize(1);
        assertThat(group.getDays().get(0).getMeta()).containsEntry("source", "stale-client");
    }

    private Group buildGroup() {
        Group group = new Group();
        group.setId(UUID.randomUUID());
        group.setCode("QA-42");
        group.setLocation("B201");
        group.setCourse("4A");

        Day day = new Day();
        day.setId(UUID.randomUUID());
        day.setDate(LocalDate.of(2026, 1, 12));
        day.setGroup(group);

        User instructor = new User();
        instructor.setId(UUID.randomUUID());
        instructor.setUsername("mentor");
        instructor.setFullName("Mentor QA");
        instructor.setRole(Role.INSTRUCTOR);
        instructor.setActive(true);
        instructor.setCanTeach(true);

        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setDay(day);
        lesson.setOrderNumber(1);
        lesson.setTitle("APCS intro");
        lesson.setType(LessonType.LECTURE);
        lesson.setDurationHours(4);
        lesson.setAssignedInstructors(List.of(instructor));
        lesson.setLecturer("Mentor QA");
        lesson.setLecturers(List.of("Mentor QA"));

        day.setLessons(List.of(lesson));
        group.setDays(List.of(day));
        return group;
    }
}
