package ru.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.dto.ChangePasswordRequest;
import ru.dto.InternalUserDetailsDto;
import ru.dto.MyProfileUpdateRequest;
import ru.dto.UserDto;
import ru.dto.UserUpsertRequest;
import ru.model.User;
import ru.repository.UserRepository;

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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found"));
    }

    public InternalUserDetailsDto getInternalByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));

        return InternalUserDetailsDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .position(user.getPosition())
                .department(user.getDepartment())
                .role(user.getRole())
                .active(user.isActive())
                .build();
    }

    @Transactional
    public UserDto updateCurrentProfile(Authentication authentication, MyProfileUpdateRequest request) {
        User user = getCurrentUser(authentication);
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPosition(request.getPosition());
        user.setDepartment(request.getDepartment());
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void changeCurrentPassword(Authentication authentication, ChangePasswordRequest request) {
        User user = getCurrentUser(authentication);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public UserDto create(UserUpsertRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists: " + request.getUsername());
        }

        User user = new User();
        apply(user, request);
        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto update(UUID id, UserUpsertRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
        apply(user, request);
        return toDto(userRepository.save(user));
    }

    private void apply(User user, UserUpsertRequest request) {
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPosition(request.getPosition());
        user.setDepartment(request.getDepartment());
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
                .phone(user.getPhone())
                .position(user.getPosition())
                .department(user.getDepartment())
                .role(user.getRole())
                .active(user.isActive())
                .canTeach(user.isCanTeach())
                .build();
    }
}
