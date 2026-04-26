package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.model.Role;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivityDto {

    private UUID userId;
    private String username;
    private String fullName;
    private Role role;
    private boolean active;
    private LocalDateTime lastLoginAt;
    private long loginCount30d;
    private long loginCountTotal;
}
