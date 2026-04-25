package ru.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dto.ImportedInstructorSyncRequest;
import ru.dto.InternalUserDetailsDto;
import ru.service.UserService;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/by-username/{username}")
    public InternalUserDetailsDto getByUsername(@PathVariable String username) {
        return userService.getInternalByUsername(username);
    }

    @PostMapping("/sync-instructors")
    public void syncImportedInstructors(@RequestBody ImportedInstructorSyncRequest request) {
        userService.syncImportedInstructors(request.getFullNames());
    }
}
