package ru.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.dto.UserUpsertRequest;
import ru.exception.ResourceNotFoundException;
import ru.model.Role;
import ru.model.User;
import ru.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void getCurrentUser_throwsWhenAuthenticationIsMissing() {
        UserService service = new UserService(userRepository, passwordEncoder);

        assertThatThrownBy(() -> service.getCurrentUser(null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Authenticated user not found");
    }

    @Test
    void findOrCreateInstructor_prefersActiveRealInstructorOverImportedMatches() {
        UserService service = new UserService(userRepository, passwordEncoder);
        User imported = user("imported-abc12345", "Mentor QA", Role.INSTRUCTOR, true, true);
        User editor = user("editor", "Mentor QA", Role.EDITOR, true, true);
        User best = user("mentor.qa", "Mentor QA", Role.INSTRUCTOR, true, true);
        User inactive = user("mentor.old", "Mentor QA", Role.INSTRUCTOR, false, true);

        when(userRepository.findAllByFullNameIgnoreCase("Mentor QA"))
                .thenReturn(List.of(imported, editor, inactive, best));

        User resolved = service.findOrCreateInstructor("Mentor QA");

        assertThat(resolved).isSameAs(best);
    }

    @Test
    void findOrCreateInstructor_createsInactiveImportedUserWhenMissing() {
        UserService service = new UserService(userRepository, passwordEncoder);
        when(userRepository.findAllByFullNameIgnoreCase("New Mentor")).thenReturn(List.of());
        when(passwordEncoder.encode("imported-user")).thenReturn("encoded-imported-user");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = service.findOrCreateInstructor("New Mentor");

        assertThat(created.getUsername()).startsWith("imported-");
        assertThat(created.getPassword()).isEqualTo("encoded-imported-user");
        assertThat(created.getFullName()).isEqualTo("New Mentor");
        assertThat(created.getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(created.isCanTeach()).isTrue();
        assertThat(created.isActive()).isFalse();
    }

    @Test
    void create_encodesPasswordAndMapsFields() {
        UserService service = new UserService(userRepository, passwordEncoder);
        UserUpsertRequest request = new UserUpsertRequest();
        request.setUsername("editor");
        request.setPassword("editor123");
        request.setFullName("Schedule Editor");
        request.setEmail("editor@atom.local");
        request.setRole(Role.EDITOR);
        request.setActive(true);
        request.setCanTeach(false);

        when(passwordEncoder.encode("editor123")).thenReturn("encoded-editor123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("editor");
        assertThat(saved.getPassword()).isEqualTo("encoded-editor123");
        assertThat(saved.getFullName()).isEqualTo("Schedule Editor");
        assertThat(saved.getEmail()).isEqualTo("editor@atom.local");
        assertThat(saved.getRole()).isEqualTo(Role.EDITOR);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isCanTeach()).isFalse();
    }

    @Test
    void update_usesExistingUserAndThrowsWhenMissing() {
        UserService service = new UserService(userRepository, passwordEncoder);
        UUID userId = UUID.randomUUID();
        User existing = user("mentor", "Mentor QA", Role.INSTRUCTOR, true, true);
        existing.setId(userId);

        UserUpsertRequest request = new UserUpsertRequest();
        request.setUsername("mentor");
        request.setPassword("new-pass");
        request.setFullName("Mentor QA Updated");
        request.setEmail("mentor@atom.local");
        request.setRole(Role.INSTRUCTOR);
        request.setActive(true);
        request.setCanTeach(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new-pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(userId, request);

        assertThat(existing.getPassword()).isEqualTo("encoded-new-pass");
        assertThat(existing.getFullName()).isEqualTo("Mentor QA Updated");

        UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(missingId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    @Test
    void getCurrent_returnsDtoForAuthenticatedUser() {
        UserService service = new UserService(userRepository, passwordEncoder);
        User current = user("mentor", "Mentor QA", Role.INSTRUCTOR, true, true);
        current.setId(UUID.randomUUID());
        current.setEmail("mentor@atom.local");

        when(userRepository.findByUsername("mentor")).thenReturn(Optional.of(current));

        var dto = service.getCurrent(new UsernamePasswordAuthenticationToken("mentor", "n/a"));

        assertThat(dto.getId()).isEqualTo(current.getId());
        assertThat(dto.getUsername()).isEqualTo("mentor");
        assertThat(dto.getFullName()).isEqualTo("Mentor QA");
        assertThat(dto.getEmail()).isEqualTo("mentor@atom.local");
        assertThat(dto.getRole()).isEqualTo(Role.INSTRUCTOR);
    }

    private User user(String username, String fullName, Role role, boolean active, boolean canTeach) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPassword("encoded");
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(active);
        user.setCanTeach(canTeach);
        return user;
    }
}
