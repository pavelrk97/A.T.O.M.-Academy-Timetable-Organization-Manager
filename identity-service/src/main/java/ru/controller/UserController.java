package ru.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.UserActivityDto;
import ru.dto.UserDto;
import ru.dto.UserUpsertRequest;
import ru.service.UserActivityService;
import ru.service.UserService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserActivityService userActivityService;

    public UserController(UserService userService,
                          UserActivityService userActivityService) {
        this.userService = userService;
        this.userActivityService = userActivityService;
    }

    @GetMapping
    public List<UserDto> getAll() {
        return userService.getAll();
    }

    @GetMapping("/activity")
    public List<UserActivityDto> getActivity() {
        return userActivityService.getActivityForAllUsers();
    }

    @PostMapping
    public UserDto create(@Valid @RequestBody UserUpsertRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UserUpsertRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        userService.delete(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
