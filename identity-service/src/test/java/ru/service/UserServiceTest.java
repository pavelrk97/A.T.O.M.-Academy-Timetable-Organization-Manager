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
import ru.model.Role;
import ru.model.User;
import ru.repository.UserRepository;

import java.util.Optional;

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
    void updateCurrentProfile_updatesOptionalFieldsForCurrentUser() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("mentor", "pass");
        User user = user("mentor");
        MyProfileUpdateRequest request = new MyProfileUpdateRequest();
        request.setFullName("Mentor QA");
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

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("encoded-old");
        user.setFullName("Mentor QA");
        user.setRole(Role.INSTRUCTOR);
        user.setActive(true);
        user.setCanTeach(true);
        return user;
    }
}
