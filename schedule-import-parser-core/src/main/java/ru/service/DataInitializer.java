package ru.service;

import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.model.Role;
import ru.model.User;
import ru.repository.UserRepository;

@Component
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        seed("admin", "admin123", "Administrator", Role.ADMIN, true);
        seed("editor", "editor123", "Schedule Editor", Role.EDITOR, true);
        seed("instructor", "instructor123", "Main Instructor", Role.INSTRUCTOR, true);
    }

    private void seed(String username, String rawPassword, String fullName, Role role, boolean canTeach) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(true);
        user.setCanTeach(canTeach);
        userRepository.save(user);
    }
}
