package ru.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.client.CoreClient;
import ru.dto.UserDto;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CoreClient coreClient;

    public AuthController(CoreClient coreClient) {
        this.coreClient = coreClient;
    }

    @GetMapping("/me")
    public UserDto me(@RequestHeader("Authorization") String authorization) {
        return coreClient.getMe(authorization);
    }
}
