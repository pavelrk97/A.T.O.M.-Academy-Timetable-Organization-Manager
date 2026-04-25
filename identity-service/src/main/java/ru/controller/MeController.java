package ru.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.ChangePasswordRequest;
import ru.dto.MyProfileUpdateRequest;
import ru.dto.UserDto;
import ru.service.UserService;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public UserDto getProfile(Authentication authentication) {
        return userService.getCurrent(authentication);
    }

    @PutMapping("/profile")
    public UserDto updateProfile(Authentication authentication,
                                 @Valid @RequestBody MyProfileUpdateRequest request) {
        return userService.updateCurrentProfile(authentication, request);
    }

    @PutMapping("/password")
    public void changePassword(Authentication authentication,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changeCurrentPassword(authentication, request);
    }
}
