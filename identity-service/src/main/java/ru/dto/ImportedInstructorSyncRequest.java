package ru.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ImportedInstructorSyncRequest {

    @NotNull
    private List<String> fullNames = new ArrayList<>();
}
