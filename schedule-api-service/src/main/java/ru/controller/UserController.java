package ru.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.client.IdentityClient;
import ru.dto.UserActivityDto;
import ru.dto.UserDto;
import ru.dto.UserUpsertRequest;
import ru.security.DownstreamAuthHeaderFactory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final IdentityClient identityClient;
    private final DownstreamAuthHeaderFactory authHeaderFactory;

    public UserController(IdentityClient identityClient,
                          DownstreamAuthHeaderFactory authHeaderFactory) {
        this.identityClient = identityClient;
        this.authHeaderFactory = authHeaderFactory;
    }

    @GetMapping
    public List<UserDto> getAll(Authentication authentication) {
        return identityClient.getUsers(authHeaderFactory.bearerHeader(authentication));
    }

    @GetMapping("/activity")
    public List<UserActivityDto> getActivity(Authentication authentication) {
        return identityClient.getUsersActivity(authHeaderFactory.bearerHeader(authentication));
    }

    @PostMapping
    public UserDto create(Authentication authentication,
                          @Valid @RequestBody UserUpsertRequest request) {
        return identityClient.createUser(authHeaderFactory.bearerHeader(authentication), request);
    }

    @PutMapping("/{id}")
    public UserDto update(Authentication authentication,
                          @PathVariable UUID id,
                          @Valid @RequestBody UserUpsertRequest request) {
        return identityClient.updateUser(authHeaderFactory.bearerHeader(authentication), id, request);
    }
}
