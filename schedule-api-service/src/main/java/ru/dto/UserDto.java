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
public class UserDto {
    private UUID id;
    private String username;
    private String fullName;
    private String displayName;
    private String email;
    private String phone;
    private String position;
    private String department;
    private Role role;
    private boolean editorAccess;
    private boolean active;
    private boolean canTeach;
}
