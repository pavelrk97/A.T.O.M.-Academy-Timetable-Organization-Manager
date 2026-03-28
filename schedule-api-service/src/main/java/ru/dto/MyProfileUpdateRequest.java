package ru.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MyProfileUpdateRequest {

    @NotBlank
    private String fullName;

    private String email;

    private String phone;

    private String position;

    private String department;
}
