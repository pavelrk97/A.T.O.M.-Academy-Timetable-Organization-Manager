package ru.service;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.model.Role;
import ru.model.User;
import ru.repository.UserRepository;

@Component
@Profile("dev")
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        seed("admin", "admin123", "Administrator", Role.ADMIN, false, true, false);
        seed("editor", "editor123", "Schedule Editor", Role.EDITOR, false, true, false);
        seed("instructor", "123", "Main Instructor", Role.INSTRUCTOR, true, true, true);
    }

    private void seed(String username,
                      String rawPassword,
                      String fullName,
                      Role role,
                      boolean editorAccess,
                      boolean canTeach,
                      boolean resetPasswordOnStartup) {
        User user = userRepository.findByUsername(username).orElseGet(User::new);
        boolean isNew = user.getId() == null;

        if (isNew) {
            user.setUsername(username);
        }

        if (isNew || resetPasswordOnStartup) {
            user.setPassword(passwordEncoder.encode(rawPassword));
        }

        user.setFullName(fullName);
        user.setDisplayName(fullName);
        user.setRole(role);
        user.setEditorAccess(editorAccess);
        user.setActive(true);
        user.setCanTeach(canTeach);
        userRepository.save(user);
    }
}
