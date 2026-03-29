package ru.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MyProfileUpdateRequest {

    private String displayName;

    private String email;

    private String phone;

    private String position;

    private String department;
}
