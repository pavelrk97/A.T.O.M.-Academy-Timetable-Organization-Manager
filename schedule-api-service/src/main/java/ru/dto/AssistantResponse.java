package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistantResponse {
    private String answer;
    private List<ScheduleEntryDto> lessons;
    private boolean captchaRequired;
    private String captchaId;
    private String captchaQuestion;
}
