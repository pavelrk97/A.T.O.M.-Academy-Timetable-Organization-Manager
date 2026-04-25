package ru.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.model.User;
import ru.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DataInitializer dataInitializer;

    @Test
    void init_resetsDemoInstructorPasswordOnExistingAccount() {
        User existingAdmin = new User();
        existingAdmin.setId(UUID.randomUUID());
        existingAdmin.setUsername("admin");
        existingAdmin.setPassword("admin-old");

        User existingEditor = new User();
        existingEditor.setId(UUID.randomUUID());
        existingEditor.setUsername("editor");
        existingEditor.setPassword("editor-old");

        User existingInstructor = new User();
        existingInstructor.setId(UUID.randomUUID());
        existingInstructor.setUsername("instructor");
        existingInstructor.setPassword("instructor-old");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existingAdmin));
        when(userRepository.findByUsername("editor")).thenReturn(Optional.of(existingEditor));
        when(userRepository.findByUsername("instructor")).thenReturn(Optional.of(existingInstructor));
        when(passwordEncoder.encode("123")).thenReturn("encoded-123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        dataInitializer.init();

        assertThat(existingInstructor.getPassword()).isEqualTo("encoded-123");
        assertThat(existingInstructor.isEditorAccess()).isTrue();
        verify(passwordEncoder, never()).encode("admin123");
        verify(passwordEncoder, never()).encode("editor123");
    }

    @Test
    void init_createsNewAccountsWhenTheyAreMissing() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("editor")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("instructor")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("admin123")).thenReturn("encoded-admin");
        when(passwordEncoder.encode("editor123")).thenReturn("encoded-editor");
        when(passwordEncoder.encode("123")).thenReturn("encoded-instructor");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        dataInitializer.init();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(3)).save(userCaptor.capture());

        assertThat(userCaptor.getAllValues())
                .extracting(User::getUsername)
                .containsExactly("admin", "editor", "instructor");
    }
}
