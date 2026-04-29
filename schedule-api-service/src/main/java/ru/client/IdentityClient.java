package ru.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import ru.dto.ChangePasswordRequest;
import ru.dto.LoginRequest;
import ru.dto.MyProfileUpdateRequest;
import ru.dto.TokenResponse;
import ru.dto.UserActivityDto;
import ru.dto.UserDto;
import ru.dto.UserUpsertRequest;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "identity-service", url = "${identity.service.url}")
public interface IdentityClient {

    @PostMapping("/api/auth/login")
    TokenResponse login(@RequestBody LoginRequest request);

    @GetMapping("/api/auth/me")
    UserDto getMe(@RequestHeader("Authorization") String authorization);

    @GetMapping("/api/me/profile")
    UserDto getMyProfile(@RequestHeader("Authorization") String authorization);

    @PutMapping("/api/me/profile")
    UserDto updateMyProfile(@RequestHeader("Authorization") String authorization,
                            @RequestBody MyProfileUpdateRequest request);

    @PutMapping("/api/me/password")
    void changeMyPassword(@RequestHeader("Authorization") String authorization,
                          @RequestBody ChangePasswordRequest request);

    @GetMapping("/api/users")
    List<UserDto> getUsers(@RequestHeader("Authorization") String authorization);

    @GetMapping("/api/users/activity")
    List<UserActivityDto> getUsersActivity(@RequestHeader("Authorization") String authorization);

    @PostMapping("/api/users")
    UserDto createUser(@RequestHeader("Authorization") String authorization, @RequestBody UserUpsertRequest request);

    @PutMapping("/api/users/{id}")
    UserDto updateUser(@RequestHeader("Authorization") String authorization,
                       @PathVariable UUID id,
                       @RequestBody UserUpsertRequest request);

    @DeleteMapping("/api/users/{id}")
    void deleteUser(@RequestHeader("Authorization") String authorization,
                    @PathVariable UUID id);
}
