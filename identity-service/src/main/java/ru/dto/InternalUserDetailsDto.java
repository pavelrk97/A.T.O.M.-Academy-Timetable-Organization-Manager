package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.model.Role;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalUserDetailsDto {

    private UUID id;
    private String username;
    private String password;
    private String fullName;
    private String email;
    private String phone;
    private String position;
    private String department;
    private Role role;
    private boolean active;
}
