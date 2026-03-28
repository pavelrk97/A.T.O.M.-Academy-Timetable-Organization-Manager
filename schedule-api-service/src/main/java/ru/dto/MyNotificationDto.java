package ru.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.model.NotificationType;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyNotificationDto {

    private NotificationType type;
    private UUID dayId;
    private LocalDate date;
    private String message;
    private String link;
}
