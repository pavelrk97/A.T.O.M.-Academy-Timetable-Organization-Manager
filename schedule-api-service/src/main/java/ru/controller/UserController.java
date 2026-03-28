package ru.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.client.IdentityClient;
import ru.dto.UserDto;
import ru.dto.UserUpsertRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final IdentityClient identityClient;

    public UserController(IdentityClient identityClient) {
        this.identityClient = identityClient;
    }

    @GetMapping
    public List<UserDto> getAll(@RequestHeader("Authorization") String authorization) {
        return identityClient.getUsers(authorization);
    }

    @PostMapping
    public UserDto create(@RequestHeader("Authorization") String authorization,
                          @Valid @RequestBody UserUpsertRequest request) {
        return identityClient.createUser(authorization, request);
    }

    @PutMapping("/{id}")
    public UserDto update(@RequestHeader("Authorization") String authorization,
                          @PathVariable UUID id,
                          @Valid @RequestBody UserUpsertRequest request) {
        return identityClient.updateUser(authorization, id, request);
    }
}
