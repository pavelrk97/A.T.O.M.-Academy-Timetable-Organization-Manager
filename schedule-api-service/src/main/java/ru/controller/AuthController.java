package ru.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.client.IdentityClient;
import ru.dto.LoginRequest;
import ru.dto.TokenResponse;
import ru.dto.UserDto;
import ru.security.DownstreamAuthHeaderFactory;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IdentityClient identityClient;
    private final DownstreamAuthHeaderFactory authHeaderFactory;

    public AuthController(IdentityClient identityClient,
                          DownstreamAuthHeaderFactory authHeaderFactory) {
        this.identityClient = identityClient;
        this.authHeaderFactory = authHeaderFactory;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return identityClient.login(request);
    }

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        return identityClient.getMe(authHeaderFactory.bearerHeader(authentication));
    }
}
