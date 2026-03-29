package ru.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import ru.model.Role;

@Getter
@Setter
public class UserUpsertRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String fullName;

    private String displayName;

    private String email;

    private String phone;

    private String position;

    private String department;

    @NotNull
    private Role role;

    private boolean active = true;

    private boolean canTeach = true;
}
