package ru.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.client.IdentityClient;
import ru.dto.UserDto;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IdentityClient identityClient;

    public AuthController(IdentityClient identityClient) {
        this.identityClient = identityClient;
    }

    @GetMapping("/me")
    public UserDto me(@RequestHeader("Authorization") String authorization) {
        return identityClient.getMe(authorization);
    }
}
