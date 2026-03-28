package ru.service;

import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.dto.UserDto;
import ru.dto.UserUpsertRequest;
import ru.exception.ResourceNotFoundException;
import ru.model.User;
import ru.repository.UserRepository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserDto> getAll() {
        return userRepository.findAll().stream().map(this::toDto).toList();
    }

    public UserDto getCurrent(Authentication authentication) {
        return toDto(getCurrentUser(authentication));
    }

    public User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    @Transactional
    public UserDto create(UserUpsertRequest request) {
        User user = new User();
        apply(user, request);
        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto update(UUID id, UserUpsertRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        apply(user, request);
        return toDto(userRepository.save(user));
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    public User findOrCreateInstructor(String fullName) {
        List<User> matches = userRepository.findAllByFullNameIgnoreCase(fullName);
        if (!matches.isEmpty()) {
            return pickBestInstructorMatch(matches);
        }

        User user = new User();
        user.setUsername(generateUsername());
        user.setPassword(passwordEncoder.encode("imported-user"));
        user.setFullName(fullName);
        user.setRole(ru.model.Role.INSTRUCTOR);
        user.setCanTeach(true);
        user.setActive(false);
        return userRepository.save(user);
    }

    private void apply(User user, UserUpsertRequest request) {
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setActive(request.isActive());
        user.setCanTeach(request.isCanTeach());
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.isActive())
                .canTeach(user.isCanTeach())
                .build();
    }

    private String generateUsername() {
        return "imported-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private User pickBestInstructorMatch(List<User> matches) {
        return matches.stream()
                .sorted(Comparator
                        .comparing(User::isActive).reversed()
                        .thenComparing(user -> user.getRole() == ru.model.Role.INSTRUCTOR, Comparator.reverseOrder())
                        .thenComparing(User::isCanTeach, Comparator.reverseOrder())
                        .thenComparing(user -> !isImportedUsername(user.getUsername()), Comparator.reverseOrder())
                        .thenComparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst()
                .orElseThrow();
    }

    private boolean isImportedUsername(String username) {
        return username != null && username.startsWith("imported-");
    }
}
