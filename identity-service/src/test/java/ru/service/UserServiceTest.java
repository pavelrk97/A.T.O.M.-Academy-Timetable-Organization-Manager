package ru.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import ru.dto.ChangePasswordRequest;
import ru.dto.MyProfileUpdateRequest;
import ru.dto.UserDto;
import ru.dto.UserUpsertRequest;
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

    @InjectMocks
    private UserService userService;

    @Test
    void getAll_hidesInternalImportedAndInvalidPlaceholderAccounts() {
        User visibleAdmin = user("admin");
        visibleAdmin.setRole(Role.ADMIN);
        visibleAdmin.setFullName("Administrator");
        visibleAdmin.setDisplayName("Administrator");

        User hiddenImported = user("imported-abc12345");
        hiddenImported.setRole(Role.INSTRUCTOR);
        hiddenImported.setFullName("Harper");
        hiddenImported.setDisplayName("Harper");

        User hiddenPlaceholder = user("Name");
        hiddenPlaceholder.setRole(Role.INSTRUCTOR);
        hiddenPlaceholder.setFullName("Name");
        hiddenPlaceholder.setDisplayName("Name");

        when(userRepository.findAll()).thenReturn(List.of(hiddenImported, hiddenPlaceholder, visibleAdmin));

        List<UserDto> users = userService.getAll();

        assertThat(users).extracting(UserDto::getUsername).containsExactly("admin");
    }

    @Test
    void updateCurrentProfile_updatesOptionalFieldsAndDisplayNameWithoutTouchingFullName() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("mentor", "pass");
        User user = user("mentor");
        MyProfileUpdateRequest request = new MyProfileUpdateRequest();
        request.setDisplayName("Mentor Visible");
        request.setEmail("mentor@example.com");
        request.setPhone("+79990001122");
        request.setPosition("Senior Instructor");
        request.setDepartment("Automation");

        when(userRepository.findByUsername("mentor")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto result = userService.updateCurrentProfile(authentication, request);

        assertThat(result.getFullName()).isEqualTo("Mentor QA");
        assertThat(result.getDisplayName()).isEqualTo("Mentor Visible");
        assertThat(result.getPhone()).isEqualTo("+79990001122");
        assertThat(result.getPosition()).isEqualTo("Senior Instructor");
        assertThat(result.getDepartment()).isEqualTo("Automation");
    }

    @Test
    void changeCurrentPassword_rejectsWrongCurrentPassword() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("mentor", "pass");
        User user = user("mentor");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong");
        request.setNewPassword("new-pass");

        when(userRepository.findByUsername("mentor")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> userService.changeCurrentPassword(authentication, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void changeCurrentPassword_reencodesPassword() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("mentor", "pass");
        User user = user("mentor");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-pass");
        request.setNewPassword("new-pass");

        when(userRepository.findByUsername("mentor")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new");

        userService.changeCurrentPassword(authentication, request);

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(userRepository).save(user);
    }

    @Test
    void create_persistsEncodedPasswordAndOptionalProfileFields() {
        UserUpsertRequest request = new UserUpsertRequest();
        request.setUsername("editor");
        request.setPassword("plain-pass");
        request.setFullName("Editor QA");
        request.setDisplayName("Editor Visible");
        request.setEmail("editor@example.com");
        request.setPhone("+79990001123");
        request.setPosition("Coordinator");
        request.setDepartment("Operations");
        request.setRole(Role.EDITOR);
        request.setActive(true);
        request.setCanTeach(false);

        when(userRepository.existsByUsername("editor")).thenReturn(false);
        when(passwordEncoder.encode("plain-pass")).thenReturn("encoded-pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        UserDto result = userService.create(request);

        assertThat(result.getUsername()).isEqualTo("editor");
        assertThat(result.getDisplayName()).isEqualTo("Editor Visible");
        assertThat(result.getPhone()).isEqualTo("+79990001123");
        assertThat(result.getPosition()).isEqualTo("Coordinator");
        assertThat(result.getDepartment()).isEqualTo("Operations");
        assertThat(result.getRole()).isEqualTo(Role.EDITOR);
    }

    @Test
    void update_rewritesRoleAndOptionalFields() {
        UUID userId = UUID.randomUUID();
        User existing = user("editor");
        existing.setId(userId);
        existing.setRole(Role.EDITOR);

        UserUpsertRequest request = new UserUpsertRequest();
        request.setUsername("editor");
        request.setPassword("new-pass");
        request.setFullName("Editor Lead");
        request.setDisplayName("Editor Alias");
        request.setEmail("lead@example.com");
        request.setPhone("+79990001124");
        request.setPosition("Lead");
        request.setDepartment("Academy");
        request.setRole(Role.ADMIN);
        request.setActive(true);
        request.setCanTeach(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto result = userService.update(userId, request);

        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getFullName()).isEqualTo("Editor Lead");
        assertThat(result.getDisplayName()).isEqualTo("Editor Alias");
        assertThat(result.getDepartment()).isEqualTo("Academy");
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.isCanTeach()).isTrue();
    }

    @Test
    void syncImportedInstructors_createsImportedAccountsAndRebindsDemoInstructor() {
        User demoInstructor = user("instructor");
        demoInstructor.setFullName("Old Name");

        when(userRepository.findAll()).thenReturn(List.of(demoInstructor));
        when(userRepository.findByUsername("Kharlamova")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("Volkova")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("instructor")).thenReturn(Optional.of(demoInstructor));
        when(passwordEncoder.encode("12345")).thenReturn("encoded-12345");
        when(passwordEncoder.encode("123")).thenReturn("encoded-123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.syncImportedInstructors(List.of("Kharlamova", "Volkova", "Kharlamova"));

        assertThat(demoInstructor.getPassword()).isEqualTo("encoded-123");
        assertThat(demoInstructor.getFullName()).isEqualTo("Kharlamova");
        assertThat(demoInstructor.isEditorAccess()).isTrue();
        assertThat(demoInstructor.isCanTeach()).isTrue();
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                "Kharlamova".equals(user.getUsername()) &&
                        "encoded-12345".equals(user.getPassword()) &&
                        !user.isEditorAccess()));
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                "Volkova".equals(user.getUsername()) &&
                        "encoded-12345".equals(user.getPassword()) &&
                        !user.isEditorAccess()));
    }

    @Test
    void syncImportedInstructors_deactivatesStaleAndPlaceholderInstructorAccounts() {
        User demoInstructor = user("instructor");
        demoInstructor.setFullName("Old Demo");
        demoInstructor.setEditorAccess(true);

        User staleImportedLogin = user("Administrator");
        staleImportedLogin.setFullName("Administrator");
        staleImportedLogin.setRole(Role.INSTRUCTOR);
        staleImportedLogin.setEditorAccess(false);
        staleImportedLogin.setCanTeach(true);
        staleImportedLogin.setActive(true);

        User placeholderImported = user("imported-deadbeef");
        placeholderImported.setFullName("Name");
        placeholderImported.setRole(Role.INSTRUCTOR);
        placeholderImported.setEditorAccess(false);
        placeholderImported.setCanTeach(true);
        placeholderImported.setActive(true);

        when(userRepository.findAll()).thenReturn(List.of(demoInstructor, staleImportedLogin, placeholderImported));
        when(userRepository.findByUsername("Harper")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("instructor")).thenReturn(Optional.of(demoInstructor));
        when(passwordEncoder.encode("12345")).thenReturn("encoded-12345");
        when(passwordEncoder.encode("123")).thenReturn("encoded-123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.syncImportedInstructors(List.of("Harper"));

        assertThat(staleImportedLogin.isActive()).isFalse();
        assertThat(staleImportedLogin.isCanTeach()).isFalse();
        assertThat(placeholderImported.isActive()).isFalse();
        assertThat(placeholderImported.isCanTeach()).isFalse();
        assertThat(demoInstructor.getFullName()).isEqualTo("Harper");
    }

    @Test
    void syncImportedInstructors_ignoresEmptyPayload() {
        User placeholderImported = user("imported-deadbeef");
        placeholderImported.setFullName("Name");
        placeholderImported.setRole(Role.INSTRUCTOR);
        placeholderImported.setEditorAccess(false);
        placeholderImported.setCanTeach(true);
        placeholderImported.setActive(true);

        when(userRepository.findAll()).thenReturn(List.of(placeholderImported));

        userService.syncImportedInstructors(java.util.Arrays.asList(" ", null, "Name"));

        assertThat(placeholderImported.isActive()).isFalse();
        assertThat(placeholderImported.isCanTeach()).isFalse();
    }

    private User user(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPassword("encoded-old");
        user.setFullName("Mentor QA");
        user.setDisplayName("Mentor QA");
        user.setRole(Role.INSTRUCTOR);
        user.setActive(true);
        user.setCanTeach(true);
        return user;
    }
}
