package ru.dto;

import lombok.Getter;
import lombok.Setter;
import ru.model.Role;

@Getter
@Setter
public class InternalUserDetailsDto {

    private String username;
    private String password;
    private Role role;
    private boolean active;
}
