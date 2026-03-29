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
    void updateCurrentProfile_updatesOptionalFieldsWithoutTouchingFullName() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("mentor", "pass");
        User user = user("mentor");
        MyProfileUpdateRequest request = new MyProfileUpdateRequest();
        request.setEmail("mentor@example.com");
        request.setPhone("+79990001122");
        request.setPosition("Senior Instructor");
        request.setDepartment("Automation");

        when(userRepository.findByUsername("mentor")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto result = userService.updateCurrentProfile(authentication, request);

        assertThat(result.getFullName()).isEqualTo("Mentor QA");
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
        assertThat(result.getDepartment()).isEqualTo("Academy");
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.isCanTeach()).isTrue();
    }

    private User user(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPassword("encoded-old");
        user.setFullName("Mentor QA");
        user.setRole(Role.INSTRUCTOR);
        user.setActive(true);
        user.setCanTeach(true);
        return user;
    }
}
